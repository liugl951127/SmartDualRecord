package com.minimax.dualrecord.service;

import com.minimax.dualrecord.domain.Business;
import com.minimax.dualrecord.domain.EventLog;
import com.minimax.dualrecord.domain.Recording;
import com.minimax.dualrecord.domain.enums.BusinessType;
import com.minimax.dualrecord.domain.enums.Channel;
import com.minimax.dualrecord.domain.enums.RecordingState;
import com.minimax.dualrecord.domain.enums.SellerType;
import com.minimax.dualrecord.repository.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * RecordingService 扩展测试
 * 覆盖: emergency-stop / link-rec / audit-review / follow-up 集成
 */
class RecordingServiceExtendedTest {

    @Test
    void testEmergencyStopAI_WritesEvent() {
        BusinessRepository bizRepo = mock(BusinessRepository.class);
        RecordingRepository recRepo = mock(RecordingRepository.class);
        RecordingNodeRepository nodeRepo = mock(RecordingNodeRepository.class);
        EventLogRepository evtRepo = mock(EventLogRepository.class);
        ScriptService scriptSvc = mock(ScriptService.class);
        RiskAssessmentService riskSvc = mock(RiskAssessmentService.class);
        ComplianceService compSvc = mock(ComplianceService.class);
        QaService qaSvc = mock(QaService.class);
        FollowUpService followUpSvc = mock(FollowUpService.class);
        SensitiveDataMasker masker = mock(SensitiveDataMasker.class);
        RealTimeCoachingService coachSvc = mock(RealTimeCoachingService.class);
        com.minimax.dualrecord.ai.LlmGateway llm = mock(com.minimax.dualrecord.ai.LlmGateway.class);
        com.minimax.dualrecord.ai.AsrService asr = mock(com.minimax.dualrecord.ai.AsrService.class);
        com.minimax.dualrecord.ai.DeepfakeDetector df = mock(com.minimax.dualrecord.ai.DeepfakeDetector.class);
        com.minimax.dualrecord.util.BusinessIdGenerator idGen = mock(com.minimax.dualrecord.util.BusinessIdGenerator.class);

        RecordingService svc = new RecordingService(bizRepo, recRepo, nodeRepo, evtRepo,
                scriptSvc, riskSvc, compSvc, qaSvc, followUpSvc, llm, asr, df, masker, coachSvc, idGen);

        svc.emergencyStopAI("admin01", "监管要求");

        verify(evtRepo, times(1)).insert(any(EventLog.class));
    }

    @Test
    void testLinkRecordings_SameBusiness_OK() {
        BusinessRepository bizRepo = mock(BusinessRepository.class);
        RecordingRepository recRepo = mock(RecordingRepository.class);
        EventLogRepository evtRepo = mock(EventLogRepository.class);
        ScriptService scriptSvc = mock(ScriptService.class);
        RiskAssessmentService riskSvc = mock(RiskAssessmentService.class);
        ComplianceService compSvc = mock(ComplianceService.class);
        QaService qaSvc = mock(QaService.class);
        FollowUpService followUpSvc = mock(FollowUpService.class);
        SensitiveDataMasker masker = mock(SensitiveDataMasker.class);
        RealTimeCoachingService coachSvc = mock(RealTimeCoachingService.class);
        com.minimax.dualrecord.ai.LlmGateway llm = mock(com.minimax.dualrecord.ai.LlmGateway.class);
        com.minimax.dualrecord.ai.AsrService asr = mock(com.minimax.dualrecord.ai.AsrService.class);
        com.minimax.dualrecord.ai.DeepfakeDetector df = mock(com.minimax.dualrecord.ai.DeepfakeDetector.class);
        com.minimax.dualrecord.util.BusinessIdGenerator idGen = mock(com.minimax.dualrecord.util.BusinessIdGenerator.class);

        Recording primary = new Recording();
        primary.setRecId("REC-PRIMARY");
        primary.setBusinessId("BNK-001");
        primary.setChannel(Channel.OFFLINE);
        Recording linked = new Recording();
        linked.setRecId("REC-LINKED");
        linked.setBusinessId("BNK-001");
        linked.setChannel(Channel.REMOTE_VIDEO);

        when(recRepo.selectOne(any(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper.class)))
                .thenReturn(primary)
                .thenReturn(linked);

        RecordingService svc = new RecordingService(bizRepo, recRepo, mock(RecordingNodeRepository.class), evtRepo,
                scriptSvc, riskSvc, compSvc, qaSvc, followUpSvc, llm, asr, df, masker, coachSvc, idGen);

        // 不应抛异常
        assertDoesNotThrow(() -> svc.linkRecordings("REC-PRIMARY", "REC-LINKED", "admin01"));
        verify(recRepo, times(2)).updateById(any(Recording.class));
    }

    @Test
    void testLinkRecordings_DifferentBusiness_Fails() {
        BusinessRepository bizRepo = mock(BusinessRepository.class);
        RecordingRepository recRepo = mock(RecordingRepository.class);
        EventLogRepository evtRepo = mock(EventLogRepository.class);
        ScriptService scriptSvc = mock(ScriptService.class);
        RiskAssessmentService riskSvc = mock(RiskAssessmentService.class);
        ComplianceService compSvc = mock(ComplianceService.class);
        QaService qaSvc = mock(QaService.class);
        FollowUpService followUpSvc = mock(FollowUpService.class);
        SensitiveDataMasker masker = mock(SensitiveDataMasker.class);
        RealTimeCoachingService coachSvc = mock(RealTimeCoachingService.class);
        com.minimax.dualrecord.ai.LlmGateway llm = mock(com.minimax.dualrecord.ai.LlmGateway.class);
        com.minimax.dualrecord.ai.AsrService asr = mock(com.minimax.dualrecord.ai.AsrService.class);
        com.minimax.dualrecord.ai.DeepfakeDetector df = mock(com.minimax.dualrecord.ai.DeepfakeDetector.class);
        com.minimax.dualrecord.util.BusinessIdGenerator idGen = mock(com.minimax.dualrecord.util.BusinessIdGenerator.class);

        Recording primary = new Recording();
        primary.setRecId("REC-A");
        primary.setBusinessId("BNK-001");
        primary.setChannel(Channel.OFFLINE);
        Recording linked = new Recording();
        linked.setRecId("REC-B");
        linked.setBusinessId("BNK-002");  // 不同业务
        linked.setChannel(Channel.REMOTE_VIDEO);

        when(recRepo.selectOne(any(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper.class)))
                .thenReturn(primary)
                .thenReturn(linked);

        RecordingService svc = new RecordingService(bizRepo, recRepo, mock(RecordingNodeRepository.class), evtRepo,
                scriptSvc, riskSvc, compSvc, qaSvc, followUpSvc, llm, asr, df, masker, coachSvc, idGen);

        assertThrows(com.minimax.dualrecord.exception.BusinessException.class,
                () -> svc.linkRecordings("REC-A", "REC-B", "admin01"));
    }

    @Test
    void testSignAndArchive_SchedulesFollowUps() {
        BusinessRepository bizRepo = mock(BusinessRepository.class);
        RecordingRepository recRepo = mock(RecordingRepository.class);
        RecordingNodeRepository nodeRepo = mock(RecordingNodeRepository.class);
        EventLogRepository evtRepo = mock(EventLogRepository.class);
        ScriptService scriptSvc = mock(ScriptService.class);
        RiskAssessmentService riskSvc = mock(RiskAssessmentService.class);
        ComplianceService compSvc = mock(ComplianceService.class);
        QaService qaSvc = mock(QaService.class);
        FollowUpService followUpSvc = mock(FollowUpService.class);
        SensitiveDataMasker masker = mock(SensitiveDataMasker.class);
        RealTimeCoachingService coachSvc = mock(RealTimeCoachingService.class);
        com.minimax.dualrecord.ai.LlmGateway llm = mock(com.minimax.dualrecord.ai.LlmGateway.class);
        com.minimax.dualrecord.ai.AsrService asr = mock(com.minimax.dualrecord.ai.AsrService.class);
        com.minimax.dualrecord.ai.DeepfakeDetector df = mock(com.minimax.dualrecord.ai.DeepfakeDetector.class);
        com.minimax.dualrecord.util.BusinessIdGenerator idGen = mock(com.minimax.dualrecord.util.BusinessIdGenerator.class);

        Business b = newBusiness();
        // selectOne for business_id lookup (new behavior)
        when(bizRepo.selectOne(any(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper.class)))
                .thenReturn(b);
        // 状态机允许 SIGNED → ARCHIVED
        when(recRepo.selectOne(any(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper.class)))
                .thenReturn(new Recording());

        RecordingService svc = new RecordingService(bizRepo, recRepo, nodeRepo, evtRepo,
                scriptSvc, riskSvc, compSvc, qaSvc, followUpSvc, llm, asr, df, masker, coachSvc, idGen);

        svc.signAndArchive("BNK-001");

        verify(followUpSvc, times(1)).scheduleThreeFollowUps("BNK-001");
    }

    private Business newBusiness() {
        Business b = new Business();
        b.setBusinessId("BNK-001");
        b.setBusinessType(BusinessType.WEALTH);
        b.setProductId("BNK-FIN-001");
        b.setCustomerIdHash("c1");
        b.setSellerIdHash("s1");
        b.setChannel(Channel.OFFLINE);
        b.setState(RecordingState.SIGNED);
        b.setAmount(new BigDecimal("100"));
        b.setProductRiskLevel("R2");
        b.setCreatedAt(LocalDateTime.now());
        b.setUpdatedAt(LocalDateTime.now());
        b.setDeleted(0);
        return b;
    }
}
