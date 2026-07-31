package com.minimax.dualrecord.controller;

import com.minimax.dualrecord.domain.QaResult;
import com.minimax.dualrecord.service.QaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 质检 API
 */
@RestController
@RequestMapping("/api/v1/qa")
@RequiredArgsConstructor
@Tag(name = "质检", description = "AI 预筛 + 人工复核入口")
public class QaController {

    private final QaService service;

    @PostMapping("/pre-screen")
    @Operation(summary = "AI 预筛一段音视频（已转写）")
    public ResponseEntity<QaResult> preScreen(@RequestParam String businessId,
                                                @RequestParam String recId,
                                                @RequestParam String asrText) {
        return ResponseEntity.ok(service.aiPreScreen(businessId, recId, asrText));
    }
}
