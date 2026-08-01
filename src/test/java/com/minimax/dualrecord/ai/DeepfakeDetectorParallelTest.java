package com.minimax.dualrecord.ai;

import com.minimax.dualrecord.ai.impl.MockDeepfakeDetector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 反深伪 4 路并行检测测试
 *
 * 覆盖: 4 路推理结果 / latencyMs / verdict / requireHumanTakeover
 */
class DeepfakeDetectorParallelTest {

    private final DeepfakeDetector detector = new MockDeepfakeDetector();

    @Test
    void testDetectParallel4Ways_AllNormal() {
        DetectionResult r = detector.detectParallel4Ways(null, null);
        assertEquals("NORMAL", r.getVerdict());
        assertFalse(r.getRequireHumanTakeover());
        assertNotNull(r.getChannelScores());
        assertEquals(4, r.getChannelScores().size(), "必须有 4 路分数");
    }

    @Test
    void testDetectParallel4Ways_All4Channels() {
        DetectionResult r = detector.detectParallel4Ways(null, null);
        assertTrue(r.getChannelScores().containsKey("VISUAL_FACE_SWAP"), "人脸换脸");
        assertTrue(r.getChannelScores().containsKey("VOICE_CLONE"), "语音克隆");
        assertTrue(r.getChannelScores().containsKey("PHYSIOLOGICAL"), "生理指标");
        assertTrue(r.getChannelScores().containsKey("CROSS_MODAL"), "跨模态");
    }

    @Test
    void testDetectParallel4Ways_HasLatency() {
        DetectionResult r = detector.detectParallel4Ways(null, null);
        assertNotNull(r.getLatencyMs());
        assertTrue(r.getLatencyMs() >= 0, "latency 应 >= 0");
    }

    @Test
    void testDetectParallel4Ways_HasModelVersion() {
        DetectionResult r = detector.detectParallel4Ways(null, null);
        assertNotNull(r.getModelVersion());
        assertTrue(r.getModelVersion().startsWith("mock-"), "Mock 模式应标 mock 前缀");
    }

    @Test
    void testDetailedDetect_EquivalentToParallel() {
        // detailedDetect 现在转调 detectParallel4Ways
        DetectionResult a = detector.detailedDetect(null, null);
        DetectionResult b = detector.detectParallel4Ways(null, null);
        assertEquals(a.getVerdict(), b.getVerdict());
    }

    @Test
    void testDetectParallel4Ways_ParallelLatency() {
        // 4 路并行应快于串行 (沙箱中所有都 < 50ms, 但合并后 < 50ms)
        DetectionResult r = detector.detectParallel4Ways(null, null);
        assertNotNull(r.getLatencyMs());
        assertTrue(r.getLatencyMs() < 1000, "4 路并行推理应 < 1s");
    }
}
