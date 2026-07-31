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
}
