package com.minimax.dualrecord.service;

import com.minimax.dualrecord.domain.Business;
import com.minimax.dualrecord.domain.Recording;
import com.minimax.dualrecord.domain.enums.Channel;
import com.minimax.dualrecord.domain.enums.SellerType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 32 项录像合规检查测试 (v1.2)
 */
class RecordingComplianceServiceTest {

    private final RecordingComplianceService service = new RecordingComplianceService();

    private Recording sampleRecording() {
        Recording r = new Recording();
        r.setRecId("REC-2026-001");
        r.setBusinessId("BNK-001");
        r.setChannel(Channel.OFFLINE);
        r.setSellerType(SellerType.HUMAN);
        r.setRecStartUtc(LocalDateTime.now().minusMinutes(30));
        r.setRecEndUtc(LocalDateTime.now());
        r.setDurationMs(1_800_000L);
        r.setEncryption("SM4-CBC");
        r.setRetentionUntil(LocalDate.now().plusYears(10));
        r.setFileSha256("a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6a7b8c9d0");
        r.setBlockchainTx("0xabc123def456");
        r.setEncryptionIv("iv-001");
        r.setSignedHash("signed-001");
        r.setLocationLat(new BigDecimal("39.9042"));
        r.setLocationLng(new BigDecimal("116.4074"));
        r.setIpAddress("192.168.1.1");
        r.setDeviceFingerprint("device-001");
        return r;
    }

    private Business sampleBusiness() {
        Business b = new Business();
        b.setBusinessId("BNK-001");
        return b;
    }

    @Test
    void testCheck_FullPass() {
        var report = service.check(sampleRecording(), sampleBusiness());
        // 打印哪些没通过
        for (var r : report.results()) {
            if (!r.passed()) {
                System.out.println("FAIL #" + r.seq() + " " + r.name() + " actual=" + r.actualValue() + " severity=" + r.severity());
            }
        }
        assertEquals(RecordingComplianceService.ComplianceStatus.PASS, report.status(),
                "全部 OK 应 PASS, score=" + report.score() + ", critical=" + report.criticalCount());
        assertEquals(100, report.score());
        assertEquals(0, report.criticalCount());
        assertEquals(32, report.results().size(), "应跑 32 项");
    }

    @Test
    void testCheck_DigitalHumanMissingWatermark_Fail() {
        Recording r = sampleRecording();
        r.setChannel(Channel.SELF_AI);
        r.setWatermarkVisible(0);  // 数字人必须 watermark=1
        r.setAudioIdPerMinute(0);  // 数字人必须 audio_id >= 1
        var report = service.check(r, sampleBusiness());
        // 数字人缺 watermark + audio_id → 至少 2 FAIL
        assertTrue(report.criticalCount() >= 2,
                "数字人缺标识应有 ≥ 2 FAIL, 实际: " + report.criticalCount());
        assertEquals(RecordingComplianceService.ComplianceStatus.FAIL, report.status());
    }

    @Test
    void testCheck_HighBlackFrameRatio_Fail() {
        Recording r = sampleRecording();
        r.setBlackFrameRatio(new BigDecimal("50"));  // 黑屏 50% > 30% 阈值
        var report = service.check(r, sampleBusiness());
        assertTrue(report.criticalCount() >= 1, "高黑屏率应 FAIL");
    }

    @Test
    void testCheck_ThirdPartyPresent_Fail() {
        Recording r = sampleRecording();
        r.setThirdPartyCount(2);  // 出现第 3 方
        var report = service.check(r, sampleBusiness());
        assertEquals(RecordingComplianceService.ComplianceStatus.FAIL, report.status());
    }

    @Test
    void testCheck_FaceRatioTooLow_Fail() {
        Recording r = sampleRecording();
        r.setCustomerFaceRatio(new BigDecimal("50"));  // < 80% 阈值
        var report = service.check(r, sampleBusiness());
        assertTrue(report.criticalCount() >= 1, "客户人脸在场率低应 FAIL");
    }

    @Test
    void testCheck_LowResolution_Warn() {
        Recording r = sampleRecording();
        r.setResolution("640x480");  // 低于 1280x720
        r.setFps(15);                // 低于 20
        var report = service.check(r, sampleBusiness());
        assertTrue(report.warnCount() >= 2, "低分辨率/帧率应 WARN");
    }

    @Test
    void testCheck_RetentionTooShort_Fail() {
        Recording r = sampleRecording();
        r.setRetentionUntil(LocalDate.now().plusYears(5));  // < 10 年
        var report = service.check(r, sampleBusiness());
        assertTrue(report.criticalCount() >= 1, "留存 < 10 年应 FAIL");
    }

    @Test
    void testIsBlocking() {
        Recording r = sampleRecording();
        r.setEncryption(null);  // 无加密 → FAIL
        var report = service.check(r, sampleBusiness());
        assertTrue(service.isBlocking(report));
    }

    @Test
    void testCheckListHas32Items() {
        assertEquals(32, RecordingComplianceService.CHECK_DEFINITIONS.size(),
                "应有 32 项检查");
    }
}
