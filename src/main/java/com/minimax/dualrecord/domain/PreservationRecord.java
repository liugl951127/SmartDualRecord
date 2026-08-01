package com.minimax.dualrecord.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 司法/公证证据保全记录 (v1.2 录像合规)
 *
 * 触发场景:
 *  - 客户投诉进入司法程序
 *  - 监管现场检查
 *  - 客户理赔纠纷
 *  - 内部审计抽查
 *
 * 流程:
 *  1. 提交申请 → SUBMITTED
 *  2. 第三方公证处介入 → NOTARIZED (有 notary_cert_no)
 *  3. 司法鉴定 → 有 preservation_hash + file_sha256
 *  4. 保全期内不可删/改/销毁
 */
@Data
@NoArgsConstructor
@TableName("tb_preservation_record")
public class PreservationRecord {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @TableField("preservation_id")
    private String preservationId;

    @TableField("rec_id")
    private String recId;

    @TableField("business_id")
    private String businessId;

    @TableField("requester_id")
    private String requesterId;

    @TableField("requester_role")
    private String requesterRole;  // AUDITOR / REGULATOR / COURT / CUSTOMER

    @TableField("reason")
    private String reason;

    @TableField("notary_org")
    private String notaryOrg;

    @TableField("notary_cert_no")
    private String notaryCertNo;

    @TableField("preserved_at")
    private LocalDateTime preservedAt;

    @TableField("preservation_hash")
    private String preservationHash;

    @TableField("file_sha256")
    private String fileSha256;

    @TableField("expires_at")
    private LocalDateTime expiresAt;

    @TableField("status")
    private String status;  // SUBMITTED / NOTARIZED / REJECTED / EXPIRED

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("deleted")
    private Integer deleted;
}
