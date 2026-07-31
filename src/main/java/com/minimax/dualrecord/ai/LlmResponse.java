package com.minimax.dualrecord.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LLM 调用响应
 *
 * 关键字段：
 *  - aiAppId / modelVersion：监管报送用
 *  - regulationRef：合规依据
 *  - tokenUsage：成本核算
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmResponse {
    /** LLM 输出的正文 */
    private String content;
    /** AI 应用备案号 */
    private String aiAppId;
    /** 模型版本号 */
    private String modelVersion;
    /** 提供商 */
    private String provider;
    /** 合规依据（哪条规则允许这次调用） */
    private String regulationRef;
    /** 输入 token */
    private Integer promptTokens;
    /** 输出 token */
    private Integer completionTokens;
    /** 总 token */
    private Integer totalTokens;
    /** 延迟（ms） */
    private Long latencyMs;
    /** 是否被合规网关拦截 */
    private Boolean blocked;
    /** 拦截原因 */
    private String blockReason;
}
