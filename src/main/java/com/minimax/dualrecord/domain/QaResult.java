package com.minimax.dualrecord.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 质检结果 · 同一笔业务跨渠道统一进同一份 record
 */
@Data
@NoArgsConstructor
@TableName("tb_qa_result")
public class QaResult {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @TableField("qa_id")
    private String qaId;

    @TableField("rec_id")
    private String recId;

    @TableField("business_id")
    private String businessId;

    @TableField("checker_type")
    private String checkerType;            // AI / HUMAN / AI_PLUS_HUMAN

    @TableField("ai_model_version")
    private String aiModelVersion;

    @TableField("ai_qa_score")
    private BigDecimal aiQaScore;

    @TableField("ai_qa_result")
    private String aiQaResult;              // PASS / PASS_WITH_FINDINGS / FAIL

    @TableField("issues_json")
    private String issuesJson;              // JSON 数组

    @TableField("human_reviewer_id")
    private String humanReviewerId;

    @TableField("human_review_status")
    private String humanReviewStatus;

    @TableField("rectification_status")
    private String rectificationStatus;

    @TableField("check_time")
    private LocalDateTime checkTime;

    @TableField("deleted")
    private Integer deleted;
}
