package com.minimax.dualrecord.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.minimax.dualrecord.domain.enums.Channel;
import com.minimax.dualrecord.domain.enums.SellerType;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 录像 · 一笔业务可能产生多段（线下 + 远程 + 数字人）
 *
 * 关键约束：
 *  - 一次性生成，不可暂停（监管）
 *  - 国密 SM4 加密 + 区块链 hash 上链
 *  - ms 精度时间戳
 *  - 数字人场景：watermark_visible=1 + audio_id_per_minute ≥ 1
 */
@Data
@NoArgsConstructor
@TableName("tb_recording")
public class Recording {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @TableField("rec_id")
    private String recId;

    @TableField("business_id")
    private String businessId;

    @TableField("channel")
    private Channel channel;

    @TableField("seller_type")
    private SellerType sellerType;

    @TableField("rec_start_utc")
    private LocalDateTime recStartUtc;

    @TableField("rec_end_utc")
    private LocalDateTime recEndUtc;

    @TableField("duration_ms")
    private Long durationMs;

    @TableField("file_path")
    private String filePath;

    @TableField("file_sha256")
    private String fileSha256;

    @TableField("file_size_bytes")
    private Long fileSizeBytes;

    @TableField("encryption")
    private String encryption;

    @TableField("blockchain_tx")
    private String blockchainTx;

    @TableField("watermark_visible")
    private Integer watermarkVisible;

    @TableField("audio_id_per_minute")
    private Integer audioIdPerMinute;

    @TableField("linked_rec_id")
    private String linkedRecId;            // 跨段关联

    @TableField("location_branch")
    private String locationBranch;

    @TableField("retention_until")
    private LocalDate retentionUntil;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("deleted")
    private Integer deleted;
}
