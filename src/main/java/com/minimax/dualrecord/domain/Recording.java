package com.minimax.dualrecord.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.minimax.dualrecord.domain.enums.Channel;
import com.minimax.dualrecord.domain.enums.SellerType;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 录像 · 一笔业务可能产生多段（线下 + 远程 + 数字人）
 *
 * 关键约束 (v1.2 录像合规增强):
 *  - 一次性生成，不可暂停（监管）
 *  - 国密 SM4 加密 + 区块链 hash 上链
 *  - ms 精度时间戳
 *  - 数字人场景：watermark_visible=1 + audio_id_per_minute ≥ 1
 *  - 质量检测分数 0-100
 *  - GPS/IP 地理位置
 *  - 设备指纹
 *  - 证据保全 ID
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

    // ==================== v1.2 录像合规增强字段 ====================
    @TableField("quality_score")
    private Integer qualityScore;                       // 质量总分 0-100

    @TableField("quality_status")
    private String qualityStatus;                       // PASS / PASS_WITH_FINDINGS / FAIL

    @TableField("resolution")
    private String resolution;                          // 1920x1080 / 1280x720

    @TableField("fps")
    private Integer fps;

    @TableField("audio_bitrate")
    private Integer audioBitrate;

    @TableField("black_frame_ratio")
    private BigDecimal blackFrameRatio;                 // 黑屏帧占比 %

    @TableField("customer_face_ratio")
    private BigDecimal customerFaceRatio;               // 客户人脸在场率 %

    @TableField("third_party_count")
    private Integer thirdPartyCount;

    @TableField("location_lat")
    private BigDecimal locationLat;

    @TableField("location_lng")
    private BigDecimal locationLng;

    @TableField("ip_address")
    private String ipAddress;

    @TableField("device_fingerprint")
    private String deviceFingerprint;

    @TableField("encryption_iv")
    private String encryptionIv;

    @TableField("signed_hash")
    private String signedHash;

    @TableField("preservation_id")
    private String preservationId;

    @TableField("retention_notified_at")
    private LocalDateTime retentionNotifiedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("deleted")
    private Integer deleted;
}
