package com.minimax.dualrecord.ai.impl;

import com.minimax.dualrecord.ai.DeepfakeDetector;
import com.minimax.dualrecord.ai.DetectionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Mock 反深伪检测器 (4 路并行)
 *
 * <h3>4 路并行</h3>
 *  - VISUAL_FACE_SWAP  人脸换脸 (视频帧)
 *  - VOICE_CLONE        语音克隆 (音频片段)
 *  - PHYSIOLOGICAL      生理指标 (眨眼/微表情/唇形)
 *  - CROSS_MODAL        跨模态一致性 (音视频口型同步)
 *
 * 沙箱中所有 4 路返回低分；生产中对接 4 个独立深度学习模型
 * 用 CompletableFuture + ExecutorService 并行推理
 *
 * <h3>拦截阈值</h3>
 *  - 任意一路 ≥ 0.85 → 标红 + 暂停双录
 *  - 任意一路 ≥ 0.92 → 人工接管 + 2h 内上报监管
 *  - 跨模态 (音视口型) 不一致 ≥ 0.7 → 强制人工
 */
@Slf4j
@Component
public class MockDeepfakeDetector implements DeepfakeDetector {

    /** 4 路并行推理的固定线程池 (生产用专用 GPU 资源池) */
    private final ExecutorService inferencePool = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "deepfake-inference");
        t.setDaemon(true);
        return t;
    });

    /** 4 路阈值 */
    private static final double FLAG_THRESHOLD = 0.85;
    private static final double BLOCK_THRESHOLD = 0.92;
    private static final double CROSS_MODAL_HUMAN = 0.70;

    @Override
    public DetectionResult detect(byte[] videoFrame, byte[] audioChunk) {
        return detailedDetect(videoFrame, audioChunk);
    }

    /**
     * 4 路并行分项打分
     */
    @Override
    public DetectionResult detectParallel4Ways(byte[] videoFrame, byte[] audioChunk) {
        long start = System.currentTimeMillis();

        // 4 路并行推理
        CompletableFuture<Double> faceSwap = CompletableFuture.supplyAsync(
                () -> inferFaceSwap(videoFrame), inferencePool);
        CompletableFuture<Double> voiceClone = CompletableFuture.supplyAsync(
                () -> inferVoiceClone(audioChunk), inferencePool);
        CompletableFuture<Double> physiological = CompletableFuture.supplyAsync(
                () -> inferPhysiological(videoFrame), inferencePool);
        CompletableFuture<Double> crossModal = CompletableFuture.supplyAsync(
                () -> inferCrossModal(videoFrame, audioChunk), inferencePool);

        // 等待全部完成
        CompletableFuture.allOf(faceSwap, voiceClone, physiological, crossModal).join();

        Map<String, Double> scores = new java.util.LinkedHashMap<>();
        scores.put("VISUAL_FACE_SWAP", faceSwap.join());
        scores.put("VOICE_CLONE", voiceClone.join());
        scores.put("PHYSIOLOGICAL", physiological.join());
        scores.put("CROSS_MODAL", crossModal.join());

        double maxScore = scores.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        long latencyMs = System.currentTimeMillis() - start;

        // 决策
        String verdict;
        boolean requireHuman = false;
        String reason;
        if (maxScore >= BLOCK_THRESHOLD) {
            verdict = "BLOCKED";
            requireHuman = true;
            reason = String.format("任意一路 >= 0.92 触发人工接管: max=%.3f", maxScore);
        } else if (maxScore >= FLAG_THRESHOLD) {
            verdict = "FLAGGED";
            requireHuman = true;
            reason = String.format("任意一路 >= 0.85 标红: max=%.3f", maxScore);
        } else if (scores.get("CROSS_MODAL") >= CROSS_MODAL_HUMAN) {
            verdict = "FLAGGED";
            requireHuman = true;
            reason = String.format("跨模态不一致 >= 0.70 强制人工: cross_modal=%.3f", scores.get("CROSS_MODAL"));
        } else {
            verdict = "NORMAL";
            requireHuman = false;
            reason = "4 路检测均正常";
        }

        return DetectionResult.builder()
                .verdict(verdict)
                .overallScore(maxScore)
                .channelScores(scores)
                .requireHumanTakeover(requireHuman)
                .evidence(reason)
                .modelVersion("mock-deepfake-v1")
                .latencyMs(latencyMs)
                .build();
    }

    /**
     * 兼容旧 API: 内部转调 4 路并行
     */
    @Override
    public DetectionResult detailedDetect(byte[] videoFrame, byte[] audioChunk) {
        return detectParallel4Ways(videoFrame, audioChunk);
    }

    // ====================================================================
    // 4 路推理 (Mock, 生产对接实际模型)
    // ====================================================================
    private double inferFaceSwap(byte[] frame) {
        // 沙箱: 始终返回极低分
        return 0.03;
    }
    private double inferVoiceClone(byte[] audio) {
        return 0.04;
    }
    private double inferPhysiological(byte[] frame) {
        return 0.06;
    }
    private double inferCrossModal(byte[] frame, byte[] audio) {
        return 0.05;
    }
}
