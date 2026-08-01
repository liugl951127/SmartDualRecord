package com.minimax.dualrecord.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 业务数据血缘 - DAG 关系图
 * 描述业务-录像-风险-话术-事件-签字 等实体之间的关联
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("tb_data_lineage")
public class DataLineage {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @TableField("business_id")
    private String businessId;

    @TableField("parent_type")
    private String parentType;        // business / recording / risk_assessment / ...

    @TableField("parent_id")
    private String parentId;

    @TableField("child_type")
    private String childType;

    @TableField("child_id")
    private String childId;

    @TableField("relation_type")
    private String relationType;      // CREATES / USES / REFERENCES / DERIVES_FROM / TRIGGERS

    @TableField("relation_meta")
    private String relationMeta;      // JSON 描述

    @TableField("created_at")
    private LocalDateTime createdAt;
}
