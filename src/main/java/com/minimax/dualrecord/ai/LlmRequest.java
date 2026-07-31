package com.minimax.dualrecord.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LLM 调用请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmRequest {
    /** 业务 ID（用于审计追踪） */
    private String businessId;
    /** system prompt */
    private String systemPrompt;
    /** user prompt */
    private String userPrompt;
    /** 业务类型（保险/理财/基金） */
    private String businessType;
    /** 风险等级（C1-C5） */
    private String riskLevel;
    /** 期望的最大 token 数 */
    private Integer maxTokens;
    /** 温度参数（越低越确定） */
    private Double temperature;
    /** 是否需要 JSON 输出 */
    private Boolean jsonMode;
}
