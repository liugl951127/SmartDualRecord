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
@TableName("tb_saga_event")
public class SagaEvent {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @TableField("saga_id")
    private String sagaId;

    @TableField("step_order")
    private Integer stepOrder;

    @TableField("event_type")
    private String eventType;  // STARTED / STEP_OK / STEP_FAIL / COMPENSATING / COMPENSATED / RETRY / COMPLETED / FAILED / SUSPENDED

    @TableField("level")
    private String level;  // INFO / WARN / ERROR

    @TableField("message")
    private String message;

    @TableField("payload_json")
    private String payloadJson;

    @TableField("occurred_at")
    private LocalDateTime occurredAt;
}
