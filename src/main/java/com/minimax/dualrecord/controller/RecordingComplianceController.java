package com.minimax.dualrecord.controller;

import com.minimax.dualrecord.domain.Recording;
import com.minimax.dualrecord.domain.UploadSession;
import com.minimax.dualrecord.domain.RecordingAccessLog;
import com.minimax.dualrecord.domain.RecordingAnnotation;
import com.minimax.dualrecord.domain.PreservationRecord;
import com.minimax.dualrecord.repository.RecordingRepository;
import com.minimax.dualrecord.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 录像合规控制器 (v1.2)
 *
 * 8 大类端点:
 *  - 32 项合规检查: POST /api/v1/recording-compliance/check
 *  - 事件标注: POST /api/v1/recording-compliance/annotate
 *  - 回放 token: POST /api/v1/recording-compliance/playback-token
 *  - 访问日志: GET /api/v1/recording-compliance/access-log/{recId}
 *  - 断点续传: POST /api/v1/recording-compliance/upload/init
 *  - 证据保全: POST /api/v1/recording-compliance/preservation/submit
 *  - 留存归档: POST /api/v1/recording-compliance/retention/archive
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/recording-compliance")
@RequiredArgsConstructor
@Tag(name = "录像合规", description = "v1.2 录像合规增强: 32 项检查 / 事件标注 / 回放 / 续传 / 证据保全 / 留存")
public class RecordingComplianceController {

    private final RecordingComplianceService complianceService;
    private final RecordingAnnotationService annotationService;
    private final RecordingPlaybackService playbackService;
    private final RecordingResumableService resumableService;
    private final EvidencePreservationService preservationService;
    private final RetentionScheduler retentionScheduler;
    private final RecordingRepository recordingRepository;

    // ===================== 32 项合规检查 =====================
    @PostMapping("/check")
    @Operation(summary = "跑 32 项录像合规检查")
    public ResponseEntity<RecordingComplianceService.ComplianceReport> check(
            @RequestParam String recId) {
        Recording rec = recordingRepository.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Recording>()
                        .eq("rec_id", recId));
        if (rec == null) return ResponseEntity.notFound().build();
        var business = new com.minimax.dualrecord.domain.Business();
        business.setBusinessId(rec.getBusinessId());
        return ResponseEntity.ok(complianceService.check(rec, business));
    }

    @GetMapping("/checklist")
    @Operation(summary = "列出 32 项检查清单定义")
    public ResponseEntity<List<RecordingComplianceService.CheckDefinition>> checklist() {
        return ResponseEntity.ok(RecordingComplianceService.CHECK_DEFINITIONS);
    }

    // ===================== 事件标注 =====================
    @PostMapping("/annotate")
    @Operation(summary = "添加录像事件标注 (8 节点/风险揭示/客户肯定 等)")
    public ResponseEntity<RecordingAnnotation> annotate(
            @RequestParam String recId,
            @RequestParam String businessId,
            @RequestParam String type,
            @RequestParam(required = false) String nodeId,
            @RequestParam long timestampMs,
            @RequestParam(required = false) String note,
            @RequestParam(required = false) String operatorId) {
        return ResponseEntity.ok(annotationService.annotate(recId, businessId, type,
                nodeId, timestampMs, note, operatorId));
    }

    @GetMapping("/annotations/{recId}")
    @Operation(summary = "列出某录像的所有标注 (按时间排序)")
    public ResponseEntity<List<RecordingAnnotation>> listAnnotations(@PathVariable String recId) {
        return ResponseEntity.ok(annotationService.listByRecId(recId));
    }

    // ===================== 回放 + DRM =====================
    @PostMapping("/playback-token")
    @Operation(summary = "签发回放 token (5 分钟有效, 不可下载传播)")
    public ResponseEntity<RecordingPlaybackService.PlaybackToken> playbackToken(
            @RequestParam String recId,
            @RequestParam String userId,
            @RequestParam String userRole,
            @RequestParam(required = false, defaultValue = "0") int ttlSec) {
        return ResponseEntity.ok(playbackService.issueToken(recId, userId, userRole, ttlSec));
    }

    @GetMapping("/access-log/{recId}")
    @Operation(summary = "列出某录像的所有访问日志 (监管/审计)")
    public ResponseEntity<List<RecordingAccessLog>> accessLog(@PathVariable String recId) {
        return ResponseEntity.ok(playbackService.listAccessLog(recId));
    }

    // ===================== 断点续传 =====================
    @PostMapping("/upload/init")
    @Operation(summary = "初始化录像分片上传 session")
    public ResponseEntity<UploadSession> uploadInit(
            @RequestParam String businessId,
            @RequestParam String channel,
            @RequestParam int totalChunks,
            @RequestParam long totalSizeBytes,
            @RequestParam(required = false) Integer chunkSize) {
        return ResponseEntity.ok(resumableService.initSession(businessId, channel,
                totalChunks, totalSizeBytes, chunkSize));
    }

    @PostMapping("/upload/chunk")
    @Operation(summary = "上传 1 个分片")
    public ResponseEntity<RecordingResumableService.ChunkUploadResult> uploadChunk(
            @RequestParam String sessionId,
            @RequestParam int chunkIndex,
            @RequestParam(required = false) byte[] data) {
        return ResponseEntity.ok(resumableService.uploadChunk(sessionId, chunkIndex, data));
    }

    @GetMapping("/upload/status/{sessionId}")
    @Operation(summary = "查询上传进度")
    public ResponseEntity<UploadSession> uploadStatus(@PathVariable String sessionId) {
        return ResponseEntity.ok(resumableService.queryStatus(sessionId));
    }

    @PostMapping("/upload/finalize/{sessionId}")
    @Operation(summary = "完成上传 → 转 tb_recording")
    public ResponseEntity<Map<String, String>> uploadFinalize(@PathVariable String sessionId) {
        String recId = resumableService.finalizeUpload(sessionId);
        return ResponseEntity.ok(Map.of("recId", recId, "sessionId", sessionId));
    }

    // ===================== 证据保全 =====================
    @PostMapping("/preservation/submit")
    @Operation(summary = "提交证据保全申请 (司法/监管/审计/客户)")
    public ResponseEntity<PreservationRecord> preservationSubmit(
            @RequestParam String recId,
            @RequestParam String requesterId,
            @RequestParam String requesterRole,
            @RequestParam String reason) {
        return ResponseEntity.ok(preservationService.submit(recId, requesterId, requesterRole, reason));
    }

    @PostMapping("/preservation/notarize/{preservationId}")
    @Operation(summary = "公证处介入")
    public ResponseEntity<PreservationRecord> preservationNotarize(
            @PathVariable String preservationId,
            @RequestParam String notaryOrg,
            @RequestParam String notaryCertNo) {
        return ResponseEntity.ok(preservationService.notarize(preservationId, notaryOrg, notaryCertNo));
    }

    @GetMapping("/preservation/verify/{preservationId}")
    @Operation(summary = "验证保全完整性")
    public ResponseEntity<EvidencePreservationService.VerificationResult> preservationVerify(
            @PathVariable String preservationId) {
        return ResponseEntity.ok(preservationService.verify(preservationId));
    }

    @GetMapping("/preservation/list/{recId}")
    @Operation(summary = "列出一笔录像的所有保全记录")
    public ResponseEntity<List<PreservationRecord>> preservationList(@PathVariable String recId) {
        return ResponseEntity.ok(preservationService.listByRecId(recId));
    }

    // ===================== 留存 =====================
    @PostMapping("/retention/scan")
    @Operation(summary = "手动触发留存扫描 (运维)")
    public ResponseEntity<Map<String, String>> retentionScan() {
        retentionScheduler.scanExpiringRecordings();
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @PostMapping("/retention/archive")
    @Operation(summary = "归档到冷存储 (recEnd < ?)")
    public ResponseEntity<Map<String, Object>> retentionArchive(
            @RequestParam String beforeDate) {
        int n = retentionScheduler.archiveToColdStorage(java.time.LocalDate.parse(beforeDate));
        return ResponseEntity.ok(Map.of("archivedCount", n, "beforeDate", beforeDate));
    }
}
