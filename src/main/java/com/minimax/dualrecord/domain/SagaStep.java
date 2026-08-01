package com.minimax.dualrecord.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("tb_saga_step")
public class SagaStep {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @TableField("saga_id")
    private String sagaId;

    @TableField("step_order")
    private Integer stepOrder;

    @TableField("step_name")
    private String stepName;

    @TableField("target_method")
    private String targetMethod;

    @TableField("compensate_method")
    private String compensateMethod;

    @TableField("input_json")
    private String inputJson;

    @TableField("output_json")
    private String outputJson;

    @TableField("status")
    private String status;  // PENDING / RUNNING / COMPLETED / FAILED / COMPENSATED / SKIPPED

    @TableField("error_message")
    private String errorMessage;

    @TableField("retry_count")
    private Integer retryCount;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("completed_at")
    private LocalDateTime completedAt;

    @TableField("duration_ms")
    private Long durationMs;
}
