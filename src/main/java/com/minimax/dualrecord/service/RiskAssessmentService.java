package com.minimax.dualrecord.service;

import com.minimax.dualrecord.domain.RiskAssessment;
import com.minimax.dualrecord.exception.BusinessException;
import com.minimax.dualrecord.repository.RiskAssessmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 风险评估服务
 *
 * <h3>5 级分类 (C1-C5)</h3>
 *  - C1 保守：防御为主, 货基 / 短期国债 / 银行存款
 *  - C2 稳健：可承担小幅波动, 纯债基金 / 短债理财
 *  - C3 平衡：可承担中等波动, 混合型理财 / 普通年金
 *  - C4 成长：可承担较大波动, 股基 / 投连稳健账户
 *  - C5 激进：可承担高波动, 私募 / 杠杆 / 衍生品
 *
 * <h3>9 维评估因子 (合规要求)</h3>
 *  - liquidity              流动性
 *  - maturity               到期时限
 *  - leverage               杠杆
 *  - structural_complexity  结构复杂性
 *  - min_amount             最低金额
 *  - investment_direction   投资方向
 *  - offering_method        募集方式
 *  - issuer_credit          发行人信用
 *  - historical_performance 同类业绩
 *
 * <h3>12 个月有效期</h3>
 *  - 超过 12 个月必须重测
 *  - 中保协自律规范第十五条
 *
 * <h3>适当性匹配 (双规则)</h3>
 *  - 客户等级 ≥ 产品等级：直接销售 + 双录明确告知
 *  - 客户等级 < 产品等级：客户主动申请 + 二次书面确认 + 销售方未主动推介
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskAssessmentService {

    private final RiskAssessmentRepository repository;

    /** 9 维权重 (合计 100) */
    private static final Map<String, Double> WEIGHTS = Map.of(
            "liquidity",             15.0,
            "maturity",              10.0,
            "leverage",              12.0,
            "structural_complexity", 10.0,
            "min_amount",             6.0,
            "investment_direction",  15.0,
            "offering_method",        6.0,
            "issuer_credit",         16.0,
            "historical_performance",10.0
    );

    /**
     * 获取客户最新的有效风险评估 (12 个月内)
     */
    public RiskAssessment getLatestValid(String customerIdHash) {
        List<RiskAssessment> list = repository.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<RiskAssessment>()
                        .eq("customer_id_hash", customerIdHash)
                        .eq("deleted", 0)
                        .orderByDesc("assessed_at")
                        .last("LIMIT 1"));
        if (list.isEmpty()) return null;
        RiskAssessment latest = list.get(0);
        if (!latest.isValid()) {
            log.warn("客户 {} 的风险评估已过期 (validUntil={})", customerIdHash, latest.getValidUntil());
            return null;
        }
        return latest;
    }

    /**
     * 评分 → 风险等级 (0-100)
     *  - 0-19  C1 保守
     *  - 20-39 C2 稳健
     *  - 40-59 C3 平衡
     *  - 60-79 C4 成长
     *  - 80+   C5 激进
     */
    public String scoreToRiskLevel(BigDecimal score) {
        if (score == null) return "C1";
        double s = score.doubleValue();
        if (s < 20) return "C1";
        if (s < 40) return "C2";
        if (s < 60) return "C3";
        if (s < 80) return "C4";
        return "C5";
    }

    /**
     * 适当性匹配决策 (主对外接口)
     * 客户风险等级 (C1-C5) vs 产品风险等级 (P1-P5 / R1-R5)
     */
    public MatchResult matchSuitability(String customerLevel, String productLevel) {
        if (customerLevel == null || productLevel == null) {
            return new MatchResult(false, "CUSTOMER_OR_PRODUCT_LEVEL_NULL",
                    "客户或产品风险等级缺失", false, false);
        }
        int cust = extractLevel(customerLevel);
        int prod = extractLevel(productLevel);
        if (cust == 0 || prod == 0) {
            return new MatchResult(false, "INVALID_LEVEL_FORMAT",
                    "风险等级格式错误: 客户=" + customerLevel + ", 产品=" + productLevel, false, false);
        }
        if (cust >= prod) {
            return new MatchResult(true, "MATCHED",
                    String.format("客户 C%d ≥ 产品 %s，可直接销售", cust, productLevel),
                    false, false);
        }
        // 客户等级 < 产品等级 → 不匹配
        return new MatchResult(false, "MISMATCHED",
                String.format("客户 C%d < 产品 %s，需客户主动申请 + 二次书面确认", cust, productLevel),
                true, true);
    }

    /** 兼容旧名 */
    public MatchResult match(String customerLevel, String productLevel) {
        return matchSuitability(customerLevel, productLevel);
    }

    private int extractLevel(String level) {
        if (level == null || level.length() < 2) return 0;
        try {
            return Integer.parseInt(level.substring(1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 提交风险评估 (9 维加权评分 + 12 个月有效期)
     */
    public RiskAssessment submit(String customerIdHash, Map<String, Object> answers) {
        BigDecimal score = calculateScore(answers);
        String level = scoreToRiskLevel(score);

        RiskAssessment ra = new RiskAssessment();
        ra.setCustomerIdHash(customerIdHash);
        ra.setAssessmentId("ASSESS" + System.currentTimeMillis());
        ra.setAnswersJson(toJson(answers));
        ra.setOverallScore(score);
        ra.setRiskLevel(level);
        ra.setValidUntil(LocalDate.now().plusMonths(12));  // 12 个月有效期
        ra.setAssessedAt(java.time.LocalDateTime.now());
        ra.setDeleted(0);
        repository.insert(ra);

        log.info("风险评估已提交: customerId={}, score={}, level={}, validUntil={}",
                customerIdHash, score, level, ra.getValidUntil());
        return ra;
    }

    /**
     * 9 维加权评分 (0-100)
     * 每维评分 0-100, 权重按 WEIGHTS 累加
     */
    private BigDecimal calculateScore(Map<String, Object> answers) {
        double total = 0.0;
        for (Map.Entry<String, Double> e : WEIGHTS.entrySet()) {
            String factor = e.getKey();
            double weight = e.getValue();
            double factorScore = extractFactorScore(answers, factor);
            total += factorScore * weight / 100.0;
        }
        return BigDecimal.valueOf(Math.min(100.0, Math.max(0.0, total))).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 单维评分 (兼容多种输入格式)
     *  - 字符串选项: 保守/稳健/平衡/成长/激进 → 10/30/50/70/90
     *  - 数字: 直接使用
     *  - null: 0
     */
    private double extractFactorScore(Map<String, Object> answers, String factor) {
        Object v = answers.get(factor);
        if (v == null) return 0.0;
        if (v instanceof Number n) {
            double d = n.doubleValue();
            return Math.min(100.0, Math.max(0.0, d));
        }
        String s = String.valueOf(v);
        return switch (s) {
            case "保守", "保守型", "低", "短期", "无", "稳健型" -> 10.0;
            case "稳健", "中等", "中低", "中性", "普通" -> 30.0;
            case "平衡", "中", "中等波动", "混合型" -> 50.0;
            case "成长", "中高", "高波动", "积极" -> 70.0;
            case "激进", "激进型", "高", "长期", "复杂", "高杠杆" -> 90.0;
            default -> {
                try { yield Double.parseDouble(s); }
                catch (NumberFormatException ex) { yield 0.0; }
            }
        };
    }

    private String toJson(Object obj) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * 匹配结果
     *  - matched: 是否可销售
     *  - reasonCode: 机器可读原因
     *  - reason: 人类可读说明
     *  - requireSecondaryConfirm: 是否需要二次书面确认
     *  - requireHumanDualSign: 是否需要销售方未主动推介书面证明
     */
    public record MatchResult(boolean matched,
                              String reasonCode,
                              String reason,
                              boolean requireSecondaryConfirm,
                              boolean requireHumanDualSign) {
        public MatchResult(boolean matched, String reason, boolean requireSecondaryConfirm) {
            this(matched, matched ? "MATCHED" : "MISMATCHED", reason, requireSecondaryConfirm, !matched);
        }
    }
}
