package com.minimax.dualrecord.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 录像事件标注 (v1.2 录像合规)
 *
 * 关键事件打时间戳标记, 便于:
 *  - 监管事后核查 (1 秒内跳到关键事件)
 *  - 客户回看 (直接定位风险揭示时刻)
 *  - 争议处理 (精确定位"是否明确肯定")
 *
 * 标注类型:
 *  - NODE_START / NODE_END    8 节点开始/结束
 *  - RISK_DISCLOSED           风险揭示完成
 *  - CUSTOMER_AFFIRMATIVE     客户肯定词
 *  - SIGNED                   客户签字
 *  - MANUAL_FLAG              人工标记 (敏感事件)
 *  - FORBIDDEN_PHRASE_HIT     禁播词命中
 *  - DEEPFAKE_SUSPECT         反深伪命中
 */
@Data
@NoArgsConstructor
@TableName("tb_recording_annotation")
public class RecordingAnnotation {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @TableField("rec_id")
    private String recId;

    @TableField("business_id")
    private String businessId;

    @TableField("annotation_type")
    private String annotationType;

    @TableField("node_id")
    private String nodeId;

    @TableField("timestamp_ms")
    private Long timestampMs;

    @TableField("note")
    private String note;

    @TableField("operator_id")
    private String operatorId;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("deleted")
    private Integer deleted;
}
