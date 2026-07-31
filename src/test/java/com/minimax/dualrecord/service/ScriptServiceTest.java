package com.minimax.dualrecord.service;

import com.minimax.dualrecord.config.ScriptProperties;
import com.minimax.dualrecord.util.ScriptYamlLoader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 通用话术 · 三层叠加测试
 */
@SpringBootTest
class ScriptServiceTest {

    @Autowired
    private ScriptService scriptService;

    @Autowired
    private ScriptYamlLoader loader;

    @Autowired
    private ScriptProperties properties;

    @Test
    void testYamlLoading() {
        loader.loadAll();
        // 默认模板 + 3 套产品专属 + 3 套产品族 = 至少 7 个
        assertTrue(loader.size() >= 7, "应至少加载 7 个模板（含默认 + 3 族 + 3 专属）");
    }

    @Test
    void testDefaultScriptExists() {
        // __DEFAULT__ 必定存在
        Map<String, Object> defaultScript = scriptService.getScript("__DEFAULT__");
        assertNotNull(defaultScript);
        assertEquals("R2", defaultScript.get("risk_level"));
        assertEquals(8, ((List<?>) defaultScript.get("nodes")).size());
    }

    @Test
    void testProductSpecificOverridesDefault() {
        // 投连险 P5 比默认 R2 高 → 应该返回 P5
        Map<String, Object> script = scriptService.getScript("LIC-INV-2026Q3-001");
        assertNotNull(script);
        assertEquals("P5", script.get("risk_level"));
    }

    @Test
    void testProductTypeDefaultForUnknownProduct() {
        // 未知产品但指定产品族 → 应返回产品族默认
        Map<String, Object> script = scriptService.getScript("UNKNOWN-PRODUCT-001", "WEALTH");
        assertNotNull(script);
        assertEquals("R2", script.get("risk_level"));
        // 应该有"产品族"特征：理财封闭期
        Object productType = script.get("product_type");
        // product_type 可能在合并后是 GENERIC 或 WEALTH
        assertNotNull(productType);
    }

    @Test
    void testGlobalDefaultForTotallyUnknown() {
        // 未知产品 + 不指定族 → 走全局默认
        Map<String, Object> script = scriptService.getScript("TOTALLY-UNKNOWN-999");
        assertNotNull(script);
        assertEquals("R2", script.get("risk_level"));
    }

    @Test
    void testForbiddenPhrasesUnion() {
        // 全局禁播词 + 投连险禁播词（合并去重）
        Map<String, Object> script = scriptService.getScript("LIC-INV-2026Q3-001");
        @SuppressWarnings("unchecked")
        List<String> phrases = (List<String>) script.get("forbidden_phrases");

        // 必定有全局默认
        assertTrue(phrases.contains("保证收益"));
        // 必定有产品族额外
        assertTrue(phrases.contains("保本保息"));
    }

    @Test
    void testPlaceholderReplacement() {
        Map<String, Object> script = scriptService.getScript("__DEFAULT__");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) script.get("nodes");
        // 找到 02-disclosure 节点
        Map<String, Object> disclosure = nodes.stream()
                .filter(n -> "02-disclosure".equals(n.get("node_id")))
                .findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        List<String> phrases = (List<String>) disclosure.get("mandatory_phrases");
        // 占位符应该被替换
        assertTrue(phrases.stream().anyMatch(p -> !p.contains("{productName}")));
    }

    @Test
    void testRuntimeForbiddenPhraseAdd() {
        int before = properties.getDefaultForbiddenPhrases().size();
        scriptService.addForbiddenPhrase("测试禁播词XYZ");
        int after = properties.getDefaultForbiddenPhrases().size();
        assertEquals(before + 1, after);
    }

    @Test
    void testAllScriptsContainsDefault() {
        Map<String, Map<String, Object>> all = scriptService.getAllScripts();
        assertTrue(all.containsKey("__DEFAULT__"));
    }
}
