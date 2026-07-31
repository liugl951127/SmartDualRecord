package com.minimax.dualrecord.ai.impl;

import com.minimax.dualrecord.ai.DeepfakeDetector;
import com.minimax.dualrecord.ai.DetectionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Mock 反深伪检测器
 * 沙箱里全部返回 NORMAL；生产对接 4 路深度学习模型
 */
@Slf4j
@Component
public class MockDeepfakeDetector implements DeepfakeDetector {

    @Override
    public DetectionResult detect(byte[] videoFrame, byte[] audioChunk) {
        return detailedDetect(videoFrame, audioChunk);
    }

    @Override
    public DetectionResult detailedDetect(byte[] videoFrame, byte[] audioChunk) {
        log.debug("Mock 反深伪检测");
        return DetectionResult.builder()
                .verdict("NORMAL")
                .overallScore(0.05)  // 极低
                .channelScores(Map.of(
                        "VISUAL_FACE_SWAP", 0.03,
                        "VOICE_CLONE", 0.04,
                        "PHYSIOLOGICAL", 0.06,
                        "CROSS_MODAL", 0.07))
                .requireHumanTakeover(false)
                .evidence("4 路检测均正常，未发现深伪特征")
                .modelVersion("mock-deepfake-v1")
                .build();
    }
}
