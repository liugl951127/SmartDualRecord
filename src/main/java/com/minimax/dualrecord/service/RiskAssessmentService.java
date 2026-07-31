package com.minimax.dualrecord.service;

import com.minimax.dualrecord.domain.RiskAssessment;
import com.minimax.dualrecord.exception.BusinessException;
import com.minimax.dualrecord.repository.RiskAssessmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 风险评估服务
 *
 * 5 级分类：C1 保守 → C5 激进
 * 9 维评估因子：流动性、到期时限、杠杆、结构复杂性、最低金额、投资方向、募集方式、发行人信用、同类业绩
 * 有效期 12 个月
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskAssessmentService {

    private final RiskAssessmentRepository repository;

    /**
     * 获取客户最新的有效风险评估
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
     * 评分 → 风险等级
     */
    public String scoreToRiskLevel(BigDecimal score) {
        if (score == null) return "C1";
        double s = score.doubleValue();
        if (s < 20) return "C1";          // 保守
        if (s < 40) return "C2";          // 稳健
        if (s < 60) return "C3";          // 平衡
        if (s < 80) return "C4";          // 成长
        return "C5";                       // 激进
    }

    /**
     * 适当性匹配决策
     * 客户风险等级 vs 产品风险等级
     */
    public MatchResult match(String customerLevel, String productLevel) {
        if (customerLevel == null || productLevel == null) {
            return new MatchResult(false, "客户或产品风险等级缺失", false);
        }
        int cust = extractLevel(customerLevel);
        int prod = extractLevel(productLevel);
        if (cust >= prod) {
            // 客户风险等级 ≥ 产品风险等级 → 可直接销售
            return new MatchResult(true, "匹配：客户风险承受覆盖产品风险", false);
        }
        // 客户风险等级 < 产品风险等级 → 需客户主动申请 + 二次确认
        return new MatchResult(false,
                String.format("不匹配：客户 C%d < 产品 %s，需客户主动申请", cust, productLevel),
                true);
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
     * 提交风险评估
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

        log.info("风险评估已提交: customerId={}, score={}, level={}", customerIdHash, score, level);
        return ra;
    }

    private BigDecimal calculateScore(Map<String, Object> answers) {
        // 简化的 9 维加权评分（实际生产会用 LLM + 规则混合）
        double score = 50.0;
        for (Object v : answers.values()) {
            if (v instanceof Number n) {
                score += n.doubleValue() * 5;
            } else if (v instanceof String s) {
                // 字符串选项：保守/稳健/平衡/成长/激进
                score += switch (s) {
                    case "保守" -> 5;
                    case "稳健" -> 15;
                    case "平衡" -> 25;
                    case "成长" -> 35;
                    case "激进" -> 45;
                    default -> 0;
                };
            }
        }
        return BigDecimal.valueOf(Math.min(100, score));
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
     */
    public record MatchResult(boolean matched, String reason, boolean requireSecondaryConfirm) {}
}
