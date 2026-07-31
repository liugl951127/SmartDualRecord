package com.minimax.dualrecord.service;

import com.minimax.dualrecord.ai.AsrService;
import com.minimax.dualrecord.ai.DeepfakeDetector;
import com.minimax.dualrecord.ai.DetectionResult;
import com.minimax.dualrecord.ai.LlmGateway;
import com.minimax.dualrecord.domain.Business;
import com.minimax.dualrecord.domain.EventLog;
import com.minimax.dualrecord.domain.Recording;
import com.minimax.dualrecord.domain.RecordingNodeDetail;
import com.minimax.dualrecord.domain.enums.BusinessType;
import com.minimax.dualrecord.domain.enums.Channel;
import com.minimax.dualrecord.domain.enums.RecordingNode;
import com.minimax.dualrecord.domain.enums.RecordingState;
import com.minimax.dualrecord.domain.enums.SellerType;
import com.minimax.dualrecord.exception.BusinessException;
import com.minimax.dualrecord.repository.BusinessRepository;
import com.minimax.dualrecord.repository.EventLogRepository;
import com.minimax.dualrecord.repository.RecordingNodeRepository;
import com.minimax.dualrecord.repository.RecordingRepository;
import com.minimax.dualrecord.statemachine.RecordingStateMachine;
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
 * <h3>设计：纯 @Transactional 模式（已弃用 Saga）</h3>
 *
 * 之前用 Saga 模式做补偿式事务管理，发现过度设计。
 * 本系统 8 张表全在同一个数据库（H2 / MariaDB），用 Spring 的
 * @Transactional 即可获得 ACID 强一致保证。
 *
 * <h3>事务边界</h3>
 * 每个 public 状态变更方法 = 1 个 @Transactional 事务：
 *  - 业务表更新
 *  - 事件日志追加
 *  - 其他关联表（录像 / 节点 / 业务记录）
 * 任意一步失败 → 整个事务回滚 → 业务状态停留在上一个一致状态
 *
 * <h3>失败处理</h3>
 * 不再写 ROLLED_BACK 状态（由 Saga 显式补偿）。
 * 改为：
 *  - 状态机校验失败 → 抛 IllegalStateTransitionException（409）
 *  - 业务异常（禁播词 / 风险评估失效） → 抛 BusinessException（400）
 *  - 数据库异常 → 事务回滚（500）
 *  - 孤儿业务（长时间停留在 RECORDING） → StaleBusinessDetector 定时扫描 + 标 FAILED
 *
 * <h3>审计追溯</h3>
 * tb_event 表继续记录所有成功的状态转换。
 * 失败的状态转换不写事件（事务回滚）。
 * 整体审计链路靠 HTTP 请求日志 + Spring transaction log + 业务表 snapshot。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordingService {

    private final BusinessRepository businessRepository;
    private final RecordingRepository recordingRepository;
    private final RecordingNodeRepository nodeRepository;
    private final EventLogRepository eventLogRepository;
    private final ScriptService scriptService;
    private final RiskAssessmentService riskService;
    private final ComplianceService complianceService;
    private final QaService qaService;
    private final LlmGateway llmGateway;
    private final AsrService asrService;
    private final DeepfakeDetector deepfakeDetector;
    private final BusinessIdGenerator idGenerator;

    // ====================================================================
    // 1. 创建业务 + 启动双录
    // ====================================================================
    @Transactional(rollbackFor = Exception.class)
    public Business startBusiness(BusinessType type, String productId,
                                  String customerIdHash, String sellerIdHash,
                                  Channel channel, SellerType sellerType,
                                  BigDecimal amount) {
        var script = scriptService.getScript(productId);
        log.info("创建业务: type={}, productId={}, channel={}", type, productId, channel);

        String businessId = idGenerator.generate(type);

        // 1. 业务主表
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

        // 2. 录像记录
        Recording rec = new Recording();
        rec.setRecId(idGenerator.generateRecId());
        rec.setBusinessId(businessId);
        rec.setChannel(channel);
        rec.setSellerType(sellerType);
        rec.setRecStartUtc(LocalDateTime.now());
        rec.setEncryption("SM4-CBC");
        rec.setWatermarkVisible(channel == Channel.SELF_AI ? 1 : 0);
        rec.setAudioIdPerMinute(channel == Channel.SELF_AI ? 1 : 0);
        rec.setRetentionUntil(LocalDate.now().plusYears(10));
        rec.setCreatedAt(LocalDateTime.now());
        rec.setDeleted(0);
        recordingRepository.insert(rec);

        // 3. 初始事件日志
        writeEvent(businessId, null, RecordingState.INIT.name(),
                "SYSTEM", "BUSINESS_CREATED");

        log.info("业务已创建: businessId={}, recId={}", businessId, rec.getRecId());
        return business;
    }

    // ====================================================================
    // 2. 加载话术
    // ====================================================================
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> loadScript(String businessId, String productId) {
        Map<String, Object> script = scriptService.getScript(productId);
        transitionState(businessId, RecordingState.SCRIPT_LOADED, "SYSTEM", "Script loaded");
        return script;
    }

    // ====================================================================
    // 3. 风险评估
    // ====================================================================
    @Transactional(rollbackFor = Exception.class)
    public RiskAssessmentService.MatchResult assessRisk(String businessId, String customerIdHash) {
        var assessment = riskService.getLatestValid(customerIdHash);
        if (assessment == null) {
            throw new BusinessException("RISK_ASSESSMENT_INVALID",
                    "客户风险评估已过期或不存在，请重新评估");
        }

        Business business = businessRepository.selectById(businessId);
        var matchResult = riskService.match(assessment.getRiskLevel(), business.getProductRiskLevel());

        business.setRiskLevel(assessment.getRiskLevel());
        business.setUpdatedAt(LocalDateTime.now());
        businessRepository.updateById(business);

        transitionState(businessId, RecordingState.RISK_ASSESSED, "SYSTEM",
                "Risk assessed: " + assessment.getRiskLevel());
        return matchResult;
    }

    // ====================================================================
    // 4. 启动录制
    // ====================================================================
    @Transactional(rollbackFor = Exception.class)
    public void startRecording(String businessId) {
        log.info("启动录制: businessId={}", businessId);
        transitionState(businessId, RecordingState.RECORDING, "SYSTEM", "Recording started");

        // 顺便写入第一条"开始录制"事件
        writeEvent(businessId, RecordingState.SCRIPT_LOADED.name(),
                RecordingState.RECORDING.name(), "SYSTEM", "RECORDING_STARTED_DETAIL");
    }

    // ====================================================================
    // 5. 完成一个节点
    // ====================================================================
    @Transactional(rollbackFor = Exception.class)
    public NodeResult completeNode(String businessId, String recId, RecordingNode node,
                                    String asrText) {
        log.info("完成节点: businessId={}, node={}", businessId, node.getCode());

        // 1. 禁播词扫描（HIGH 级别抛异常 → 事务回滚 → 节点不记录）
        List<ComplianceService.Hit> hits = complianceService.scan(asrText);
        if (complianceService.isBlocking(hits)) {
            log.warn("节点 {} 命中禁播词，强制阻断: {}", node.getCode(), hits);
            throw new BusinessException("FORBIDDEN_PHRASE_HIT",
                    "节点 " + node.getCode() + " 命中禁播词: " + hits);
        }

        // 2. 关键节点（节点 6）必须有 ASR 肯定词
        if (node.isCritical()) {
            var asrResult = asrService.transcribe(asrText.getBytes());
            if (!Boolean.TRUE.equals(asrResult.getContainsAffirmative())) {
                throw new BusinessException("CRITICAL_NODE_AFFIRMATIVE_MISSING",
                        "关键节点 " + node.getCode() + " 未识别到肯定答复");
            }
        }

        // 3. 校验业务当前确实在录制中
        Business business = businessRepository.selectById(businessId);
        if (business == null) {
            throw new BusinessException("BUSINESS_NOT_FOUND", "业务不存在: " + businessId);
        }
        if (business.getState() != RecordingState.RECORDING) {
            throw new BusinessException("ILLEGAL_STATE_FOR_NODE",
                    "当前状态 " + business.getState() + " 不允许完成节点");
        }

        // 4. 写节点证据 + 事件日志（都在同一事务里）
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

        writeEvent(businessId, business.getState().name(), business.getState().name(),
                "SYSTEM", "NODE_COMPLETED: " + node.getCode() + " (hits=" + hits.size() + ")");

        return new NodeResult(node.getCode(), true, hits, null);
    }

    // ====================================================================
    // 6. 完成所有节点 → AI 终检
    // ====================================================================
    @Transactional(rollbackFor = Exception.class)
    public QaResult finalQa(String businessId, String recId, String fullAsrText) {
        log.info("完成录制，进入终检: businessId={}, recId={}", businessId, recId);

        // 1. RECORDING → RECORDED
        transitionState(businessId, RecordingState.RECORDED, "SYSTEM", "All nodes completed");

        // 2. RECORDED → AI_QA
        transitionState(businessId, RecordingState.AI_QA, "SYSTEM", "AI QA started");

        // 3. 反深伪检测
        DetectionResult deepfake = deepfakeDetector.detect(null, null);
        if (Boolean.TRUE.equals(deepfake.getRequireHumanTakeover())) {
            transitionState(businessId, RecordingState.AI_QA_FLAGGED, "SYSTEM",
                    "Deepfake suspected: " + deepfake.getEvidence());
            log.error("反深伪命中！businessId={}, score={}", businessId, deepfake.getOverallScore());
        }

        // 4. AI 预筛
        var qa = qaService.aiPreScreen(businessId, recId, fullAsrText);

        // 5. 根据结果转换状态
        if ("FAIL".equals(qa.getAiQaResult())) {
            transitionState(businessId, RecordingState.AI_QA_FLAGGED, "SYSTEM",
                    "AI QA failed: " + qa.getAiQaResult());
            return qa;
        }

        if ("PASS_WITH_FINDINGS".equals(qa.getAiQaResult())) {
            // 需要人工复核
            transitionState(businessId, RecordingState.AI_QA_FLAGGED, "SYSTEM",
                    "AI QA passed with findings, need human review");
            transitionState(businessId, RecordingState.HUMAN_REVIEW, "SYSTEM",
                    "Human review started");
            return qa;
        }

        // PASS → 直接进入 SIGNED 状态机可走
        transitionState(businessId, RecordingState.AI_QA_PASSED, "SYSTEM",
                "AI QA passed: " + qa.getAiQaResult());
        return qa;
    }

    // ====================================================================
    // 7. 人工复核完成
    // ====================================================================
    @Transactional(rollbackFor = Exception.class)
    public void humanReview(String businessId, String reviewStatus, String reviewerId) {
        transitionState(businessId, RecordingState.HUMAN_REVIEWED, reviewerId,
                "Human review: " + reviewStatus);
    }

    // ====================================================================
    // 8. 客户签字 → 归档
    // ====================================================================
    @Transactional(rollbackFor = Exception.class)
    public void signAndArchive(String businessId) {
        transitionState(businessId, RecordingState.SIGNED, "customer", "Customer signed");
        transitionState(businessId, RecordingState.ARCHIVED, "SYSTEM", "Archived");

        // 关闭录像时间
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

    // ====================================================================
    // 9. 业务全景查询（只读，不需要事务）
    // ====================================================================
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
        List<EventLog> events = eventLogRepository.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<EventLog>()
                        .eq("business_id", businessId)
                        .orderByDesc("created_at"));

        Map<String, Object> overview = new HashMap<>();
        overview.put("business", business);
        overview.put("recordings", recordings);
        overview.put("nodes", nodes);
        overview.put("events", events);
        overview.put("node_count", nodes.size());
        overview.put("completed_node_count", nodes.stream().filter(n -> n.getCompleted() == 1).count());
        overview.put("event_count", events.size());
        return overview;
    }

    // ====================================================================
    // 10. 人工强制标记 FAILED（用于运维介入）
    // ====================================================================
    @Transactional(rollbackFor = Exception.class)
    public void manualFail(String businessId, String reason, String operatorId) {
        Business business = businessRepository.selectById(businessId);
        if (business == null) {
            throw new BusinessException("BUSINESS_NOT_FOUND", "业务不存在: " + businessId);
        }
        // 任何非终态都能标 FAILED
        if (business.getState().isTerminal()) {
            throw new BusinessException("ILLEGAL_STATE_FOR_FAIL",
                    "终态业务不能标 FAILED: " + business.getState());
        }
        // 走状态机校验
        transitionState(businessId, RecordingState.FAILED, operatorId, "MANUAL: " + reason);
    }

    // ====================================================================
    // 内部：状态转换核心（替代原 Saga.applyStateChange）
    // ====================================================================
    private void transitionState(String businessId, RecordingState target,
                                  String actorId, String reason) {
        Business business = businessRepository.selectById(businessId);
        if (business == null) {
            throw new BusinessException("BUSINESS_NOT_FOUND", "业务不存在: " + businessId);
        }
        RecordingState fromState = business.getState();

        // 状态机校验（非法转移抛 IllegalStateTransitionException → 事务回滚）
        RecordingState newState = RecordingStateMachine.transition(fromState, target);

        // 1. 更新业务状态
        business.setState(newState);
        business.setUpdatedAt(LocalDateTime.now());
        if (newState == RecordingState.ARCHIVED) {
            business.setArchivedAt(LocalDateTime.now());
        }
        businessRepository.updateById(business);

        // 2. 写事件日志（同一事务里，要么都成功要么都失败）
        writeEvent(businessId, fromState.name(), newState.name(), actorId, reason);

        log.info("状态变更: businessId={}, {} → {}, actor={}", businessId, fromState, newState, actorId);
    }

    private void writeEvent(String businessId, String fromState, String toState,
                             String actorId, String reason) {
        EventLog event = new EventLog();
        event.setBusinessId(businessId);
        event.setEventType("STATE_TRANSITION");
        event.setFromState(fromState);
        event.setToState(toState);
        event.setActorId(actorId);
        event.setActorType("SYSTEM");
        event.setEventData(String.format("{\"reason\":\"%s\"}", reason.replace("\"", "'")));
        event.setCreatedAt(LocalDateTime.now());
        eventLogRepository.insert(event);
    }

    public record NodeResult(String nodeCode, boolean passed, List<ComplianceService.Hit> hits, String error) {}
}
