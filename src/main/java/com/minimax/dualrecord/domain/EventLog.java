package com.minimax.dualrecord.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 事件流 · 事件溯源（Event Sourcing）
 * 任意状态变更都产生一条事件，可重放、可审计
 */
@Data
@NoArgsConstructor
@TableName("tb_event")
public class EventLog {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @TableField("business_id")
    private String businessId;

    @TableField("event_type")
    private String eventType;

    @TableField("event_data")
    private String eventData;                // JSON

    @TableField("from_state")
    private String fromState;

    @TableField("to_state")
    private String toState;

    @TableField("actor_id")
    private String actorId;

    @TableField("actor_type")
    private String actorType;                // SYSTEM / HUMAN / AI

    @TableField("created_at")
    private LocalDateTime createdAt;
}
