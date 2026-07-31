package com.minimax.dualrecord.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RiskAssessmentServiceTest {

    @Test
    void testScoreToRiskLevel() {
        RiskAssessmentService service = new RiskAssessmentService(null);
        assertEquals("C1", service.scoreToRiskLevel(java.math.BigDecimal.valueOf(10)));
        assertEquals("C2", service.scoreToRiskLevel(java.math.BigDecimal.valueOf(30)));
        assertEquals("C3", service.scoreToRiskLevel(java.math.BigDecimal.valueOf(50)));
        assertEquals("C4", service.scoreToRiskLevel(java.math.BigDecimal.valueOf(70)));
        assertEquals("C5", service.scoreToRiskLevel(java.math.BigDecimal.valueOf(95)));
    }

    @Test
    void testMatchCompatible() {
        RiskAssessmentService service = new RiskAssessmentService(null);
        // 客户 C3 买 R2 产品 → 匹配
        var r1 = service.match("C3", "R2");
        assertTrue(r1.matched());
        assertFalse(r1.requireSecondaryConfirm());

        // 客户 C4 买 P5 产品 → 匹配
        var r2 = service.match("C4", "P5");
        assertTrue(r2.matched());
    }

    @Test
    void testMatchIncompatible() {
        RiskAssessmentService service = new RiskAssessmentService(null);
        // 客户 C2 主动买 P5 投连险 → 不匹配 + 需二次确认
        var r1 = service.match("C2", "P5");
        assertFalse(r1.matched());
        assertTrue(r1.requireSecondaryConfirm());

        // 客户 C1 买 R3 混合基金 → 不匹配
        var r2 = service.match("C1", "R3");
        assertFalse(r2.matched());
        assertTrue(r2.requireSecondaryConfirm());
    }
}
