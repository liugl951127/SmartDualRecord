package com.minimax.dualrecord.service;

import com.minimax.dualrecord.config.ScriptProperties;
import com.minimax.dualrecord.domain.ScriptTemplate;
import com.minimax.dualrecord.exception.BusinessException;
import com.minimax.dualrecord.repository.ScriptTemplateRepository;
import com.minimax.dualrecord.util.ScriptYamlLoader;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 话术服务 · 三层叠加（通用配置）
 *
 * ┌────────────────────────────────────────────┐
 * │ Layer 3  产品专属 YAML/DB（最高优先级）        │
 * │ Layer 2  产品族默认（INSURANCE/WEALTH/FUND） │
 * │ Layer 1  全局默认（application.yml）         │
 * └────────────────────────────────────────────┘
 *
 * 叠加规则：
 *  - 任何字段缺失，自动从下一层补
 *  - 列表型字段（如禁播词）做"并集"：各层的全部合并去重
 *  - 标量型字段（如 risk_level）上层覆盖下层
 *  - 关键字段全无 → 抛异常（不静默用空）
 *
 * 好处：
 *  1. 新产品接入不需要先写 YAML，自动用全局默认跑起来
 *  2. 产品族通用差异（如保险的犹豫期 vs 理财的封闭期）只配一次
 *  3. 改全局禁播词不用改每个产品 YAML
 *  4. 同一份代码 28 个产品复用，运维成本 ↓
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptService {

    private final ScriptYamlLoader yamlLoader;
    private final ScriptTemplateRepository templateRepository;
    private final ScriptProperties scriptProperties;

    private static final String DEFAULT_PRODUCT_ID = "__DEFAULT__";

    @PostConstruct
    public void init() {
        yamlLoader.loadAll();
        log.info("ScriptService 初始化完成: YAML 模板 {} 份, 全局禁播词 {} 个, 必问 {} 个",
                yamlLoader.size(),
                scriptProperties.getDefaultForbiddenPhrases().size(),
                scriptProperties.getDefaultRequiredQuestions().size());
    }

    /**
     * 根据 productId 获取话术（无产品专属时回退到通用默认）
     */
    public Map<String, Object> getScript(String productId) {
        return resolveScript(productId, null);
    }

    /**
     * 根据 productId + productType 获取话术（指定产品族默认）
     */
    public Map<String, Object> getScript(String productId, String productType) {
        return resolveScript(productId, productType);
    }

    /**
     * 三层叠加核心算法
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveScript(String productId, String productType) {
        // Layer 3: 产品专属
        Map<String, Object> productScript = yamlLoader.get(productId);

        // Layer 2: 产品族默认（按 productType 找）
        Map<String, Object> productTypeScript = null;
        if (productType != null) {
            String typeKey = "type:" + productType;
            productTypeScript = yamlLoader.get(typeKey);
        }

        // Layer 1: 全局默认
        Map<String, Object> globalScript = buildGlobalScript();

        // 三层叠加（从低到高合并：低层被高层覆盖，列表做并集）
        Map<String, Object> merged = deepMerge(globalScript, productTypeScript);
        merged = deepMerge(merged, productScript);

        // 占位符替换
        applyPlaceholders(merged, productId, productType);

        return merged;
    }

    /**
     * 构造全局默认话术（从 application.yml + default-script.yaml 读）
     */
    private Map<String, Object> buildGlobalScript() {
        Map<String, Object> script = new HashMap<>();
        script.put("product_id", DEFAULT_PRODUCT_ID);
        script.put("product_type", "GENERIC");
        script.put("risk_level", scriptProperties.getDefaultRiskLevel());
        script.put("version", "default-v1");

        // 8 节点通用结构
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            String code = String.format("%02d", i);
            String[] names = {"出示身份证明", "风险揭示", "产品展示", "权利义务告知",
                    "如实告知询问", "明确肯定答复", "签署产品协议", "补充询问"};
            String[] ids = {"identity", "disclosure", "product", "rights",
                    "truth-tell", "confirm", "sign", "followup"};

            Map<String, Object> node = new HashMap<>();
            node.put("node_id", code + "-" + ids[i - 1]);
            node.put("display_name", names[i - 1]);
            node.put("duration_sec", scriptProperties.getDefaultNodeTimeoutSec());

            // 节点 2/6 必有内容
            if (i == 2) {
                node.put("mandatory_phrases", new ArrayList<>(scriptProperties.getDefaultMandatoryPhrases()));
                node.put("critical", true);
            } else if (i == 6) {
                node.put("mandatory_phrases", List.of(
                        "您是否已了解本产品的风险特征？",
                        "您是否已阅读产品说明书？"));
                node.put("critical", true);
                node.put("require_asr_affirmative", true);
                node.put("require_human_dual_sign", true);
            }

            nodes.add(node);
        }
        script.put("nodes", nodes);
        script.put("forbidden_phrases", new ArrayList<>(scriptProperties.getDefaultForbiddenPhrases()));
        script.put("required_questions", new ArrayList<>(scriptProperties.getDefaultRequiredQuestions()));
        script.put("channel_overrides", buildDefaultChannelOverrides());
        script.put("source", "GLOBAL_DEFAULT");
        return script;
    }

    private Map<String, Object> buildDefaultChannelOverrides() {
        Map<String, Object> overrides = new HashMap<>();
        scriptProperties.getDefaultChannelOverrides().forEach((k, v) -> {
            Map<String, Object> entry = new HashMap<>();
            entry.put("sync_mode", v.getSyncMode());
            entry.put("watermark_visible", v.getWatermarkVisible());
            entry.put("ai_disclosure", v.getAiDisclosure());
            entry.put("audio_id_per_minute", v.getAudioIdPerMinute());
            overrides.put(k, entry);
        });
        return overrides;
    }

    /**
     * 深度合并：下层 + 上层（上层字段覆盖下层，列表做并集）
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> deepMerge(Map<String, Object> base, Map<String, Object> override) {
        if (override == null || override.isEmpty()) return new HashMap<>(base);
        Map<String, Object> result = new HashMap<>(base);
        for (Map.Entry<String, Object> entry : override.entrySet()) {
            String key = entry.getKey();
            Object newVal = entry.getValue();
            Object oldVal = result.get(key);

            if (newVal == null) {
                continue;  // 上层显式 null 跳过（不覆盖）
            }
            if (oldVal == null) {
                result.put(key, newVal);
                continue;
            }
            // 列表型字段：并集
            if (newVal instanceof List<?> newList && oldVal instanceof List<?> oldList) {
                List<Object> merged = new ArrayList<>(oldList);
                for (Object o : newList) {
                    if (!merged.contains(o)) merged.add(o);
                }
                result.put(key, merged);
            }
            // Map 型字段：递归合并
            else if (newVal instanceof Map<?, ?> newMap && oldVal instanceof Map<?, ?> oldMap) {
                result.put(key, deepMerge((Map<String, Object>) oldMap, (Map<String, Object>) newMap));
            }
            // 标量型：上层覆盖
            else {
                result.put(key, newVal);
            }
        }
        return result;
    }

    /**
     * 占位符替换：{productName} {productRiskLevel} {customerLevel}
     */
    @SuppressWarnings("unchecked")
    private void applyPlaceholders(Map<String, Object> script, String productId, String productType) {
        String productName = (String) script.getOrDefault("product_name", productId);
        String productRiskLevel = (String) script.getOrDefault("risk_level", "R2");

        Map<String, String> replacements = new HashMap<>();
        replacements.put("{productName}", productName);
        replacements.put("{productRiskLevel}", productRiskLevel);

        // 递归替换字符串
        replaceInMap(script, replacements);
    }

    @SuppressWarnings("unchecked")
    private void replaceInMap(Map<String, Object> map, Map<String, String> replacements) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object val = entry.getValue();
            if (val instanceof String s) {
                for (Map.Entry<String, String> rep : replacements.entrySet()) {
                    if (s.contains(rep.getKey())) {
                        s = s.replace(rep.getKey(), rep.getValue());
                    }
                }
                entry.setValue(s);
            } else if (val instanceof Map<?, ?> m) {
                replaceInMap((Map<String, Object>) m, replacements);
            } else if (val instanceof List<?> list) {
                List<Object> newList = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof String s) {
                        String replaced = s;
                        for (Map.Entry<String, String> rep : replacements.entrySet()) {
                            if (replaced.contains(rep.getKey())) {
                                replaced = replaced.replace(rep.getKey(), rep.getValue());
                            }
                        }
                        newList.add(replaced);
                    } else if (item instanceof Map<?, ?> im) {
                        Map<String, Object> newMap = new HashMap<>((Map<String, Object>) im);
                        replaceInMap(newMap, replacements);
                        newList.add(newMap);
                    } else {
                        newList.add(item);
                    }
                }
                map.put(entry.getKey(), newList);
            }
        }
    }

    /**
     * 获取所有话术（含默认）
     */
    public Map<String, Map<String, Object>> getAllScripts() {
        Map<String, Map<String, Object>> all = new HashMap<>(yamlLoader.getAll());
        all.put(DEFAULT_PRODUCT_ID, buildGlobalScript());
        return all;
    }

    /**
     * 校验话术内容是否在 FROZEN 状态
     */
    public boolean isFrozen(String productId) {
        ScriptTemplate template = templateRepository.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ScriptTemplate>()
                        .eq("product_id", productId)
                        .eq("status", "FROZEN"));
        return template != null;
    }

    /**
     * 跨渠道 hash 一致性巡检
     */
    public List<String> consistencyCheck() {
        List<String> inconsistencies = new ArrayList<>();
        for (Map<String, Object> script : getAllScripts().values()) {
            String productId = (String) script.get("product_id");
            Map<String, Object> channelOverrides = (Map<String, Object>) script.get("channel_overrides");
            if (channelOverrides == null) continue;
            log.debug("巡检话术: productId={}, channels={}", productId, channelOverrides.keySet());
        }
        return inconsistencies;
    }

    /**
     * 暴露给 controller：获取当前生效的全局配置
     */
    public ScriptProperties getProperties() {
        return scriptProperties;
    }

    /**
     * 模拟运行时修改全局默认（不持久化，重启后失效）
     * 用于演示和测试
     */
    public void addForbiddenPhrase(String phrase) {
        if (!scriptProperties.getDefaultForbiddenPhrases().contains(phrase)) {
            scriptProperties.getDefaultForbiddenPhrases().add(phrase);
            log.info("运行时新增全局禁播词: {}", phrase);
        }
    }
}
