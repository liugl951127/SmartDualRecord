package com.minimax.dualrecord.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 客户风险评估
 * 有效期 12 个月；超过必须重测（依据中保协自律规范）
 */
@Data
@NoArgsConstructor
@TableName("tb_risk_assessment")
public class RiskAssessment {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @TableField("business_id")
    private String businessId;

    @TableField("customer_id_hash")
    private String customerIdHash;

    @TableField("assessment_id")
    private String assessmentId;

    @TableField("answers_json")
    private String answersJson;

    @TableField("overall_score")
    private BigDecimal overallScore;

    @TableField("risk_level")
    private String riskLevel;               // C1-C5

    @TableField("valid_until")
    private LocalDate validUntil;

    @TableField("assessed_at")
    private LocalDateTime assessedAt;

    @TableField("date_add")
    private LocalDateTime dateAdd;

    @TableField("date_upd")
    private LocalDateTime dateUpd;

    @TableField("deleted")
    private Integer deleted;

    public boolean isValid() {
        return validUntil != null && validUntil.isAfter(LocalDate.now());
    }
}
