package com.minimax.dualrecord.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.minimax.dualrecord.domain.Recording;
import com.minimax.dualrecord.domain.RecordingAccessLog;
import com.minimax.dualrecord.domain.enums.Channel;
import com.minimax.dualrecord.domain.enums.SellerType;
import com.minimax.dualrecord.repository.RecordingAccessLogRepository;
import com.minimax.dualrecord.repository.RecordingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 录像回放 + DRM 测试 (v1.2)
 */
class RecordingPlaybackServiceTest {

    @Test
    void testIssueToken_Success() {
        RecordingRepository recRepo = mock(RecordingRepository.class);
        RecordingAccessLogRepository logRepo = mock(RecordingAccessLogRepository.class);
        RecordingPlaybackService svc = new RecordingPlaybackService(recRepo, logRepo);
        ReflectionTestUtils.setField(svc, "defaultTtlSec", 300);

        Recording rec = new Recording();
        rec.setRecId("REC-001");
        rec.setBusinessId("BNK-001");
        rec.setChannel(Channel.OFFLINE);
        rec.setSellerType(SellerType.HUMAN);
        rec.setEncryption("SM4-CBC");
        rec.setRetentionUntil(LocalDate.now().plusYears(10));
        rec.setCreatedAt(LocalDateTime.now());
        when(recRepo.selectOne(any(QueryWrapper.class))).thenReturn(rec);

        var token = svc.issueToken("REC-001", "user-001", "CUSTOMER", 300);

        assertNotNull(token.token(), "应签发 token");
        assertEquals("REC-001", token.recId());
        assertEquals("CUSTOMER", token.userRole());
        assertTrue(token.ttlSec() > 0);
        assertTrue(token.url().contains("token="));
        // 写访问日志
        verify(logRepo, times(1)).insert(any(RecordingAccessLog.class));
    }

    @Test
    void testIssueToken_RecordingNotFound() {
        RecordingRepository recRepo = mock(RecordingRepository.class);
        RecordingAccessLogRepository logRepo = mock(RecordingAccessLogRepository.class);
        RecordingPlaybackService svc = new RecordingPlaybackService(recRepo, logRepo);

        when(recRepo.selectOne(any(QueryWrapper.class))).thenReturn(null);

        assertThrows(IllegalArgumentException.class,
                () -> svc.issueToken("REC-XXX", "user-001", "CUSTOMER", 300));
    }

    @Test
    void testHasPermission_AllRoles() {
        RecordingPlaybackService svc = new RecordingPlaybackService(
                mock(RecordingRepository.class), mock(RecordingAccessLogRepository.class));
        Recording r = new Recording();

        assertTrue(svc.hasPermission("CUSTOMER", "u1", r));
        assertTrue(svc.hasPermission("SELLER", "u1", r));
        assertTrue(svc.hasPermission("AUDITOR", "u1", r));
        assertTrue(svc.hasPermission("REGULATOR", "u1", r));
        assertTrue(svc.hasPermission("ADMIN", "u1", r));
        assertFalse(svc.hasPermission("UNKNOWN_ROLE", "u1", r));
    }

    @Test
    void testLogAccess_InsertsLog() {
        RecordingRepository recRepo = mock(RecordingRepository.class);
        RecordingAccessLogRepository logRepo = mock(RecordingAccessLogRepository.class);
        RecordingPlaybackService svc = new RecordingPlaybackService(recRepo, logRepo);

        svc.logAccess("REC-001", "BNK-001", "user-001", "CUSTOMER", "PLAYBACK", 30, "tok123");

        verify(logRepo, times(1)).insert(any(RecordingAccessLog.class));
    }
}
