package com.minimax.dualrecord.controller;

import com.minimax.dualrecord.domain.SagaEvent;
import com.minimax.dualrecord.domain.SagaInstance;
import com.minimax.dualrecord.domain.SagaStep;
import com.minimax.dualrecord.saga.SagaDemoService;
import com.minimax.dualrecord.saga.SagaFaultInjector;
import com.minimax.dualrecord.saga.SagaOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Saga 分布式事务管理
 */
@RestController
@RequestMapping("/api/v1/saga")
@RequiredArgsConstructor
@Tag(name = "Saga 事务", description = "分布式事务原子性 + 自动补偿")
public class SagaController {

    private final SagaOrchestrator orchestrator;
    private final SagaDemoService demoService;
    private final SagaFaultInjector faultInjector;

    // ============ Saga 执行 ============

    @PostMapping("/demo/run")
    @Operation(summary = "演示: 跑一遍 createBusinessDemo Saga (可注入故障)")
    public ResponseEntity<Map<String, Object>> runDemo(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> params = body != null ? body : new HashMap<>();
        if (!params.containsKey("amount")) params.put("amount", "50000");
        if (!params.containsKey("customerIdHash")) params.put("customerIdHash", "cust-hash-001");
        if (!params.containsKey("productId")) params.put("productId", "BNK-FIN-2026Q3-001");

        long start = System.currentTimeMillis();
        Map<String, Object> result = new HashMap<>();
        result.put("status", "RUNNING");
        result.put("params", params);

        try {
            Object sagaResult = demoService.createBusiness(params);
            result.put("status", "COMPLETED");
            result.put("context", sagaResult);
            result.put("durationMs", System.currentTimeMillis() - start);
        } catch (Exception e) {
            result.put("status", "FAILED");
            result.put("error", e.getMessage());
            result.put("durationMs", System.currentTimeMillis() - start);
        }
        return ResponseEntity.ok(result);
    }

    // ============ 故障注入 ============

    @PostMapping("/fault/inject")
    @Operation(summary = "故障注入: 让指定步骤失败 N 次")
    public ResponseEntity<Map<String, Object>> injectFault(@RequestBody Map<String, Object> body) {
        String step = (String) body.get("step");
        int count = body.get("count") != null ? ((Number) body.get("count")).intValue() : 1;
        faultInjector.injectFault(step, count);
        return ResponseEntity.ok(Map.of(
            "step", step,
            "count", count,
            "activeFaults", faultInjector.getStatus()
        ));
    }

    @PostMapping("/fault/global/{enabled}")
    @Operation(summary = "全局故障开关 (true=失败所有步骤)")
    public ResponseEntity<Map<String, Object>> setGlobalFault(@PathVariable boolean enabled) {
        faultInjector.setGlobalEnabled(enabled);
        return ResponseEntity.ok(Map.of("globalEnabled", enabled));
    }

    @PostMapping("/fault/clear")
    @Operation(summary = "清除所有故障注入")
    public ResponseEntity<Map<String, Object>> clearFaults() {
        faultInjector.clear();
        return ResponseEntity.ok(Map.of("cleared", true));
    }

    @GetMapping("/fault/status")
    @Operation(summary = "查看当前故障状态")
    public ResponseEntity<Map<String, Object>> faultStatus() {
        return ResponseEntity.ok(Map.of(
            "globalEnabled", faultInjector.isAnyFaultActive(),
            "stepFaults", faultInjector.getStatus()
        ));
    }

    // ============ Saga 查询 ============

    @GetMapping("/instances")
    @Operation(summary = "列出最近的 Saga 实例")
    public ResponseEntity<List<SagaInstance>> listInstances(@RequestParam(defaultValue = "30") int limit) {
        return ResponseEntity.ok(orchestrator.listRecent(limit));
    }

    @GetMapping("/instances/by-status/{status}")
    @Operation(summary = "按状态查询")
    public ResponseEntity<List<SagaInstance>> listByStatus(
        @PathVariable String status,
        @RequestParam(defaultValue = "30") int limit
    ) {
        return ResponseEntity.ok(orchestrator.listByStatus(status, limit));
    }

    @GetMapping("/instance/{sagaId}")
    @Operation(summary = "Saga 实例详情")
    public ResponseEntity<Map<String, Object>> getInstance(@PathVariable String sagaId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("instance", orchestrator.getInstance(sagaId));
        result.put("steps", orchestrator.getSteps(sagaId));
        result.put("events", orchestrator.getEvents(sagaId));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/instance/{sagaId}/steps")
    @Operation(summary = "Saga 步骤明细")
    public ResponseEntity<List<SagaStep>> getSteps(@PathVariable String sagaId) {
        return ResponseEntity.ok(orchestrator.getSteps(sagaId));
    }

    @GetMapping("/instance/{sagaId}/events")
    @Operation(summary = "Saga 事件日志")
    public ResponseEntity<List<SagaEvent>> getEvents(@PathVariable String sagaId) {
        return ResponseEntity.ok(orchestrator.getEvents(sagaId));
    }

    @GetMapping("/by-business/{businessId}")
    @Operation(summary = "按业务 ID 查 Saga")
    public ResponseEntity<List<SagaInstance>> byBusiness(@PathVariable String businessId) {
        return ResponseEntity.ok(orchestrator.listByBusiness(businessId));
    }
}
