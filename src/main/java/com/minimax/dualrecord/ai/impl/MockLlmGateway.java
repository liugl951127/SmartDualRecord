package com.minimax.dualrecord.ai.impl;

import com.minimax.dualrecord.ai.LlmGateway;
import com.minimax.dualrecord.ai.LlmRequest;
import com.minimax.dualrecord.ai.LlmResponse;
import com.minimax.dualrecord.ai.StreamHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Mock LLM 网关 · 沙箱模式使用
 * 根据 user prompt 关键词返回固定响应，模拟 LLM 行为
 *
 * 真实环境对接：
 *  - 阿里云 DashScope（qwen-max）
 *  - DeepSeek API
 *  - OpenAI API
 *  - 自研模型
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "dual-record.ai-gateway.providers[0].endpoint", havingValue = "local://mock", matchIfMissing = true)
public class MockLlmGateway implements LlmGateway {

    @Override
    public LlmResponse complete(LlmRequest request) {
        log.debug("Mock LLM 调用: businessId={}, prompt={}", request.getBusinessId(),
                request.getUserPrompt() != null ? request.getUserPrompt().substring(0, Math.min(50, request.getUserPrompt().length())) : "null");

        String content = mockResponse(request.getUserPrompt());
        return LlmResponse.builder()
                .content(content)
                .aiAppId("AI-DR-2026-MOCK")
                .modelVersion("mock-llm-v1")
                .provider("mock")
                .regulationRef("金发〔2026〕8号-第二十六条")
                .promptTokens(request.getUserPrompt() != null ? request.getUserPrompt().length() / 2 : 0)
                .completionTokens(content.length() / 2)
                .totalTokens((request.getUserPrompt() != null ? request.getUserPrompt().length() / 2 : 0) + content.length() / 2)
                .latencyMs(150L)
                .blocked(false)
                .build();
    }

    @Override
    public void streamComplete(LlmRequest request, StreamHandler handler) {
        // 模拟流式：分 3 段推送
        String content = mockResponse(request.getUserPrompt());
        int chunkSize = content.length() / 3;
        for (int i = 0; i < 3; i++) {
            int end = Math.min(content.length(), (i + 1) * chunkSize);
            String chunk = content.substring(i * chunkSize, end);
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {}
            handler.onChunk(chunk, i == 2);
        }
    }

    private String mockResponse(String prompt) {
        if (prompt == null) return "您好，请说出您的需求。";
        if (prompt.contains("风险揭示") || prompt.contains("disclosure")) {
            return "本产品为非保本浮动收益型理财，不保证本金和收益，业绩比较基准不代表实际收益，过往业绩不代表未来表现。";
        }
        if (prompt.contains("肯定") || prompt.contains("确认") || prompt.contains("affirm")) {
            return "{\"affirmative\": true, \"confidence\": 0.96, \"matched_keywords\": [\"是的\", \"清楚\", \"明白\"]}";
        }
        if (prompt.contains("质检") || prompt.contains("qa") || prompt.contains("review")) {
            return "{\"score\": 92.5, \"result\": \"PASS_WITH_FINDINGS\", \"issues\": [{\"type\": \"LATE_DISCLOSURE\", \"severity\": \"LOW\"}]}";
        }
        if (prompt.contains("话术") || prompt.contains("script")) {
            return "{\"node\": \"02-disclosure\", \"recommended_phrase\": \"建议补充说明封闭期 180 天的具体含义\"}";
        }
        return "您好，我是双录智能助手" + UUID.randomUUID().toString().substring(0, 8) + "，请问需要什么帮助？";
    }
}
