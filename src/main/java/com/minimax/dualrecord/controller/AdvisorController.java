package com.minimax.dualrecord.controller;

import com.minimax.dualrecord.domain.AdvisorSession;
import com.minimax.dualrecord.dto.TransferToAdvisorRequest;
import com.minimax.dualrecord.service.AdvisorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 理财经理转接 API (v1.5 H5 → PC)
 *
 * H5 客户流程:
 *   GET  /api/v1/advisor/list                  - 查看可选理财经理
 *   POST /api/v1/advisor/request               - 请求转接
 *   GET  /api/v1/advisor/active/{businessId}   - 查当前活跃会话
 *   POST /api/v1/advisor/{sessionId}/end       - 客户主动结束
 *
 * PC 理财经理流程:
 *   GET  /api/v1/advisor/pending/{advisorId}   - 待处理请求
 *   GET  /api/v1/advisor/active/{advisorId}    - 自己的活跃会话
 *   POST /api/v1/advisor/{sessionId}/accept    - 接单
 *   POST /api/v1/advisor/{sessionId}/decline   - 拒绝
 */
@RestController
@RequestMapping("/api/v1/advisor")
@RequiredArgsConstructor
@Tag(name = "理财经理转接", description = "H5 客户转接到 PC 理财经理")
public class AdvisorController {

    private final AdvisorService advisorService;

    @GetMapping("/list")
    @Operation(summary = "查看可选理财经理 (H5 客户端)")
    public ResponseEntity<List<Map<String, Object>>> listAdvisors() {
        return ResponseEntity.ok(advisorService.listAvailableAdvisors());
    }

    @PostMapping("/request")
    @Operation(summary = "H5 客户请求转接理财经理")
    public ResponseEntity<AdvisorSession> requestTransfer(@RequestBody TransferToAdvisorRequest req) {
        return ResponseEntity.ok(advisorService.requestTransfer(req));
    }

    @GetMapping("/active/{businessId}")
    @Operation(summary = "查业务的活跃会话 (H5 客户)")
    public ResponseEntity<AdvisorSession> getActiveByBusiness(@PathVariable String businessId) {
        AdvisorSession s = advisorService.getActiveByBusiness(businessId);
        if (s == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(s);
    }

    @PostMapping("/{sessionId}/end")
    @Operation(summary = "结束会话 (任一方)")
    public ResponseEntity<AdvisorSession> endSession(
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "CUSTOMER_LEFT") String endReason) {
        return ResponseEntity.ok(advisorService.endSession(sessionId, endReason));
    }

    @GetMapping("/pending/{advisorId}")
    @Operation(summary = "PC 端: 待处理请求")
    public ResponseEntity<List<AdvisorSession>> listPending(@PathVariable String advisorId) {
        return ResponseEntity.ok(advisorService.listPending(advisorId));
    }

    @GetMapping("/advisor/active/{advisorId}")
    @Operation(summary = "PC 端: 自己的活跃会话")
    public ResponseEntity<List<AdvisorSession>> listActive(@PathVariable String advisorId) {
        return ResponseEntity.ok(advisorService.listActive(advisorId));
    }

    @PostMapping("/{sessionId}/accept")
    @Operation(summary = "PC 端: 接单")
    public ResponseEntity<AdvisorSession> accept(
            @PathVariable String sessionId,
            @RequestParam String advisorId,
            @RequestParam String advisorName) {
        return ResponseEntity.ok(advisorService.acceptTransfer(sessionId, advisorId, advisorName));
    }

    @PostMapping("/{sessionId}/decline")
    @Operation(summary = "PC 端: 拒绝")
    public ResponseEntity<AdvisorSession> decline(
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "忙") String reason) {
        return ResponseEntity.ok(advisorService.declineTransfer(sessionId, reason));
    }
}
