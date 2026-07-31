package com.minimax.dualrecord.controller;

import com.minimax.dualrecord.domain.RiskAssessment;
import com.minimax.dualrecord.service.RiskAssessmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 风险评估 API
 */
@RestController
@RequestMapping("/api/v1/risk")
@RequiredArgsConstructor
@Tag(name = "风险评估", description = "客户风险承受能力评估 + 适当性匹配")
public class RiskAssessmentController {

    private final RiskAssessmentService service;

    @GetMapping("/latest/{customerIdHash}")
    @Operation(summary = "获取客户最新有效风险评估")
    public ResponseEntity<RiskAssessment> latest(@PathVariable String customerIdHash) {
        return ResponseEntity.ok(service.getLatestValid(customerIdHash));
    }

    @PostMapping("/submit")
    @Operation(summary = "提交风险评估问卷")
    public ResponseEntity<RiskAssessment> submit(@RequestParam String customerIdHash,
                                                   @RequestBody Map<String, Object> answers) {
        return ResponseEntity.ok(service.submit(customerIdHash, answers));
    }

    @PostMapping("/match")
    @Operation(summary = "适当性匹配（客户 vs 产品）")
    public ResponseEntity<?> match(@RequestParam String customerLevel,
                                     @RequestParam String productLevel) {
        return ResponseEntity.ok(service.match(customerLevel, productLevel));
    }
}
