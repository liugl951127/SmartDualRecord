package com.minimax.dualrecord.controller;

import com.minimax.dualrecord.domain.PushedFile;
import com.minimax.dualrecord.dto.PushFileRequest;
import com.minimax.dualrecord.dto.SignFileRequest;
import com.minimax.dualrecord.service.FilePushService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 坐席推送文件 API (v1.5)
 *
 * 端点:
 *  - POST /api/v1/file/push           坐席推送文件给客户
 *  - GET  /api/v1/file/list/{bid}     查询业务所有推送文件
 *  - GET  /api/v1/file/{fileId}       查询单个文件
 *  - POST /api/v1/file/{fileId}/view  客户标记查看
 *  - POST /api/v1/file/{fileId}/sign  客户签署/拒签
 *  - GET  /api/v1/file/templates      内置文件模板库
 */
@RestController
@RequestMapping("/api/v1/file")
@RequiredArgsConstructor
@Tag(name = "坐席推送文件", description = "线上双录中坐席向客户推送合同/资料")
public class FilePushController {

    private final FilePushService filePushService;

    @PostMapping("/push")
    @Operation(summary = "坐席推送文件给客户")
    public ResponseEntity<PushedFile> pushFile(
            @RequestBody PushFileRequest req,
            @RequestParam String operatorId) {
        return ResponseEntity.ok(filePushService.pushFile(req, operatorId));
    }

    @GetMapping("/list/{businessId}")
    @Operation(summary = "查询业务所有推送文件")
    public ResponseEntity<List<PushedFile>> listByBusiness(@PathVariable String businessId) {
        return ResponseEntity.ok(filePushService.listByBusiness(businessId));
    }

    @GetMapping("/{fileId}")
    @Operation(summary = "查询单个推送文件")
    public ResponseEntity<PushedFile> getByFileId(@PathVariable String fileId) {
        return ResponseEntity.ok(filePushService.getByFileId(fileId));
    }

    @PostMapping("/{fileId}/view")
    @Operation(summary = "客户标记已查看")
    public ResponseEntity<PushedFile> markViewed(@PathVariable String fileId) {
        return ResponseEntity.ok(filePushService.markViewed(fileId));
    }

    @PostMapping("/{fileId}/sign")
    @Operation(summary = "客户签署文件 (rejected=true 为拒签)")
    public ResponseEntity<PushedFile> signFile(
            @PathVariable String fileId,
            @RequestBody SignFileRequest req) {
        return ResponseEntity.ok(filePushService.signFile(fileId, req));
    }

    @GetMapping("/templates")
    @Operation(summary = "内置文件模板库 (产品说明书/风险揭示书/合同)")
    public ResponseEntity<List<Map<String, Object>>> templates() {
        // 内置模板 (实际生产中应存 DB)
        return ResponseEntity.ok(List.of(
                Map.of(
                        "id", "TPL-PRODUCT-001",
                        "name", "稳健型封闭式理财-产品说明书.pdf",
                        "type", "PDF",
                        "category", "BROCHURE",
                        "size", 1024 * 1024 * 2L,
                        "url", "/static/templates/product-brochure.pdf",
                        "icon", "📄"
                ),
                Map.of(
                        "id", "TPL-DISCLOSURE-001",
                        "name", "风险揭示书.pdf",
                        "type", "PDF",
                        "category", "DISCLOSURE",
                        "size", 512 * 1024L,
                        "url", "/static/templates/risk-disclosure.pdf",
                        "icon", "⚠️"
                ),
                Map.of(
                        "id", "TPL-CONTRACT-001",
                        "name", "理财合同-标准版.pdf",
                        "type", "PDF",
                        "category", "CONTRACT",
                        "size", 1024 * 1024 * 3L,
                        "url", "/static/templates/contract-standard.pdf",
                        "icon", "📜"
                ),
                Map.of(
                        "id", "TPL-CONTRACT-002",
                        "name", "电子签名授权书.pdf",
                        "type", "PDF",
                        "category", "CONTRACT",
                        "size", 256 * 1024L,
                        "url", "/static/templates/signature-authorization.pdf",
                        "icon", "✍️"
                ),
                Map.of(
                        "id", "TPL-IMAGE-001",
                        "name", "产品收益走势图.png",
                        "type", "PNG",
                        "category", "BROCHURE",
                        "size", 800 * 1024L,
                        "url", "/static/templates/return-chart.png",
                        "icon", "📊"
                )
        ));
    }
}
