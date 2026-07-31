package com.minimax.dualrecord.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 禁播词 · 全局规则
 * 命中即触发实时告警（HIGH）或事后标红（MEDIUM/LOW）
 */
@Data
@NoArgsConstructor
@TableName("tb_forbidden_phrase")
public class ForbiddenPhrase {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @TableField("phrase")
    private String phrase;

    @TableField("severity")
    private String severity;                // HIGH / MEDIUM / LOW

    @TableField("product_types")
    private String productTypes;            // ALL / INSURANCE,WEALTH,FUND

    @TableField("regulation_ref")
    private String regulationRef;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("deleted")
    private Integer deleted;
}
