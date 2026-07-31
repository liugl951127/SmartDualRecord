package com.minimax.dualrecord.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * YAML 话术模板加载器（增强版）
 *
 * 支持 3 类模板：
 *  1. 默认模板：classpath:scripts/default-script.yaml
 *     → 缓存 key = "__DEFAULT__"
 *  2. 产品族模板：classpath:scripts/insurance/wealth/fund/ 下的 productType 模板
 *     → 缓存 key = "type:INSURANCE" / "type:WEALTH" / "type:FUND"
 *  3. 产品专属模板：classpath:scripts/{产品族}/{product-id}.yaml
 *     → 缓存 key = "{product-id}"
 */
@Slf4j
@Component
public class ScriptYamlLoader {

    private final Map<String, Map<String, Object>> cache = new HashMap<>();
    private final Yaml yaml = new Yaml();

    /**
     * 加载所有 YAML 模板到缓存
     */
    public synchronized void loadAll() {
        cache.clear();
        try {
            ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:scripts/**/*.yaml");
            for (Resource resource : resources) {
                try (InputStream in = resource.getInputStream()) {
                    Map<String, Object> data = yaml.load(in);
                    String key = deriveKey(resource.getFilename(), data);
                    if (key != null) {
                        cache.put(key, data);
                        log.info("加载话术模板: key={}, file={}", key, resource.getFilename());
                    } else {
                        log.warn("YAML 文件无法解析: {}", resource.getFilename());
                    }
                }
            }
            log.info("共加载 {} 个话术模板（含默认 + 产品族 + 产品专属）", cache.size());
        } catch (IOException e) {
            log.error("加载 YAML 模板失败", e);
            throw new RuntimeException("Failed to load script templates", e);
        }
    }

    /**
     * 根据文件名推导缓存 key
     */
    @SuppressWarnings("unchecked")
    private String deriveKey(String filename, Map<String, Object> data) {
        if (filename == null) return null;
        String lower = filename.toLowerCase();
        if (lower.contains("default")) {
            return "__DEFAULT__";
        }
        // 产品族模板：在 insurance/wealth/fund 目录下的 "_type.yaml"
        if (lower.contains("_type") || lower.startsWith("type-")) {
            String productType = (String) data.get("product_type");
            if (productType != null) {
                return "type:" + productType;
            }
        }
        // 产品专属
        return (String) data.get("product_id");
    }

    public Map<String, Object> get(String productId) {
        if (cache.isEmpty()) {
            loadAll();
        }
        return cache.get(productId);
    }

    public Map<String, Map<String, Object>> getAll() {
        if (cache.isEmpty()) {
            loadAll();
        }
        return cache;
    }

    public int size() {
        if (cache.isEmpty()) {
            loadAll();
        }
        return cache.size();
    }
}
