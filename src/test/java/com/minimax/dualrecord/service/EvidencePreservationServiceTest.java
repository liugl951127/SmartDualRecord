package com.minimax.dualrecord.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.minimax.dualrecord.domain.PreservationRecord;
import com.minimax.dualrecord.domain.Recording;
import com.minimax.dualrecord.repository.PreservationRecordRepository;
import com.minimax.dualrecord.repository.RecordingRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 证据保全服务测试 (v1.2)
 */
class EvidencePreservationServiceTest {

    @Test
    void testSubmit_Success() {
        PreservationRecordRepository prRepo = mock(PreservationRecordRepository.class);
        RecordingRepository recRepo = mock(RecordingRepository.class);
        EvidencePreservationService svc = new EvidencePreservationService(prRepo, recRepo);

        Recording rec = new Recording();
        rec.setRecId("REC-001");
        rec.setBusinessId("BNK-001");
        rec.setFileSha256("a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0");
        when(recRepo.selectOne(any(QueryWrapper.class))).thenReturn(rec);

        var p = svc.submit("REC-001", "auditor-001", "AUDITOR", "客户投诉进入司法");

        assertNotNull(p.getPreservationId());
        assertTrue(p.getPreservationId().startsWith("PR"));
        assertEquals("SUBMITTED", p.getStatus());
        assertEquals("AUDITOR", p.getRequesterRole());
        assertNotNull(p.getPreservationHash());
        assertEquals(64, p.getPreservationHash().length(), "SHA-256 64 字符");
        // 关联到录像
        verify(recRepo, times(1)).updateById(any(Recording.class));
    }

    @Test
    void testSubmit_RecordingNotFound() {
        PreservationRecordRepository prRepo = mock(PreservationRecordRepository.class);
        RecordingRepository recRepo = mock(RecordingRepository.class);
        EvidencePreservationService svc = new EvidencePreservationService(prRepo, recRepo);
        when(recRepo.selectOne(any(QueryWrapper.class))).thenReturn(null);

        assertThrows(IllegalArgumentException.class,
                () -> svc.submit("REC-XXX", "u", "AUDITOR", "test"));
    }

    @Test
    void testNotarize() {
        PreservationRecordRepository prRepo = mock(PreservationRecordRepository.class);
        RecordingRepository recRepo = mock(RecordingRepository.class);
        EvidencePreservationService svc = new EvidencePreservationService(prRepo, recRepo);

        PreservationRecord existing = new PreservationRecord();
        existing.setStatus("SUBMITTED");
        when(prRepo.selectOne(any(QueryWrapper.class))).thenReturn(existing);

        var p = svc.notarize("PR-001", "北京公证处", "GZ-2026-001");

        assertEquals("NOTARIZED", p.getStatus());
        assertEquals("北京公证处", p.getNotaryOrg());
        assertEquals("GZ-2026-001", p.getNotaryCertNo());
    }

    @Test
    void testVerify_NotFound() {
        PreservationRecordRepository prRepo = mock(PreservationRecordRepository.class);
        RecordingRepository recRepo = mock(RecordingRepository.class);
        EvidencePreservationService svc = new EvidencePreservationService(prRepo, recRepo);
        when(prRepo.selectOne(any(QueryWrapper.class))).thenReturn(null);

        var r = svc.verify("PR-XXX");
        assertFalse(r.valid());
    }
}
