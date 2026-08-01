package com.minimax.dualrecord.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.minimax.dualrecord.domain.RecordingAnnotation;
import com.minimax.dualrecord.repository.RecordingAnnotationRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 录像事件标注测试 (v1.2)
 */
class RecordingAnnotationServiceTest {

    @Test
    void testAnnotate_InsertsEvent() {
        RecordingAnnotationRepository repo = mock(RecordingAnnotationRepository.class);
        RecordingAnnotationService svc = new RecordingAnnotationService(repo);

        var a = svc.annotate("REC-001", "BNK-001", "NODE_START", "02-disclosure",
                60_000L, "Node 2 started", "system");

        verify(repo, times(1)).insert(any(RecordingAnnotation.class));
        assertEquals("REC-001", a.getRecId());
        assertEquals("NODE_START", a.getAnnotationType());
        assertEquals(60_000L, a.getTimestampMs());
    }

    @Test
    void testAnnotateNodeProgress_Inserts2Events() {
        RecordingAnnotationRepository repo = mock(RecordingAnnotationRepository.class);
        RecordingAnnotationService svc = new RecordingAnnotationService(repo);

        int n = svc.annotateNodeProgress("REC-001", "BNK-001", "02-disclosure",
                60_000L, 120_000L, "system");

        assertEquals(2, n, "应插入 2 条 (NODE_START + NODE_END)");
        verify(repo, times(2)).insert(any(RecordingAnnotation.class));
    }

    @Test
    void testListByRecId() {
        RecordingAnnotationRepository repo = mock(RecordingAnnotationRepository.class);
        RecordingAnnotationService svc = new RecordingAnnotationService(repo);

        svc.listByRecId("REC-001");
        verify(repo, times(1)).selectList(any(QueryWrapper.class));
    }

    @Test
    void testAnnotationTypes() {
        // 验证 8 个标准标注类型常量
        assertEquals("NODE_START", RecordingAnnotationService.NODE_START);
        assertEquals("NODE_END", RecordingAnnotationService.NODE_END);
        assertEquals("RISK_DISCLOSED", RecordingAnnotationService.RISK_DISCLOSED);
        assertEquals("CUSTOMER_AFFIRMATIVE", RecordingAnnotationService.CUSTOMER_AFFIRMATIVE);
        assertEquals("SIGNED", RecordingAnnotationService.SIGNED);
        assertEquals("MANUAL_FLAG", RecordingAnnotationService.MANUAL_FLAG);
        assertEquals("FORBIDDEN_PHRASE_HIT", RecordingAnnotationService.FORBIDDEN_PHRASE_HIT);
        assertEquals("DEEPFAKE_SUSPECT", RecordingAnnotationService.DEEPFAKE_SUSPECT);
    }
}
