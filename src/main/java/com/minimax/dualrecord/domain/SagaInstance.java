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
 * Saga 实例 - 一次完整分布式事务
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("tb_saga_instance")
public class SagaInstance {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @TableField("saga_id")
    private String sagaId;

    @TableField("saga_name")
    private String sagaName;

    @TableField("business_id")
    private String businessId;

    @TableField("status")
    private String status;        // PENDING / RUNNING / COMPLETED / FAILED / COMPENSATING / COMPENSATED / SUSPENDED

    @TableField("current_step")
    private Integer currentStep;

    @TableField("total_steps")
    private Integer totalSteps;

    @TableField("payload_json")
    private String payloadJson;

    @TableField("context_json")
    private String contextJson;   // 步骤间共享上下文 (key=stepOrder, value=output)

    @TableField("error_message")
    private String errorMessage;

    @TableField("error_step")
    private String errorStep;

    @TableField("retry_count")
    private Integer retryCount;

    @TableField("max_retries")
    private Integer maxRetries;

    @TableField("timeout_ms")
    private Long timeoutMs;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("completed_at")
    private LocalDateTime completedAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
