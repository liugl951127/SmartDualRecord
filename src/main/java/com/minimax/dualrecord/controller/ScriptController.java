package com.minimax.dualrecord.controller;

import com.minimax.dualrecord.service.ScriptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 话术管理 API
 */
@RestController
@RequestMapping("/api/v1/script")
@RequiredArgsConstructor
@Tag(name = "话术管理", description = "话术模板查询、跨渠道一致性巡检")
public class ScriptController {

    private final ScriptService scriptService;

    @GetMapping("/{productId}")
    @Operation(summary = "获取单个产品的完整话术")
    public ResponseEntity<Map<String, Object>> getScript(@org.springframework.web.bind.annotation.PathVariable String productId) {
        return ResponseEntity.ok(scriptService.getScript(productId));
    }

    @GetMapping("/all")
    @Operation(summary = "获取所有话术")
    public ResponseEntity<Map<String, Map<String, Object>>> all() {
        return ResponseEntity.ok(scriptService.getAllScripts());
    }

    @GetMapping("/consistency")
    @Operation(summary = "跨渠道 hash 一致性巡检")
    public ResponseEntity<List<String>> consistencyCheck() {
        return ResponseEntity.ok(scriptService.consistencyCheck());
    }

    @GetMapping("/frozen/{productId}")
    @Operation(summary = "检查话术是否处于 FROZEN 状态")
    public ResponseEntity<Boolean> isFrozen(@org.springframework.web.bind.annotation.PathVariable String productId) {
        return ResponseEntity.ok(scriptService.isFrozen(productId));
    }
}
