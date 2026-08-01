package com.minimax.dualrecord.ai;

/**
 * 反深伪检测器接口
 *
 * 4 路并行检测：
 *  - VISUAL_FACE_SWAP  人脸换脸
 *  - VOICE_CLONE        语音克隆
 *  - PHYSIOLOGICAL      生理指标（眨眼/微表情/唇形）
 *  - CROSS_MODAL        跨模态一致性
 *
 * 任意一路 > 0.92 触发兜底（暂停双录 + 人工接管 + 2h 内上报监管）
 */
public interface DeepfakeDetector {

    /**
     * 单次综合检测
     */
    DetectionResult detect(byte[] videoFrame, byte[] audioChunk);

    /**
     * 4 路分项打分 (旧 API, 等价 detectParallel4Ways)
     */
    DetectionResult detailedDetect(byte[] videoFrame, byte[] audioChunk);

    /**
     * 4 路并行推理 (新 API)
     *  - CompletableFuture.allOf 4 路并发
     *  - 任意一路 ≥ 0.92 强制人工
     *  - 任意一路 ≥ 0.85 标红
     *  - 跨模态 ≥ 0.70 强制人工
     *  - 返回值含 latencyMs (4 路并发总耗时)
     */
    DetectionResult detectParallel4Ways(byte[] videoFrame, byte[] audioChunk);
}
