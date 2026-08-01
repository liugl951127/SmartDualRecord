package com.minimax.dualrecord.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.minimax.dualrecord.domain.RecordingAnnotation;
import com.minimax.dualrecord.repository.RecordingAnnotationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 录像事件标注服务 (v1.2 录像合规)
 *
 * 关键事件打时间戳标记, 便于:
 *  - 监管事后核查 (1 秒内跳到关键事件)
 *  - 客户回看 (直接定位风险揭示时刻)
 *  - 争议处理 (精确定位"是否明确肯定")
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordingAnnotationService {

    private final RecordingAnnotationRepository repository;

    public static final String NODE_START = "NODE_START";
    public static final String NODE_END = "NODE_END";
    public static final String RISK_DISCLOSED = "RISK_DISCLOSED";
    public static final String CUSTOMER_AFFIRMATIVE = "CUSTOMER_AFFIRMATIVE";
    public static final String SIGNED = "SIGNED";
    public static final String MANUAL_FLAG = "MANUAL_FLAG";
    public static final String FORBIDDEN_PHRASE_HIT = "FORBIDDEN_PHRASE_HIT";
    public static final String DEEPFAKE_SUSPECT = "DEEPFAKE_SUSPECT";

    /**
     * 添加 1 个标注
     */
    @Transactional(rollbackFor = Exception.class)
    public RecordingAnnotation annotate(String recId, String businessId, String type,
                                        String nodeId, long timestampMs, String note, String operatorId) {
        RecordingAnnotation a = new RecordingAnnotation();
        a.setRecId(recId);
        a.setBusinessId(businessId);
        a.setAnnotationType(type);
        a.setNodeId(nodeId);
        a.setTimestampMs(timestampMs);
        a.setNote(note);
        a.setOperatorId(operatorId);
        a.setCreatedAt(LocalDateTime.now());
        repository.insert(a);
        log.debug("录像标注: recId={}, type={}, ts={}ms", recId, type, timestampMs);
        return a;
    }

    /**
     * 批量标注 (8 节点进度)
     */
    @Transactional(rollbackFor = Exception.class)
    public int annotateNodeProgress(String recId, String businessId, String nodeId,
                                     long startMs, long endMs, String operatorId) {
        int n = 0;
        RecordingAnnotation start = annotate(recId, businessId, NODE_START, nodeId, startMs,
                "Node " + nodeId + " started", operatorId);
        n++;
        RecordingAnnotation end = annotate(recId, businessId, NODE_END, nodeId, endMs,
                "Node " + nodeId + " ended", operatorId);
        n++;
        return n;
    }

    /**
     * 列出某录像的所有标注 (按时间排序)
     */
    public List<RecordingAnnotation> listByRecId(String recId) {
        return repository.selectList(
                new QueryWrapper<RecordingAnnotation>()
                        .eq("rec_id", recId)
                        .orderByAsc("timestamp_ms"));
    }

    /**
     * 列出某业务的所有标注
     */
    public List<RecordingAnnotation> listByBusinessId(String businessId) {
        return repository.selectList(
                new QueryWrapper<RecordingAnnotation>()
                        .eq("business_id", businessId)
                        .orderByAsc("timestamp_ms"));
    }
}
