package com.minimax.dualrecord.service;

import com.minimax.dualrecord.ai.AsrService;
import com.minimax.dualrecord.ai.DeepfakeDetector;
import com.minimax.dualrecord.ai.DetectionResult;
import com.minimax.dualrecord.ai.LlmGateway;
import com.minimax.dualrecord.domain.Business;
import com.minimax.dualrecord.domain.Recording;
import com.minimax.dualrecord.domain.RecordingNodeDetail;
import com.minimax.dualrecord.domain.enums.BusinessType;
import com.minimax.dualrecord.domain.enums.Channel;
import com.minimax.dualrecord.domain.enums.RecordingNode;
import com.minimax.dualrecord.domain.enums.RecordingState;
import com.minimax.dualrecord.domain.enums.SellerType;
import com.minimax.dualrecord.exception.BusinessException;
import com.minimax.dualrecord.repository.BusinessRepository;
import com.minimax.dualrecord.repository.RecordingNodeRepository;
import com.minimax.dualrecord.repository.RecordingRepository;
import com.minimax.dualrecord.saga.RecordingSagaCoordinator;
import com.minimax.dualrecord.util.BusinessIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 录制服务 · 核心业务逻辑
 *
 * 把以下 6 件事串成一条线：
 *  1. 创建业务（生成 business_id）
 *  2. 加载话术（按 product_id 加载 YAML）
 *  3. 启动 8 节点录制
 *  4. 每个节点完成时写证据 + 触发 AI 实时质检
 *  5. 全部节点完成 → AI 终检
 *  6. 归档
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordingService {

    private final BusinessRepository businessRepository;
    private final RecordingRepository recordingRepository;
    private final RecordingNodeRepository nodeRepository;
    private final ScriptService scriptService;
    private final RiskAssessmentService riskService;
    private final ComplianceService complianceService;
    private final QaService qaService;
    private final LlmGateway llmGateway;
    private final AsrService asrService;
    private final DeepfakeDetector deepfakeDetector;
    private final RecordingSagaCoordinator saga;
    private final BusinessIdGenerator idGenerator;

    /**
     * 1. 创建业务 + 启动双录
     */
    @Transactional
    public Business startBusiness(BusinessType type, String productId,
                                  String customerIdHash, String sellerIdHash,
                                  Channel channel, SellerType sellerType,
                                  BigDecimal amount) {
        // 校验话术
        var script = scriptService.getScript(productId);
        log.info("创建业务: type={}, productId={}, channel={}", type, productId, channel);

        // 生成业务 ID
        String businessId = idGenerator.generate(type);

        // 创建业务记录
        Business business = new Business();
        business.setBusinessId(businessId);
        business.setBusinessType(type);
        business.setProductId(productId);
        business.setCustomerIdHash(customerIdHash);
        business.setSellerIdHash(sellerIdHash);
        business.setChannel(channel);
        business.setState(RecordingState.INIT);
        business.setAmount(amount);
        business.setProductRiskLevel((String) script.get("risk_level"));
        business.setCreatedAt(LocalDateTime.now());
        business.setUpdatedAt(LocalDateTime.now());
        business.setDeleted(0);
        businessRepository.insert(business);

        // 创建录像记录
        Recording rec = new Recording();
        rec.setRecId(idGenerator.generateRecId());
        rec.setBusinessId(businessId);
        rec.setChannel(channel);
        rec.setSellerType(sellerType);
        rec.setRecStartUtc(LocalDateTime.now());
        rec.setEncryption("SM4-CBC");
        rec.setWatermarkVisible(channel == Channel.SELF_AI ? 1 : 0);
        rec.setAudioIdPerMinute(channel == Channel.SELF_AI ? 1 : 0);
        rec.setRetentionUntil(LocalDate.now().plusYears(10));  // 10 年留存
        rec.setCreatedAt(LocalDateTime.now());
        rec.setDeleted(0);
        recordingRepository.insert(rec);

        log.info("业务已创建: businessId={}, recId={}", businessId, rec.getRecId());
        return business;
    }

    /**
     * 2. 加载话术（按渠道差分）
     */
    public Map<String, Object> loadScript(String businessId, String productId) {
        Map<String, Object> script = scriptService.getScript(productId);
        // 状态机推进：INIT → SCRIPT_LOADED
        saga.applyStateChange(businessId, RecordingState.SCRIPT_LOADED, "SYSTEM", "Script loaded");
        return script;
    }

    /**
     * 3. 风险评估
     */
    public RiskAssessmentService.MatchResult assessRisk(String businessId, String customerIdHash) {
        var assessment = riskService.getLatestValid(customerIdHash);
        if (assessment == null) {
            throw new BusinessException("RISK_ASSESSMENT_INVALID",
                    "客户风险评估已过期或不存在，请重新评估");
        }

        Business business = businessRepository.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Business>()
                        .eq("business_id", businessId));
        var matchResult = riskService.match(assessment.getRiskLevel(), business.getProductRiskLevel());

        business.setRiskLevel(assessment.getRiskLevel());
        business.setUpdatedAt(LocalDateTime.now());
        businessRepository.updateById(business);

        // 状态机推进：SCRIPT_LOADED → RISK_ASSESSED
        saga.applyStateChange(businessId, RecordingState.RISK_ASSESSED, "SYSTEM",
                "Risk assessed: " + assessment.getRiskLevel());
        return matchResult;
    }

    /**
     * 4. 启动录制（8 节点状态机）
     */
    @Transactional
    public void startRecording(String businessId) {
        log.info("启动录制: businessId={}", businessId);
        saga.applyStateChange(businessId, RecordingState.RECORDING, "SYSTEM", "Recording started");
    }

    /**
     * 5. 完成一个节点（含 AI 实时质检 + 禁播词扫描）
     */
    @Transactional
    public NodeResult completeNode(String businessId, String recId, RecordingNode node,
                                    String asrText) {
        log.info("完成节点: businessId={}, node={}", businessId, node.getCode());

        // 1. 禁播词扫描
        List<ComplianceService.Hit> hits = complianceService.scan(asrText);
        if (complianceService.isBlocking(hits)) {
            log.warn("节点 {} 命中禁播词，强制阻断: {}", node.getCode(), hits);
            throw new BusinessException("FORBIDDEN_PHRASE_HIT",
                    "节点 " + node.getCode() + " 命中禁播词: " + hits);
        }

        // 2. 关键节点额外校验
        if (node.isCritical()) {
            // 节点 6 明确肯定答复：必须有 ASR 肯定词
            var asrResult = asrService.transcribe(asrText.getBytes());
            if (!Boolean.TRUE.equals(asrResult.getContainsAffirmative())) {
                throw new BusinessException("CRITICAL_NODE_AFFIRMATIVE_MISSING",
                        "关键节点 " + node.getCode() + " 未识别到肯定答复");
            }
        }

        // 3. 写节点证据
        RecordingNodeDetail detail = new RecordingNodeDetail();
        detail.setBusinessId(businessId);
        detail.setRecId(recId);
        detail.setNodeId(node.getCode());
        detail.setNodeName(node.getDisplayName());
        detail.setStartUtc(LocalDateTime.now().minusSeconds(60));
        detail.setEndUtc(LocalDateTime.now());
        detail.setDurationMs(60_000L);
        detail.setCompleted(1);
        detail.setEvidenceTs(LocalDateTime.now());
        detail.setOperatorId("system");
        detail.setDeleted(0);
        nodeRepository.insert(detail);

        return new NodeResult(node.getCode(), true, hits, null);
    }

    /**
     * 6. 完成所有节点 → 进入终检
     */
    @Transactional
    public QaResult finalQa(String businessId, String recId, String fullAsrText) {
        log.info("完成录制，进入终检: businessId={}, recId={}", businessId, recId);
        saga.applyStateChange(businessId, RecordingState.RECORDED, "SYSTEM", "All nodes completed");
        saga.applyStateChange(businessId, RecordingState.AI_QA, "SYSTEM", "AI QA started");

        // 反深伪检测（如果有视频帧）
        DetectionResult deepfake = deepfakeDetector.detect(null, null);
        if (Boolean.TRUE.equals(deepfake.getRequireHumanTakeover())) {
            saga.applyStateChange(businessId, RecordingState.AI_QA_FLAGGED, "SYSTEM",
                    "Deepfake suspected: " + deepfake.getEvidence());
            log.error("反深伪命中！businessId={}, score={}", businessId, deepfake.getOverallScore());
        }

        // AI 预筛
        var qa = qaService.aiPreScreen(businessId, recId, fullAsrText);

        if ("FAIL".equals(qa.getAiQaResult())) {
            saga.applyStateChange(businessId, RecordingState.AI_QA_FLAGGED, "SYSTEM",
                    "AI QA failed: " + qa.getAiQaResult());
            return qa;
        }

        // 通过
        RecordingState next = "PASS_WITH_FINDINGS".equals(qa.getAiQaResult())
                ? RecordingState.HUMAN_REVIEW
                : RecordingState.SIGNED;
        saga.applyStateChange(businessId,
                next == RecordingState.HUMAN_REVIEW ? RecordingState.AI_QA_FLAGGED : RecordingState.AI_QA_PASSED,
                "SYSTEM", "AI QA passed: " + qa.getAiQaResult());
        return qa;
    }

    /**
     * 7. 客户签字 → 归档
     */
    @Transactional
    public void signAndArchive(String businessId) {
        saga.applyStateChange(businessId, RecordingState.SIGNED, "customer", "Customer signed");
        saga.applyStateChange(businessId, RecordingState.ARCHIVED, "SYSTEM", "Archived");

        // 关闭录像时间
        Business business = businessRepository.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Business>()
                        .eq("business_id", businessId));
        Recording rec = recordingRepository.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Recording>()
                        .eq("business_id", businessId));
        if (rec != null) {
            rec.setRecEndUtc(LocalDateTime.now());
            if (rec.getRecStartUtc() != null) {
                rec.setDurationMs(java.time.Duration.between(rec.getRecStartUtc(), rec.getRecEndUtc()).toMillis());
            }
            recordingRepository.updateById(rec);
        }
        log.info("业务已归档: businessId={}", businessId);
    }

    /**
     * 获取业务全貌（按 business_id 关联所有表）
     */
    public Map<String, Object> getBusinessOverview(String businessId) {
        Business business = businessRepository.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Business>()
                        .eq("business_id", businessId));
        if (business == null) {
            throw new BusinessException("BUSINESS_NOT_FOUND", "业务不存在: " + businessId);
        }

        List<Recording> recordings = recordingRepository.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Recording>()
                        .eq("business_id", businessId).eq("deleted", 0));
        List<RecordingNodeDetail> nodes = nodeRepository.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<RecordingNodeDetail>()
                        .eq("business_id", businessId).eq("deleted", 0));

        Map<String, Object> overview = new HashMap<>();
        overview.put("business", business);
        overview.put("recordings", recordings);
        overview.put("nodes", nodes);
        overview.put("node_count", nodes.size());
        overview.put("completed_node_count", nodes.stream().filter(n -> n.getCompleted() == 1).count());
        return overview;
    }

    public record NodeResult(String nodeCode, boolean passed, List<ComplianceService.Hit> hits, String error) {}
}
