package com.minimax.dualrecord.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 录像节点明细 · 8 节点各 1 条
 * 关键节点（NODE_06_CONFIRM）必须有 ASR 命中关键词 + 坐席人工双签
 */
@Data
@NoArgsConstructor
@TableName("tb_rec_node")
public class RecordingNodeDetail {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @TableField("business_id")
    private String businessId;

    @TableField("rec_id")
    private String recId;

    @TableField("node_id")
    private String nodeId;

    @TableField("node_name")
    private String nodeName;

    @TableField("start_utc")
    private LocalDateTime startUtc;

    @TableField("end_utc")
    private LocalDateTime endUtc;

    @TableField("duration_ms")
    private Long durationMs;

    @TableField("completed")
    private Integer completed;

    @TableField("evidence_ts")
    private LocalDateTime evidenceTs;

    @TableField("operator_id")
    private String operatorId;

    @TableField("deleted")
    private Integer deleted;
}
