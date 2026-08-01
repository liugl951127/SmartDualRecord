package com.minimax.dualrecord.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 录像分片上传 Session (v1.2 录像合规)
 *
 * 监管/客户要求: 录像中断后必须能断点续传
 *  - 网络异常
 *  - 客户端崩溃
 *  - 浏览器关闭
 *
 * 设计:
 *  - 默认每片 5MB
 *  - 7 天未完成自动 EXPIRED
 *  - 完成后自动转 tb_recording
 */
@Data
@NoArgsConstructor
@TableName("tb_upload_session")
public class UploadSession {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @TableField("session_id")
    private String sessionId;

    @TableField("business_id")
    private String businessId;

    @TableField("rec_id")
    private String recId;

    @TableField("channel")
    private String channel;

    @TableField("total_chunks")
    private Integer totalChunks;

    @TableField("uploaded_chunks")
    private Integer uploadedChunks;

    @TableField("chunk_size")
    private Integer chunkSize;

    @TableField("total_size_bytes")
    private Long totalSizeBytes;

    @TableField("status")
    private String status;  // IN_PROGRESS / COMPLETED / EXPIRED / FAILED

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("last_chunk_at")
    private LocalDateTime lastChunkAt;

    @TableField("completed_at")
    private LocalDateTime completedAt;

    @TableField("expires_at")
    private LocalDateTime expiresAt;
}
