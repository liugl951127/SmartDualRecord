package com.minimax.dualrecord.controller;

import com.minimax.dualrecord.domain.AuditChainEntry;
import com.minimax.dualrecord.service.AuditChainService;
import com.minimax.dualrecord.service.DataLineageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 数据防篡改 + 血缘追溯
 */
@RestController
@RequestMapping("/api/v1/integrity")
@RequiredArgsConstructor
@Tag(name = "数据完整性", description = "审计链验证 + 数据血缘追溯")
public class TamperProofController {

    private final AuditChainService auditChainService;
    private final DataLineageService dataLineageService;

    // ============ 审计链 ============

    @GetMapping("/chains")
    @Operation(summary = "列出所有审计链")
    public ResponseEntity<List<String>> listChains() {
        return ResponseEntity.ok(auditChainService.listBusinessChains());
    }

    @GetMapping("/chain/{chainId}")
    @Operation(summary = "获取一条审计链的所有条目")
    public ResponseEntity<List<AuditChainEntry>> getChain(@PathVariable String chainId) {
        return ResponseEntity.ok(auditChainService.listChain(chainId));
    }

    @GetMapping("/chain/{chainId}/debug")
    @Operation(summary = "调试: 重新计算每条的 chain_hash 看是否匹配")
    public ResponseEntity<List<Map<String, Object>>> debugChain(@PathVariable String chainId) {
        return ResponseEntity.ok(auditChainService.debugChain(chainId));
    }

    @GetMapping("/chain/{chainId}/verify")
    @Operation(summary = "验证一条审计链的完整性")
    public ResponseEntity<Map<String, Object>> verifyChain(@PathVariable String chainId) {
        return ResponseEntity.ok(auditChainService.verifyChain(chainId));
    }

    @GetMapping("/business/{businessId}/entries")
    @Operation(summary = "列出某业务相关的所有审计条目")
    public ResponseEntity<List<AuditChainEntry>> getBusinessEntries(
        @PathVariable String businessId,
        @RequestParam(defaultValue = "100") int limit
    ) {
        return ResponseEntity.ok(auditChainService.listForBusiness(businessId, limit));
    }

    @GetMapping("/entity/{entityType}/{entityId}/entries")
    @Operation(summary = "列出某实体的所有审计条目")
    public ResponseEntity<List<AuditChainEntry>> getEntityEntries(
        @PathVariable String entityType,
        @PathVariable String entityId
    ) {
        return ResponseEntity.ok(auditChainService.listForEntity(entityType, entityId));
    }

    // ============ 数据血缘 ============

    @GetMapping("/lineage/business/{businessId}")
    @Operation(summary = "获取业务全链路血缘图 (DAG)")
    public ResponseEntity<Map<String, Object>> getBusinessLineage(@PathVariable String businessId) {
        return ResponseEntity.ok(dataLineageService.getBusinessLineage(businessId));
    }

    @GetMapping("/lineage/stats")
    @Operation(summary = "血缘统计")
    public ResponseEntity<Map<String, Object>> stats() {
        return ResponseEntity.ok(dataLineageService.stats());
    }

    @GetMapping("/lineage/find/{entityType}/{entityId}")
    @Operation(summary = "反向追溯: 哪些业务使用了此 entity")
    public ResponseEntity<List<String>> findUsing(
        @PathVariable String entityType,
        @PathVariable String entityId
    ) {
        return ResponseEntity.ok(dataLineageService.findBusinessesUsing(entityType, entityId));
    }

    // ============ 演示用 - append 入口 (供手动测试) ============

    @PostMapping("/demo/append")
    @Operation(summary = "演示用: 手动 append 一条审计 (用于测试 hash chain)")
    public ResponseEntity<Map<String, Object>> demoAppend(@RequestBody Map<String, Object> body) {
        String chainId = (String) body.getOrDefault("chainId", "demo-" + System.currentTimeMillis());
        String entityType = (String) body.getOrDefault("entityType", "demo");
        String entityId = (String) body.getOrDefault("entityId", "demo-1");
        Map<String, Object> payload = new HashMap<>(body);
        payload.remove("chainId");
        payload.remove("entityType");
        payload.remove("entityId");

        String hash = auditChainService.append(
            chainId,
            "CREATE",
            entityType,
            entityId,
            (String) body.get("businessId"),
            (String) body.getOrDefault("actorId", "demo"),
            (String) body.getOrDefault("actorRole", "DEMO"),
            payload
        );
        return ResponseEntity.ok(Map.of(
            "status", "appended",
            "chainId", chainId,
            "entityType", entityType,
            "entityId", entityId,
            "chainHash", hash
        ));
    }
}
