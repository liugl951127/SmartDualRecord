package com.minimax.dualrecord.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户-理财经理会话 (v1.5 H5 → PC 转接)
 *
 * 场景: H5 客户做线上双录时, 需要人工协助
 *  - 客户点击"转接理财经理"
 *  - 弹出可选理财经理列表 (按就近原则)
 *  - 发送请求到 PC 端理财经理
 *  - 理财经理接单 → 建立会话
 *  - 客户在 H5 看到理财经理头像 + 文字/语音交流
 *  - 理财经理接管录制或继续指导
 */
@Data
@TableName("tb_advisor_session")
public class AdvisorSession {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @TableField("session_id")
    private String sessionId;             // UUID, 客户端使用

    @TableField("business_id")
    private String businessId;             // 关联业务

    @TableField("customer_id_hash")
    private String customerIdHash;         // 客户脱敏 ID

    @TableField("customer_name")
    private String customerName;           // 客户姓名 (来自身份核验)

    @TableField("customer_mobile")
    private String customerMobile;         // 客户手机 (脱敏)

    @TableField("advisor_id")
    private String advisorId;               // 理财经理 ID

    @TableField("advisor_name")
    private String advisorName;

    @TableField("advisor_branch")
    private String advisorBranch;           // 网点

    /** 转接原因 (TECH_ISSUE / PRODUCT_QUESTION / COMPLIANCE_QUERY / OTHER) */
    @TableField("reason")
    private String reason;

    /** 客户填写的具体描述 */
    @TableField("description")
    private String description;

    /** 状态: PENDING / ACCEPTED / DECLINED / ACTIVE / ENDED / TIMEOUT */
    @TableField("status")
    private String status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("accepted_at")
    private LocalDateTime acceptedAt;

    @TableField("ended_at")
    private LocalDateTime endedAt;

    /** 结束原因 (CUSTOMER_LEFT / ADVISOR_ENDED / TIMEOUT / BUSINESS_DONE) */
    @TableField("end_reason")
    private String endReason;

    @TableField("deleted")
    private Integer deleted;
}
