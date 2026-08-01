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
 * 审计链 - append-only 哈希链条目
 * 每条记录都是不可变的, 任何修改都会破坏 hash chain
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("tb_audit_chain")
public class AuditChainEntry {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @TableField("chain_id")
    private String chainId;

    @TableField("sequence_no")
    private Long sequenceNo;

    @TableField("operation_type")
    private String operationType;     // CREATE/UPDATE/DELETE/STATE_TRANSITION/SIGN/PRESERVE

    @TableField("entity_type")
    private String entityType;        // business/recording/rec_node/risk/script/event/...

    @TableField("entity_id")
    private String entityId;

    @TableField("business_id")
    private String businessId;

    @TableField("actor_id")
    private String actorId;

    @TableField("actor_role")
    private String actorRole;

    @TableField("payload_hash")
    private String payloadHash;       // SHA-256(payload_json)

    @TableField("payload_json")
    private String payloadJson;       // 完整 payload

    @TableField("prev_hash")
    private String prevHash;          // 上一节点的 chain_hash (or GENESIS for #0)

    @TableField("chain_hash")
    private String chainHash;         // SHA-256(prev_hash + payload_hash + metadata)

    @TableField("hmac_signature")
    private String hmacSignature;     // HMAC-SHA256(server_key, chain_hash)

    @TableField("merkle_root")
    private String merkleRoot;        // 每 N 条聚合一次

    @TableField("server_node")
    private String serverNode;        // 服务节点标识

    @TableField("signed_at")
    private LocalDateTime signedAt;
}
