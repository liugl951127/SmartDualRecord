package com.minimax.dualrecord.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.minimax.dualrecord.domain.enums.BusinessType;
import com.minimax.dualrecord.domain.enums.Channel;
import com.minimax.dualrecord.domain.enums.RecordingState;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 业务主表 · 贯穿全链路的"业务 ID"载体
 *
 * 业务 ID = 流水号 = 跨段串联 = 事后复盘的唯一锚点
 */
@Data
@NoArgsConstructor
@TableName("tb_business")
public class Business {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @TableField("business_id")
    private String businessId;

    @TableField("business_type")
    private BusinessType businessType;

    @TableField("product_id")
    private String productId;

    @TableField("customer_id_hash")
    private String customerIdHash;

    @TableField("seller_id_hash")
    private String sellerIdHash;

    @TableField("channel")
    private Channel channel;

    @TableField("state")
    private RecordingState state;

    @TableField("current_node")
    private String currentNode;

    @TableField("amount")
    private BigDecimal amount;

    @TableField("risk_level")
    private String riskLevel;          // 客户风险等级 C1-C5

    @TableField("product_risk_level")
    private String productRiskLevel;    // 产品风险等级 P1-P5 / R1-R5

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("archived_at")
    private LocalDateTime archivedAt;

    @TableField("deleted")
    private Integer deleted;
}
