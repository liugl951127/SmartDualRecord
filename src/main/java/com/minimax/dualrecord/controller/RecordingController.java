package com.minimax.dualrecord.controller;

import com.minimax.dualrecord.domain.Business;
import com.minimax.dualrecord.domain.QaResult;
import com.minimax.dualrecord.dto.CompleteNodeRequest;
import com.minimax.dualrecord.dto.StartBusinessRequest;
import com.minimax.dualrecord.domain.enums.RecordingNode;
import com.minimax.dualrecord.service.RecordingService;
import com.minimax.dualrecord.service.ScriptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 录制主流程控制器
 *
 * 提供双录业务的全生命周期 REST API：
 *  - 创建业务 → 加载话术 → 风险评估 → 启动录制
 *  - 8 节点逐个完成 → 终检 → 签字 → 归档
 *  - 全景查询
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/recording")
@RequiredArgsConstructor
@Tag(name = "录制主流程", description = "双录业务全生命周期 API")
public class RecordingController {

    private final RecordingService recordingService;
    private final ScriptService scriptService;
    private final com.minimax.dualrecord.service.FollowUpService followUpService;

    @PostMapping("/start")
    @Operation(summary = "创建业务并启动双录")
    public ResponseEntity<Business> startBusiness(@RequestBody @Valid StartBusinessRequest request) {
        Business business = recordingService.startBusiness(
                request.getBusinessType(),
                request.getProductId(),
                request.getCustomerIdHash(),
                request.getSellerIdHash(),
                request.getChannel(),
                request.getSellerType(),
                request.getAmount()
        );
        return ResponseEntity.ok(business);
    }

    @PostMapping("/script/load")
    @Operation(summary = "加载话术（按产品 ID）")
    public ResponseEntity<Map<String, Object>> loadScript(@RequestParam String businessId,
                                                          @RequestParam String productId) {
        return ResponseEntity.ok(recordingService.loadScript(businessId, productId));
    }

    @PostMapping("/risk/assess")
    @Operation(summary = "风险评估 + 适当性匹配")
    public ResponseEntity<?> assessRisk(@RequestParam String businessId,
                                          @RequestParam String customerIdHash) {
        var matchResult = recordingService.assessRisk(businessId, customerIdHash);
        return ResponseEntity.ok(Map.of(
                "businessId", businessId,
                "matchResult", matchResult
        ));
    }

    @PostMapping("/begin")
    @Operation(summary = "启动录制（进入 8 节点状态机）")
    public ResponseEntity<Void> startRecording(@RequestParam String businessId) {
        recordingService.startRecording(businessId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/node/complete")
    @Operation(summary = "完成一个 8 节点中的 1 个")
    public ResponseEntity<?> completeNode(@RequestBody @Valid CompleteNodeRequest request) {
        var result = recordingService.completeNode(
                request.getBusinessId(),
                request.getRecId(),
                request.getNode(),
                request.getAsrText()
        );
        return ResponseEntity.ok(result);
    }

    @PostMapping("/finalize")
    @Operation(summary = "完成所有节点 → AI 终检")
    public ResponseEntity<QaResult> finalize(@RequestParam String businessId,
                                              @RequestParam String recId,
                                              @RequestParam String fullAsrText) {
        return ResponseEntity.ok(recordingService.finalQa(businessId, recId, fullAsrText));
    }

    @PostMapping("/sign")
    @Operation(summary = "客户签字 → 归档")
    public ResponseEntity<Void> signAndArchive(@RequestParam String businessId) {
        recordingService.signAndArchive(businessId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/human-review")
    @Operation(summary = "人工复核完成（HUMAN_REVIEW → HUMAN_REVIEWED）")
    public ResponseEntity<Void> humanReview(@RequestParam String businessId,
                                             @RequestParam String reviewStatus,
                                             @RequestParam String reviewerId) {
        recordingService.humanReview(businessId, reviewStatus, reviewerId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/manual-fail")
    @Operation(summary = "运维介入：手动标 FAILED（替代 Saga 模式的回滚机制）")
    public ResponseEntity<Void> manualFail(@RequestParam String businessId,
                                            @RequestParam String reason,
                                            @RequestParam String operatorId) {
        recordingService.manualFail(businessId, reason, operatorId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/overview/{businessId}")
    @Operation(summary = "查询业务全景")
    public ResponseEntity<Map<String, Object>> overview(@PathVariable String businessId) {
        return ResponseEntity.ok(recordingService.getBusinessOverview(businessId));
    }

    @GetMapping("/nodes")
    @Operation(summary = "查询所有 8 节点定义")
    public ResponseEntity<?> allNodes() {
        return ResponseEntity.ok(RecordingNode.orderedAll());
    }

    // ====================================================================
    // 紧急 / 跨段 / 审计
    // ====================================================================
    @PostMapping("/emergency-stop")
    @Operation(summary = "紧急停用 AI（合规紧急开关，15 分钟内生效）")
    public ResponseEntity<Void> emergencyStopAI(@RequestParam String operatorId,
                                                  @RequestParam String reason) {
        recordingService.emergencyStopAI(operatorId, reason);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/link-recording")
    @Operation(summary = "关联跨段录像（线上线下融合业务）")
    public ResponseEntity<Void> linkRecordings(@RequestParam String primaryRecId,
                                                @RequestParam String linkedRecId,
                                                @RequestParam String operatorId) {
        recordingService.linkRecordings(primaryRecId, linkedRecId, operatorId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/audit-review/{businessId}")
    @Operation(summary = "审计回看（监管 / 风控 / 内部审计, 自动脱敏）")
    public ResponseEntity<Map<String, Object>> auditReview(@PathVariable String businessId,
                                                             @RequestParam String auditorId) {
        return ResponseEntity.ok(recordingService.auditReview(businessId, auditorId));
    }

    @PostMapping("/realtime-coaching")
    @Operation(summary = "实时耳返副驾（0.5 秒内推送 3 选 1 话术）")
    public ResponseEntity<Map<String, Object>> realTimeCoaching(@RequestParam String businessId,
                                                                  @RequestParam String asrSegment) {
        return ResponseEntity.ok(recordingService.getRealTimeCoaching(businessId, asrSegment));
    }

    @PostMapping("/followup/wants-to-cancel")
    @Operation(summary = "客户回复\"想退保\"→ 转人工 + 标记需干预")
    public ResponseEntity<Void> customerReplyWantsToCancel(@RequestParam String businessId,
                                                            @RequestParam String replyContent) {
        followUpService.customerReplyWantsToCancel(businessId, replyContent);
        return ResponseEntity.noContent().build();
    }

    // ====================================================================
    // v1.5 跨渠道补录 (Offline Failed → Online Resume)
    // ====================================================================

    @PostMapping("/offline-failed")
    @Operation(summary = "线下双录某节点未通过 → 标记 OFFLINE_FAILED + 生成补录 token")
    public ResponseEntity<Map<String, Object>> markOfflineFailed(
            @RequestParam String businessId,
            @RequestParam String failedNode,
            @RequestParam String reason,
            @RequestParam(required = false) String detail) {
        String token = recordingService.markOfflineFailedAndIssueResumeToken(
                businessId, failedNode, reason, detail);
        return ResponseEntity.ok(Map.of(
                "businessId", businessId,
                "failedAtNode", failedNode,
                "reason", reason,
                "resumeToken", token,
                "resumeUrl", "/client-portal?token=" + token
        ));
    }

    @GetMapping("/resume-info/{token}")
    @Operation(summary = "根据 token 查询补录信息 (客户扫码调用)")
    public ResponseEntity<Map<String, Object>> getResumeInfo(@PathVariable String token) {
        return ResponseEntity.ok(recordingService.getResumeInfoByToken(token));
    }

    @PostMapping("/resume-complete")
    @Operation(summary = "线上补录完成 (客户在 ClientPortal 完成所有后续节点)")
    public ResponseEntity<Map<String, Object>> completeResume(
            @RequestParam String businessId,
            @RequestParam String token) {
        recordingService.completeOnlineResume(businessId, token);
        return ResponseEntity.ok(Map.of("businessId", businessId, "status", "RESUMED"));
    }
}
