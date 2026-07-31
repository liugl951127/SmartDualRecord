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
     * 4 路分项打分
     */
    DetectionResult detailedDetect(byte[] videoFrame, byte[] audioChunk);
}
