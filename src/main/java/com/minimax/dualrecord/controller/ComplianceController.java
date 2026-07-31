package com.minimax.dualrecord.controller;

import com.minimax.dualrecord.service.ComplianceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 合规引擎 API
 */
@RestController
@RequestMapping("/api/v1/compliance")
@RequiredArgsConstructor
@Tag(name = "合规引擎", description = "禁播词扫描 + 合规规则查询")
public class ComplianceController {

    private final ComplianceService service;

    @PostMapping("/scan")
    @Operation(summary = "扫描一段文字中的禁播词")
    public ResponseEntity<List<ComplianceService.Hit>> scan(@RequestParam String text) {
        return ResponseEntity.ok(service.scan(text));
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新禁播词缓存")
    public ResponseEntity<Void> refresh() {
        service.refreshCache();
        return ResponseEntity.noContent().build();
    }
}
