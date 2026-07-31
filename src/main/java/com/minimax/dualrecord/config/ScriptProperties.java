package com.minimax.dualrecord.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用话术配置 · 三层叠加体系中的最底层（全局默认）
 *
 * 优先级从高到低：
 *  1. DB / YAML 里的产品专属话术
 *  2. 产品族（INSURANCE / WEALTH / FUND）默认
 *  3. 本类配置的全局默认（兜底）
 *
 * 用法：
 *  - 运维修改 application.yml 即可改变全局行为
 *  - 新产品接入时，只要继承全局默认，立刻能跑
 *  - 缺字段时会自动从下层补，不报"空内容"错误
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "dual-record.script")
public class ScriptProperties {

    /** 全局默认风险等级（兜底） */
    private String defaultRiskLevel = "R2";

    /** 全局默认节点超时（秒） */
    private Integer defaultNodeTimeoutSec = 90;

    /** 全局默认禁播词（兜底） */
    private List<String> defaultForbiddenPhrases = new ArrayList<>(List.of(
            "保证收益", "保本保息", "稳赚不赔", "绝对安全", "无风险"
    ));

    /** 全局默认必问问题 */
    private List<String> defaultRequiredQuestions = new ArrayList<>(List.of(
            "您是否已了解本产品的风险特征？",
            "您是否已阅读产品说明书和风险揭示书？"
    ));

    /** 全局默认必播项（每个节点都播） */
    private List<String> defaultMandatoryPhrases = new ArrayList<>(List.of(
            "本产品不保证本金和收益",
            "投资有风险，过往业绩不代表未来表现"
    ));

    /** 按渠道差分（默认） */
    @NestedConfigurationProperty
    private Map<String, ChannelOverride> defaultChannelOverrides = new HashMap<>(Map.of(
            "OFFLINE", new ChannelOverride("same_frame", false, false, 0),
            "REMOTE_VIDEO", new ChannelOverride("same_frame", false, false, 0),
            "SELF_AI", new ChannelOverride("ai_with_disclosure", true, true, 1),
            "INTERNET_TEXT", new ChannelOverride("text_only", false, false, 0)
    ));

    /** 按产品族默认风险等级（兜底） */
    private Map<String, String> productTypeRiskLevel = new HashMap<>(Map.of(
            "INSURANCE", "P3",
            "WEALTH", "R2",
            "FUND", "R2"
    ));

    /** 占位符替换 */
    private Map<String, String> placeholders = new HashMap<>(Map.of(
            "{productName}", "本产品",
            "{productRiskLevel}", "R2"
    ));

    /**
     * 渠道差分
     */
    @Data
    public static class ChannelOverride {
        private String syncMode;
        private Boolean watermarkVisible;
        private Boolean aiDisclosure;
        private Integer audioIdPerMinute;

        public ChannelOverride() {}
        public ChannelOverride(String syncMode, Boolean watermarkVisible,
                                Boolean aiDisclosure, Integer audioIdPerMinute) {
            this.syncMode = syncMode;
            this.watermarkVisible = watermarkVisible;
            this.aiDisclosure = aiDisclosure;
            this.audioIdPerMinute = audioIdPerMinute;
        }
    }
}
