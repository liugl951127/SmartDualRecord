package com.minimax.dualrecord.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 话术模板 · 单一来源真相 (Single Source of Truth)
 *
 * 4 渠道共用同一份"内容"，差异只在 channel_overrides
 * 状态： DRAFT / APPROVED / FROZEN
 *  FROZEN 后任何 AI 不得改写内容（合规红线 #6）
 */
@Data
@NoArgsConstructor
@TableName("tb_script_template")
public class ScriptTemplate {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @TableField("product_id")
    private String productId;

    @TableField("product_type")
    private String productType;             // INSURANCE / WEALTH / FUND

    @TableField("version")
    private String version;

    @TableField("risk_level")
    private String riskLevel;               // P1-P5 / R1-R5

    @TableField("mandatory_disclosure")
    private String mandatoryDisclosure;     // JSON 数组：必播项

    @TableField("forbidden_phrases")
    private String forbiddenPhrases;        // JSON 数组：禁播词

    @TableField("required_questions")
    private String requiredQuestions;       // JSON 数组：必问问题

    @TableField("channel_overrides")
    private String channelOverrides;        // JSON：4 渠道差分

    @TableField("content_hash")
    private String contentHash;             // 跨渠道 hash 校验

    @TableField("status")
    private String status;                  // DRAFT / APPROVED / FROZEN

    @TableField("approved_by")
    private String approvedBy;

    @TableField("approved_at")
    private LocalDateTime approvedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("deleted")
    private Integer deleted;
}
