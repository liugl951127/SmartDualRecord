package com.minimax.dualrecord.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.minimax.dualrecord.domain.UploadSession;
import com.minimax.dualrecord.repository.UploadSessionRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 录像分片上传/断点续传测试 (v1.2)
 */
class RecordingResumableServiceTest {

    @Test
    void testInitSession() {
        UploadSessionRepository repo = mock(UploadSessionRepository.class);
        RecordingResumableService svc = new RecordingResumableService(repo);

        var s = svc.initSession("BNK-001", "OFFLINE", 100, 524_288_000L, 5_242_880);

        assertNotNull(s.getSessionId());
        assertTrue(s.getSessionId().startsWith("US"));
        assertEquals(100, s.getTotalChunks());
        assertEquals(0, s.getUploadedChunks());
        assertEquals("IN_PROGRESS", s.getStatus());
        assertEquals(5_242_880, s.getChunkSize());
    }

    @Test
    void testUploadChunk_FirstChunk() {
        UploadSessionRepository repo = mock(UploadSessionRepository.class);
        RecordingResumableService svc = new RecordingResumableService(repo);

        UploadSession existing = new UploadSession();
        existing.setSessionId("US-001");
        existing.setBusinessId("BNK-001");
        existing.setTotalChunks(3);
        existing.setUploadedChunks(0);
        existing.setStatus("IN_PROGRESS");
        existing.setExpiresAt(LocalDateTime.now().plusDays(7));
        when(repo.selectOne(any(QueryWrapper.class))).thenReturn(existing);

        var r = svc.uploadChunk("US-001", 0, new byte[5_242_880]);

        assertEquals(0, r.chunkIndex());
        assertEquals(1, r.uploaded());
        assertEquals(3, r.total());
        assertFalse(r.complete());
        verify(repo, times(1)).updateById(any(UploadSession.class));
    }

    @Test
    void testUploadChunk_LastChunk_Complete() {
        UploadSessionRepository repo = mock(UploadSessionRepository.class);
        RecordingResumableService svc = new RecordingResumableService(repo);

        UploadSession existing = new UploadSession();
        existing.setSessionId("US-001");
        existing.setTotalChunks(3);
        existing.setUploadedChunks(2);
        existing.setStatus("IN_PROGRESS");
        existing.setExpiresAt(LocalDateTime.now().plusDays(7));
        when(repo.selectOne(any(QueryWrapper.class))).thenReturn(existing);

        var r = svc.uploadChunk("US-001", 2, new byte[5_242_880]);

        assertEquals(3, r.uploaded());
        assertTrue(r.complete(), "上传最后 1 片应 complete=true");
    }

    @Test
    void testUploadChunk_ExpiredSession() {
        UploadSessionRepository repo = mock(UploadSessionRepository.class);
        RecordingResumableService svc = new RecordingResumableService(repo);

        UploadSession expired = new UploadSession();
        expired.setSessionId("US-001");
        expired.setTotalChunks(3);
        expired.setStatus("IN_PROGRESS");
        expired.setExpiresAt(LocalDateTime.now().minusDays(1));  // 已过期
        when(repo.selectOne(any(QueryWrapper.class))).thenReturn(expired);

        assertThrows(IllegalStateException.class,
                () -> svc.uploadChunk("US-001", 0, new byte[10]));
    }

    @Test
    void testUploadChunk_InvalidIndex() {
        UploadSessionRepository repo = mock(UploadSessionRepository.class);
        RecordingResumableService svc = new RecordingResumableService(repo);

        UploadSession existing = new UploadSession();
        existing.setSessionId("US-001");
        existing.setTotalChunks(3);
        existing.setStatus("IN_PROGRESS");
        existing.setExpiresAt(LocalDateTime.now().plusDays(7));
        when(repo.selectOne(any(QueryWrapper.class))).thenReturn(existing);

        assertThrows(IllegalArgumentException.class,
                () -> svc.uploadChunk("US-001", 99, new byte[10]));
    }

    @Test
    void testFinalize() {
        UploadSessionRepository repo = mock(UploadSessionRepository.class);
        RecordingResumableService svc = new RecordingResumableService(repo);

        UploadSession ready = new UploadSession();
        ready.setSessionId("US-001");
        ready.setTotalChunks(3);
        ready.setUploadedChunks(3);
        ready.setStatus("IN_PROGRESS");
        when(repo.selectOne(any(QueryWrapper.class))).thenReturn(ready);

        String recId = svc.finalizeUpload("US-001");
        assertNotNull(recId);
        assertTrue(recId.startsWith("REC"));
    }

    @Test
    void testFinalize_NotComplete() {
        UploadSessionRepository repo = mock(UploadSessionRepository.class);
        RecordingResumableService svc = new RecordingResumableService(repo);

        UploadSession incomplete = new UploadSession();
        incomplete.setSessionId("US-001");
        incomplete.setTotalChunks(10);
        incomplete.setUploadedChunks(3);  // 不够
        incomplete.setStatus("IN_PROGRESS");
        when(repo.selectOne(any(QueryWrapper.class))).thenReturn(incomplete);

        assertThrows(IllegalStateException.class, () -> svc.finalizeUpload("US-001"));
    }
}
