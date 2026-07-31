package com.minimax.dualrecord.controller;

import com.minimax.dualrecord.domain.enums.RecordingState;
import com.minimax.dualrecord.statemachine.RecordingStateMachine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 健康检查 + 系统信息
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "系统", description = "健康检查 + 状态机查询")
public class HealthController {

    @GetMapping("/health")
    @Operation(summary = "服务健康检查")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> h = new HashMap<>();
        h.put("status", "UP");
        h.put("service", "dual-record-llm-service");
        h.put("version", "1.0.0");
        h.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(h);
    }

    @GetMapping("/statemachine/transitions")
    @Operation(summary = "查询所有合法状态转移")
    public ResponseEntity<Map<String, Set<RecordingState>>> transitions() {
        Map<String, Set<RecordingState>> result = new HashMap<>();
        for (RecordingState state : RecordingState.values()) {
            result.put(state.name(), RecordingStateMachine.nextStates(state));
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/statemachine/can-transition")
    @Operation(summary = "检查两个状态之间是否合法")
    public ResponseEntity<Boolean> canTransition(@RequestParam String from, @RequestParam String to) {
        return ResponseEntity.ok(RecordingStateMachine.canTransition(
                RecordingState.valueOf(from), RecordingState.valueOf(to)));
    }
}
