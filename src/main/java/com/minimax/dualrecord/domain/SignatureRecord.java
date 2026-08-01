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
 * 签名记录 - 关键 entity 的 HMAC 签名
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("tb_signature")
public class SignatureRecord {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @TableField("entity_type")
    private String entityType;

    @TableField("entity_id")
    private String entityId;

    @TableField("business_id")
    private String businessId;

    @TableField("signer_id")
    private String signerId;

    @TableField("algorithm")
    private String algorithm;          // HMAC-SHA256 / RSA-SHA256

    @TableField("public_key_id")
    private String publicKeyId;        // 未来支持非对称

    @TableField("signature")
    private String signature;

    @TableField("content_hash")
    private String contentHash;

    @TableField("signed_at")
    private LocalDateTime signedAt;

    @TableField("valid_until")
    private LocalDateTime validUntil;

    @TableField("revoked")
    private Integer revoked;           // 0/1

    @TableField("revoke_reason")
    private String revokeReason;
}
