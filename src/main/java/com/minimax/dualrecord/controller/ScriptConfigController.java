package com.minimax.dualrecord.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimax.dualrecord.config.ScriptProperties;
import com.minimax.dualrecord.domain.ScriptTemplate;
import com.minimax.dualrecord.dto.ScriptTemplateRequest;
import com.minimax.dualrecord.service.ScriptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 通用话术配置 API
 *
 * 提供 4 类接口：
 *  1. 查询全局默认配置
 *  2. 查询产品族默认配置
 *  3. 查询产品专属配置
 *  4. 运行时新增全局禁播词
 *
 * 整个体系的 3 层叠加逻辑都在 ScriptService.resolveScript() 里完成
 */
@RestController
@RequestMapping("/api/v1/script-config")
@RequiredArgsConstructor
@Tag(name = "通用话术配置", description = "全局默认 + 产品族默认 + 产品专属，三层叠加")
public class ScriptConfigController {

    private final ScriptService scriptService;
    private final ScriptProperties scriptProperties;

    @GetMapping("/global")
    @Operation(summary = "查询全局默认话术配置")
    public ResponseEntity<Map<String, Object>> globalConfig() {
        // 不能直接返回 ScriptProperties Bean: CGLIB 代理会带 $$beanFactory 字段
        // Jackson 序列化时炸出 "No serializer found for StandardBeanExpressionResolver"
        // 解决: 用 Map 显式拷贝字段
        Map<String, Object> dto = new java.util.LinkedHashMap<>();
        dto.put("defaultRiskLevel", scriptProperties.getDefaultRiskLevel());
        dto.put("defaultNodeTimeoutSec", scriptProperties.getDefaultNodeTimeoutSec());
        dto.put("defaultForbiddenPhrases", scriptProperties.getDefaultForbiddenPhrases());
        dto.put("defaultRequiredQuestions", scriptProperties.getDefaultRequiredQuestions());
        dto.put("defaultMandatoryPhrases", scriptProperties.getDefaultMandatoryPhrases());
        dto.put("defaultChannelOverrides", scriptProperties.getDefaultChannelOverrides());
        dto.put("productTypeRiskLevel", scriptProperties.getProductTypeRiskLevel());
        dto.put("placeholders", scriptProperties.getPlaceholders());
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/product/{productId}")
    @Operation(summary = "查询单个产品（已合并三层）的完整话术")
    public ResponseEntity<Map<String, Object>> productScript(
            @PathVariable String productId,
            @RequestParam(required = false) String productType) {
        return ResponseEntity.ok(scriptService.getScript(productId, productType));
    }

    @GetMapping("/product/{productId}/source")
    @Operation(summary = "查询产品话术的来源层级（用于诊断）")
    public ResponseEntity<Map<String, Object>> productSource(
            @PathVariable String productId,
            @RequestParam(required = false) String productType) {
        Map<String, Object> product = scriptService.getScript(productId);
        Map<String, Object> typeLevel = productType != null
                ? scriptService.getScript("type:" + productType, null)
                : null;

        Map<String, Object> source = new java.util.LinkedHashMap<>();
        source.put("product_id", productId);
        source.put("product_source", product == null ? "GLOBAL_DEFAULT" : "PRODUCT_SPECIFIC");
        source.put("type_source", typeLevel == null ? "GLOBAL_DEFAULT" : "PRODUCT_TYPE");
        source.put("has_global_default", true);
        source.put("resolution_chain", product != null
                ? "PRODUCT_SPECIFIC → PRODUCT_TYPE → GLOBAL_DEFAULT"
                : (typeLevel != null
                    ? "PRODUCT_TYPE → GLOBAL_DEFAULT"
                    : "GLOBAL_DEFAULT"));
        return ResponseEntity.ok(source);
    }

    @GetMapping("/all")
    @Operation(summary = "查询所有话术（含全局默认 + 产品族 + 产品专属）")
    public ResponseEntity<Map<String, Map<String, Object>>> all() {
        return ResponseEntity.ok(scriptService.getAllScripts());
    }

    @PostMapping("/forbidden-phrase")
    @Operation(summary = "运行时新增全局禁播词（立即生效）")
    public ResponseEntity<Map<String, Object>> addForbiddenPhrase(@RequestParam String phrase) {
        scriptService.addForbiddenPhrase(phrase);
        return ResponseEntity.ok(Map.of(
                "phrase", phrase,
                "totalCount", scriptProperties.getDefaultForbiddenPhrases().size()
        ));
    }

    @GetMapping("/forbidden-phrases")
    @Operation(summary = "查询当前全局禁播词列表")
    public ResponseEntity<List<String>> forbiddenPhrases() {
        return ResponseEntity.ok(scriptProperties.getDefaultForbiddenPhrases());
    }

    // ====================================================================
    // v1.4 产品话术 CRUD (DB 持久化) - 配置工作台
    // ====================================================================

    @GetMapping("/db-templates")
    @Operation(summary = "查询所有产品话术模板 (DB)")
    public ResponseEntity<List<Map<String, Object>>> listDbTemplates() {
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (ScriptTemplate t : scriptService.listDbTemplates()) {
            result.add(scriptService.toApiMap(t));
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/db-template/{productId}")
    @Operation(summary = "查询单个产品话术模板 (DB)")
    public ResponseEntity<Map<String, Object>> getDbTemplate(@PathVariable String productId) {
        ScriptTemplate t = scriptService.getDbTemplate(productId);
        if (t == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(scriptService.toApiMap(t));
    }

    @PostMapping("/db-template")
    @Operation(summary = "创建/更新产品话术模板 (DB)")
    public ResponseEntity<ScriptTemplate> upsertDbTemplate(@RequestBody ScriptTemplateRequest req) throws Exception {
        // DTO → Entity 转换 (List/Map → JSON String)
        ObjectMapper om = new ObjectMapper();
        ScriptTemplate template = new ScriptTemplate();
        template.setId(req.getId());
        template.setProductId(req.getProductId());
        template.setProductType(req.getProductType());
        template.setVersion(req.getVersion());
        template.setRiskLevel(req.getRiskLevel());
        template.setMandatoryDisclosure(req.getMandatoryDisclosure() == null ? "[]" : om.writeValueAsString(req.getMandatoryDisclosure()));
        template.setForbiddenPhrases(req.getForbiddenPhrases() == null ? "[]" : om.writeValueAsString(req.getForbiddenPhrases()));
        template.setRequiredQuestions(req.getRequiredQuestions() == null ? "[]" : om.writeValueAsString(req.getRequiredQuestions()));
        template.setChannelOverrides(req.getChannelOverrides() == null ? "{}" : om.writeValueAsString(req.getChannelOverrides()));
        template.setStatus(req.getStatus());
        return ResponseEntity.ok(scriptService.upsertDbTemplate(template));
    }

    @DeleteMapping("/db-template/{id}")
    @Operation(summary = "删除产品话术模板 (DB)")
    public ResponseEntity<Map<String, Object>> deleteDbTemplate(@PathVariable String id) {
        boolean ok = scriptService.deleteDbTemplate(id);
        return ResponseEntity.ok(Map.of("deleted", ok));
    }

    @PostMapping("/db-template/{id}/submit")
    @Operation(summary = "提交审核 (DRAFT → PENDING_REVIEW)")
    public ResponseEntity<ScriptTemplate> submitForReview(@PathVariable String id) {
        return ResponseEntity.ok(scriptService.submitForReview(id));
    }

    @PostMapping("/db-template/{id}/approve")
    @Operation(summary = "审核通过 (PENDING_REVIEW → APPROVED)")
    public ResponseEntity<ScriptTemplate> approveTemplate(
            @PathVariable String id,
            @RequestParam String approver) {
        return ResponseEntity.ok(scriptService.approveTemplate(id, approver));
    }

    @PostMapping("/db-template/{id}/freeze")
    @Operation(summary = "冻结模板 (APPROVED → FROZEN, 司法锁)")
    public ResponseEntity<ScriptTemplate> freezeTemplate(@PathVariable String id) {
        return ResponseEntity.ok(scriptService.freezeTemplate(id));
    }
}
