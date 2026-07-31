package com.minimax.dualrecord.ai;

/**
 * ASR 服务接口（语音转文字）
 * 沙箱模式下返回固定文本；生产环境对接阿里/百度/讯飞
 */
public interface AsrService {

    /**
     * 同步转写
     * @param audioBytes 音频字节流
     * @return 转写结果（含每句话的时间戳）
     */
    AsrResult transcribe(byte[] audioBytes);

    /**
     * 实时流式转写（WebSocket 推流）
     */
    void streamTranscribe(String businessId, byte[] chunk, StreamHandler handler);
}
