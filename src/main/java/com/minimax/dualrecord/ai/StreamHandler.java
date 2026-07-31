package com.minimax.dualrecord.ai;

/**
 * LLM 流式响应处理器
 * 用于数字人坐席场景（800ms 内必须开始说话）
 */
@FunctionalInterface
public interface StreamHandler {
    void onChunk(String content, boolean isLast);
}
