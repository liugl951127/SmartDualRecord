package com.minimax.dualrecord.service;

import com.minimax.dualrecord.ai.LlmGateway;
import com.minimax.dualrecord.ai.LlmRequest;
import com.minimax.dualrecord.ai.LlmResponse;
import com.minimax.dualrecord.ai.AsrService;
import com.minimax.dualrecord.ai.AsrResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 实时耳返副驾服务测试
 *
 * 覆盖: 0.5s 目标 / 禁播词兜底 / 降级判断
 */
class RealTimeCoachingServiceTest {

    @Test
    void testCoach_LlmCalled() {
        LlmGateway llm = mock(LlmGateway.class);
        AsrService asr = mock(AsrService.class);
        ComplianceService comp = mock(ComplianceService.class);
        when(comp.scan(any())).thenReturn(List.of());  // 无禁播词
        when(llm.complete(any(LlmRequest.class))).thenReturn(
                LlmResponse.builder()
                        .content("建议补充说明封闭期 180 天的具体含义")
                        .aiAppId("AI-DR-2026")
                        .modelVersion("qa-llm-v3.2.0")
                        .latencyMs(150L)
                        .build());

        RealTimeCoachingService svc = new RealTimeCoachingService(llm, asr, comp);
        Map<String, Object> resp = svc.coach("BNK-001", "客户问封闭期是多久");

        assertNotNull(resp.get("coaching"));
        assertNotNull(resp.get("latencyMs"));
        assertEquals(Boolean.FALSE, resp.get("degraded"));
        verify(llm, times(1)).complete(any(LlmRequest.class));
    }

    @Test
    void testCoach_ForbiddenPhrase_DirectAlert() {
        LlmGateway llm = mock(LlmGateway.class);
        AsrService asr = mock(AsrService.class);
        ComplianceService comp = mock(ComplianceService.class);
        // 命中禁播词
        when(comp.scan(any())).thenReturn(List.of(
                new ComplianceService.Hit("保本", "HIGH", "金发 8 号")));
        RealTimeCoachingService svc = new RealTimeCoachingService(llm, asr, comp);

        Map<String, Object> resp = svc.coach("BNK-001", "这个产品保本保息");

        assertNotNull(resp.get("alerts"));
        assertTrue(((List<?>) resp.get("alerts")).size() > 0);
        // 命中禁播词不应再调 LLM
        verify(llm, never()).complete(any(LlmRequest.class));
    }

    @Test
    void testCoach_DegradedWhenSlow() {
        LlmGateway llm = mock(LlmGateway.class);
        AsrService asr = mock(AsrService.class);
        ComplianceService comp = mock(ComplianceService.class);
        when(comp.scan(any())).thenReturn(List.of());
        when(llm.complete(any(LlmRequest.class))).thenAnswer(inv -> {
            Thread.sleep(1100);  // 模拟慢
            return LlmResponse.builder()
                    .content("慢响应")
                    .aiAppId("AI-DR")
                    .modelVersion("qa-llm-v3.2.0")
                    .latencyMs(1100L)
                    .build();
        });
        RealTimeCoachingService svc = new RealTimeCoachingService(llm, asr, comp);

        Map<String, Object> resp = svc.coach("BNK-001", "客户询问");

        assertEquals(Boolean.TRUE, resp.get("degraded"), "超过 1s 应降级");
    }
}
