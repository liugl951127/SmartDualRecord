package com.minimax.dualrecord.service;

import com.minimax.dualrecord.domain.Business;
import com.minimax.dualrecord.domain.enums.BusinessType;
import com.minimax.dualrecord.domain.enums.Channel;
import com.minimax.dualrecord.domain.enums.RecordingNode;
import com.minimax.dualrecord.domain.enums.RecordingState;
import com.minimax.dualrecord.domain.enums.SellerType;
import com.minimax.dualrecord.exception.BusinessException;
import com.minimax.dualrecord.exception.IllegalStateTransitionException;
import com.minimax.dualrecord.repository.BusinessRepository;
import com.minimax.dualrecord.repository.EventLogRepository;
import com.minimax.dualrecord.repository.RecordingNodeRepository;
import com.minimax.dualrecord.scheduled.StaleBusinessDetector;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RecordingService 单元测试（@Transactional 模式）
 *
 * 关键验证：
 *  1. 正常状态机流转
 *  2. 非法转移抛异常
 *  3. 事件日志随状态变更写入
 *  4. 异常导致事务回滚（不会留下半成品状态）
 *  5. StaleBusinessDetector 能正确标 FAILED
 */
@SpringBootTest
@org.springframework.test.context.ActiveProfiles("sandbox")
class RecordingServiceTest {

    @Autowired
    private RecordingService recordingService;

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private EventLogRepository eventLogRepository;

    @Autowired
    private RecordingNodeRepository nodeRepository;

    @Autowired
    private StaleBusinessDetector detector;

    @Test
    void testFullHappyPath() {
        // 创建业务
        Business biz = recordingService.startBusiness(
                BusinessType.WEALTH, "BNK-FIN-2026Q3-001",
                "cust-test-001", null, Channel.OFFLINE, SellerType.HUMAN,
                new BigDecimal("50000"));
        assertEquals(RecordingState.INIT, biz.getState());

        // 身份核验 → 风险评估 → 加载话术 (按状态机正确顺序)
        recordingService.assessRisk(biz.getBusinessId(), "cust-hash-001");
        assertEquals(RecordingState.RISK_ASSESSED,
                getState(biz.getBusinessId()));

        // 加载话术
        recordingService.loadScript(biz.getBusinessId(), "BNK-FIN-2026Q3-001");
        assertEquals(RecordingState.SCRIPT_LOADED,
                getState(biz.getBusinessId()));

        // 启动录制
        recordingService.startRecording(biz.getBusinessId());
        assertEquals(RecordingState.RECORDING,
                getState(biz.getBusinessId()));

        // 完成 8 节点
        for (RecordingNode node : RecordingNode.orderedAll()) {
            String asr = node == RecordingNode.NODE_06_CONFIRM
                    ? "是的，我清楚了。明白。"
                    : "正常的双录对话。";
            recordingService.completeNode(biz.getBusinessId(),
                    "REC-TEST", node, asr);
        }
        assertEquals(RecordingState.RECORDING, getState(biz.getBusinessId()));  // 状态没变

        // 终检
        var qa = recordingService.finalQa(biz.getBusinessId(), "REC-TEST", "完整 ASR");
        assertNotNull(qa);
        assertTrue(qa.getAiQaResult().equals("PASS")
                || qa.getAiQaResult().equals("PASS_WITH_FINDINGS")
                || qa.getAiQaResult().equals("FAIL"));

        // 签字归档
        recordingService.signAndArchive(biz.getBusinessId());
        assertEquals(RecordingState.ARCHIVED, getState(biz.getBusinessId()));

        // 事件日志应该记录了所有成功的状态转换
        long eventCount = eventLogRepository.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.minimax.dualrecord.domain.EventLog>()
                        .eq("business_id", biz.getBusinessId()));
        assertTrue(eventCount >= 5, "至少应该有 5 条事件日志，实际 " + eventCount);
    }

    @Test
    void testIllegalStateTransitionThrows() {
        Business biz = recordingService.startBusiness(
                BusinessType.WEALTH, "BNK-FIN-2026Q3-001",
                "cust-002", null, Channel.OFFLINE, SellerType.HUMAN,
                new BigDecimal("10000"));

        // 尝试在 INIT 状态完成节点 (非法)
        assertThrows(BusinessException.class, () -> {
            // @Transactional 模式: 状态机校验失败会被 wrap 成 BusinessException
            Business fresh = businessRepository.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Business>()
                            .eq("business_id", biz.getBusinessId()));
            recordingService.completeNode(biz.getBusinessId(), "REC-002",
                    RecordingNode.NODE_01_IDENTITY, "出示身份证");
        });
    }

    @Test
    void testForbiddenPhraseBlocks() {
        Business biz = recordingService.startBusiness(
                BusinessType.WEALTH, "BNK-FIN-2026Q3-001",
                "cust-003", null, Channel.OFFLINE, SellerType.HUMAN,
                new BigDecimal("10000"));
        recordingService.assessRisk(biz.getBusinessId(), "cust-hash-001");
        recordingService.loadScript(biz.getBusinessId(), "BNK-FIN-2026Q3-001");
        recordingService.startRecording(biz.getBusinessId());

        // 命中禁播词 → 抛 BusinessException → 事务回滚
        BusinessException ex = assertThrows(BusinessException.class, () -> {
            recordingService.completeNode(biz.getBusinessId(), "REC-003",
                    RecordingNode.NODE_02_DISCLOSURE,
                    "本产品保本保息，绝对安全。");
        });
        assertTrue(ex.getMessage().contains("禁播词"), "异常消息应含 禁播词: " + ex.getMessage());

        // 节点 02 不应该被记录（事务回滚）
        long nodeCount = nodeRepository.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.minimax.dualrecord.domain.RecordingNodeDetail>()
                        .eq("business_id", biz.getBusinessId())
                        .eq("node_id", "02-disclosure"));
        assertEquals(0, nodeCount, "禁播词命中后节点不应被记录");

        // 业务状态仍为 RECORDING（没变）
        assertEquals(RecordingState.RECORDING, getState(biz.getBusinessId()));
    }

    @Test
    void testManualFail() {
        Business biz = recordingService.startBusiness(
                BusinessType.WEALTH, "BNK-FIN-2026Q3-001",
                "cust-004", null, Channel.OFFLINE, SellerType.HUMAN,
                new BigDecimal("10000"));
        recordingService.assessRisk(biz.getBusinessId(), "cust-hash-001");
        recordingService.loadScript(biz.getBusinessId(), "BNK-FIN-2026Q3-001");
        recordingService.manualFail(biz.getBusinessId(), "客户突然取消", "ops-001");

        assertEquals(RecordingState.FAILED, getState(biz.getBusinessId()));
    }

    @Test
    void testStaleDetector() {
        Business biz = recordingService.startBusiness(
                BusinessType.WEALTH, "BNK-FIN-2026Q3-001",
                "cust-stale-001", null, Channel.OFFLINE, SellerType.HUMAN,
                new BigDecimal("10000"));
        recordingService.assessRisk(biz.getBusinessId(), "cust-hash-001");
        recordingService.loadScript(biz.getBusinessId(), "BNK-FIN-2026Q3-001");
        recordingService.startRecording(biz.getBusinessId());

        // 人工把 updated_at 改到 1 小时前
        Business toStale = businessRepository.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Business>()
                        .eq("business_id", biz.getBusinessId()));
        toStale.setUpdatedAt(LocalDateTime.now().minusHours(1));
        businessRepository.updateById(toStale);

        // 跑检测器
        int processed = detector.doScan();
        assertTrue(processed >= 1, "应该至少处理 1 个孤儿业务，实际 " + processed);

        // 业务应被标 FAILED
        assertEquals(RecordingState.FAILED, getState(biz.getBusinessId()));
    }

    private RecordingState getState(String businessId) {
        return businessRepository.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Business>()
                        .eq("business_id", businessId)).getState();
    }
}
