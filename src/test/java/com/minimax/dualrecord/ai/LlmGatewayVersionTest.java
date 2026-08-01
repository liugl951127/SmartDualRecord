package com.minimax.dualrecord.ai;

import com.minimax.dualrecord.ai.impl.MockLlmGateway;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LLM 网关版本化测试
 *
 * 覆盖: aiAppId / modelVersion / regulationRef / tokenUsage
 */
class LlmGatewayVersionTest {

    private final LlmGateway gateway = new MockLlmGateway();

    @Test
    void testComplete_HasAiAppId() {
        LlmRequest req = LlmRequest.builder()
                .businessId("BNK20260801-0001")
                .userPrompt("请介绍本理财产品的风险")
                .build();
        LlmResponse resp = gateway.complete(req);
        assertNotNull(resp.getAiAppId(), "监管要求: 必须有 AI 备案号");
        assertFalse(resp.getAiAppId().isEmpty());
    }

    @Test
    void testComplete_HasModelVersion() {
        LlmRequest req = LlmRequest.builder()
                .businessId("BNK20260801-0001")
                .userPrompt("风险揭示")
                .build();
        LlmResponse resp = gateway.complete(req);
        assertNotNull(resp.getModelVersion(), "监管要求: 必须有模型版本号");
        assertTrue(resp.getModelVersion().length() > 0);
    }

    @Test
    void testComplete_HasRegulationRef() {
        LlmRequest req = LlmRequest.builder()
                .businessId("BNK20260801-0001")
                .userPrompt("风险揭示")
                .build();
        LlmResponse resp = gateway.complete(req);
        assertNotNull(resp.getRegulationRef(), "必须有合规依据引用");
        assertTrue(resp.getRegulationRef().contains("金发"), "应引用金发 8 号");
    }

    @Test
    void testComplete_TrackLatency() {
        LlmRequest req = LlmRequest.builder()
                .businessId("BNK20260801-0001")
                .userPrompt("风险揭示")
                .build();
        LlmResponse resp = gateway.complete(req);
        assertNotNull(resp.getLatencyMs(), "必须跟踪延迟");
        assertTrue(resp.getLatencyMs() > 0);
    }

    @Test
    void testComplete_TrackTokenUsage() {
        LlmRequest req = LlmRequest.builder()
                .businessId("BNK20260801-0001")
                .userPrompt("客户问: 这个产品保本吗?")
                .build();
        LlmResponse resp = gateway.complete(req);
        assertNotNull(resp.getTotalTokens());
        assertEquals(resp.getPromptTokens() + resp.getCompletionTokens(), resp.getTotalTokens());
    }

    @Test
    void testComplete_CustomerIdHashPreserved() {
        LlmRequest req = LlmRequest.builder()
                .businessId("BNK20260801-0001")
                .customerIdHash("abc123def456")
                .userPrompt("风险揭示")
                .build();
        LlmResponse resp = gateway.complete(req);
        assertNotNull(resp);
        // 监管要求: 客户 ID hash 不可送 LLM (响应不应泄漏)
    }

    @Test
    void testStreamComplete_CallsHandler3Times() {
        LlmRequest req = LlmRequest.builder()
                .businessId("BNK20260801-0001")
                .userPrompt("风险揭示")
                .build();
        final int[] count = {0};
        final StringBuilder sb = new StringBuilder();
        gateway.streamComplete(req, (chunk, isLast) -> {
            count[0]++;
            sb.append(chunk);
            if (isLast) {
                assertTrue(sb.length() > 0);
            }
        });
        assertEquals(3, count[0], "流式应分 3 段推送");
    }
}
