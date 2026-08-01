package com.minimax.dualrecord.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.minimax.dualrecord.domain.UploadSession;
import com.minimax.dualrecord.repository.UploadSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 录像分片上传/断点续传服务 (v1.2 录像合规)
 *
 * 场景:
 *  - 网络异常/中断
 *  - 客户端崩溃
 *  - 浏览器关闭
 *  - 客户中途暂停 → 第二天继续
 *
 * 协议:
 *  1. initSession(businessId, totalChunks, chunkSize) → sessionId
 *  2. uploadChunk(sessionId, chunkIndex, data) → 已上传的 chunk 索引
 *  3. queryStatus(sessionId) → 进度
 *  4. finalizeUpload(sessionId) → Recording 实体
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordingResumableService {

    private final UploadSessionRepository sessionRepository;

    /** 默认 5MB/片 */
    public static final int DEFAULT_CHUNK_SIZE = 5 * 1024 * 1024;
    /** 默认 7 天过期 */
    public static final int DEFAULT_EXPIRY_DAYS = 7;

    /** 内存级 chunk 索引 (生产用 OSS/MinIO + Redis) */
    private final Set<String> inMemoryChunkKeys = ConcurrentHashMap.newKeySet();

    /**
     * 初始化上传 session
     */
    @Transactional(rollbackFor = Exception.class)
    public UploadSession initSession(String businessId, String channel,
                                      int totalChunks, long totalSizeBytes, Integer chunkSize) {
        String sessionId = "US" + System.currentTimeMillis() + "-"
                + HexFormat.of().formatHex(new byte[4]).substring(0, 6);
        UploadSession s = new UploadSession();
        s.setSessionId(sessionId);
        s.setBusinessId(businessId);
        s.setChannel(channel);
        s.setTotalChunks(totalChunks);
        s.setUploadedChunks(0);
        s.setChunkSize(chunkSize == null || chunkSize <= 0 ? DEFAULT_CHUNK_SIZE : chunkSize);
        s.setTotalSizeBytes(totalSizeBytes);
        s.setStatus("IN_PROGRESS");
        s.setStartedAt(LocalDateTime.now());
        s.setLastChunkAt(LocalDateTime.now());
        s.setExpiresAt(LocalDateTime.now().plusDays(DEFAULT_EXPIRY_DAYS));
        sessionRepository.insert(s);
        log.info("上传 session 初始化: {} ({} 片, 总 {} MB)", sessionId, totalChunks,
                totalSizeBytes / 1024 / 1024);
        return s;
    }

    /**
     * 上传 1 个分片
     */
    @Transactional(rollbackFor = Exception.class)
    public ChunkUploadResult uploadChunk(String sessionId, int chunkIndex, byte[] data) {
        UploadSession s = sessionRepository.selectOne(
                new QueryWrapper<UploadSession>().eq("session_id", sessionId));
        if (s == null) {
            throw new IllegalArgumentException("Session 不存在: " + sessionId);
        }
        if (!"IN_PROGRESS".equals(s.getStatus())) {
            throw new IllegalStateException("Session 状态非 IN_PROGRESS: " + s.getStatus());
        }
        if (s.getExpiresAt().isBefore(LocalDateTime.now())) {
            s.setStatus("EXPIRED");
            sessionRepository.updateById(s);
            throw new IllegalStateException("Session 已过期");
        }
        if (chunkIndex < 0 || chunkIndex >= s.getTotalChunks()) {
            throw new IllegalArgumentException("分片序号非法: " + chunkIndex);
        }
        // 写内存索引 (生产: 写 OSS/MinIO chunk 桶)
        String chunkKey = sessionId + "#" + chunkIndex;
        inMemoryChunkKeys.add(chunkKey);
        s.setUploadedChunks(s.getUploadedChunks() + 1);
        s.setLastChunkAt(LocalDateTime.now());
        sessionRepository.updateById(s);
        log.debug("分片上传: {} chunk={} ({} bytes)", sessionId, chunkIndex, data == null ? 0 : data.length);
        return new ChunkUploadResult(sessionId, chunkIndex, s.getUploadedChunks(),
                s.getTotalChunks(), isComplete(s));
    }

    /**
     * 查询进度
     */
    public UploadSession queryStatus(String sessionId) {
        return sessionRepository.selectOne(
                new QueryWrapper<UploadSession>().eq("session_id", sessionId));
    }

    /**
     * 完成上传, 转 tb_recording
     */
    @Transactional(rollbackFor = Exception.class)
    public String finalizeUpload(String sessionId) {
        UploadSession s = queryStatus(sessionId);
        if (s == null) {
            throw new IllegalArgumentException("Session 不存在: " + sessionId);
        }
        if (!isComplete(s)) {
            throw new IllegalStateException("分片未完整: " + s.getUploadedChunks() + "/" + s.getTotalChunks());
        }
        s.setStatus("COMPLETED");
        s.setCompletedAt(LocalDateTime.now());
        sessionRepository.updateById(s);

        // 实际: merge 所有分片 → 对象存储 → 写 tb_recording
        String recId = "REC" + System.currentTimeMillis() + "-FINALIZED";
        s.setRecId(recId);
        sessionRepository.updateById(s);
        log.info("上传完成: sessionId={} → recId={}", sessionId, recId);
        return recId;
    }

    /**
     * 列出会话
     */
    public List<UploadSession> listByBusinessId(String businessId) {
        return sessionRepository.selectList(
                new QueryWrapper<UploadSession>().eq("business_id", businessId));
    }

    private boolean isComplete(UploadSession s) {
        return s.getUploadedChunks() != null
                && s.getTotalChunks() != null
                && s.getUploadedChunks() >= s.getTotalChunks();
    }

    public record ChunkUploadResult(String sessionId, int chunkIndex, int uploaded,
                                    int total, boolean complete) {}
}
