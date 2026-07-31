package com.minimax.dualrecord.ai;

/**
 * LLM 网关接口
 *
 * 抽象了 4 个 LLM 提供商（阿里 / DeepSeek / OpenAI / 自研）
 * 实际生产中可结合：
 *  - 模型路由（按业务类型 + 风险等级选模型）
 *  - 限流（每个用户/业务/小时的 QPS）
 *  - 备案标识（每个调用都打上 ai_app_id + model_version）
 *  - 失败转移（主备切换）
 *  - 数据脱敏（入参出参都过滤 PII）
 */
public interface LlmGateway {

    /**
     * 同步调用 LLM
     * @param request 调用请求（system prompt + user prompt + 业务元数据）
     * @return 响应（content + token usage + 备案号 + 模型版本）
     */
    LlmResponse complete(LlmRequest request);

    /**
     * 流式调用（用于数字人实时坐席）
     */
    void streamComplete(LlmRequest request, StreamHandler handler);
}
