package com.minimax.dualrecord.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimax.dualrecord.domain.PushedFile;
import com.minimax.dualrecord.dto.PushFileRequest;
import com.minimax.dualrecord.dto.SignFileRequest;
import com.minimax.dualrecord.exception.BusinessException;
import com.minimax.dualrecord.repository.PushedFileRepository;
import com.minimax.dualrecord.websocket.RecordingWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 坐席推送文件服务 (v1.5)
 *
 * 业务流:
 *   1. 坐席选择文件 → POST /push
 *   2. 服务端落库 (status=PUSHED) + WebSocket 推送给客户
 *   3. 客户查看 → POST /view  (status=VIEWED) + WebSocket 反馈坐席
 *   4. 客户签署/拒签 → POST /sign|reject  (status=SIGNED|REJECTED) + WebSocket 反馈
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FilePushService {

    private final PushedFileRepository pushedFileRepository;
    private final RecordingWebSocketHandler webSocketHandler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 坐席推送文件
     */
    @Transactional(rollbackFor = Exception.class)
    public PushedFile pushFile(PushFileRequest req, String pushedBy) {
        if (req.getBusinessId() == null || req.getBusinessId().isEmpty()) {
            throw new BusinessException("INVALID_BUSINESS_ID", "业务 ID 不能为空");
        }
        if (req.getFileName() == null || req.getFileName().isEmpty()) {
            throw new BusinessException("INVALID_FILE_NAME", "文件名不能为空");
        }
        if (req.getFileUrl() == null || req.getFileUrl().isEmpty()) {
            throw new BusinessException("INVALID_FILE_URL", "文件 URL 不能为空");
        }
        PushedFile file = new PushedFile();
        file.setBusinessId(req.getBusinessId());
        file.setFileId(UUID.randomUUID().toString().replace("-", ""));
        file.setFileName(req.getFileName());
        file.setFileType(req.getFileType() != null ? req.getFileType() : "PDF");
        file.setFileUrl(req.getFileUrl());
        file.setFileSize(req.getFileSize());
        file.setFileCategory(req.getFileCategory() != null ? req.getFileCategory() : "OTHER");
        file.setPushedBy(pushedBy);
        file.setPushedAt(LocalDateTime.now());
        file.setStatus("PUSHED");
        file.setDeleted(0);
        if (req.getRemark() != null) file.setRemark(req.getRemark());
        pushedFileRepository.insert(file);

        // WebSocket 推送通知
        pushToClient(file, "FILE_PUSHED");
        log.info("坐席推送文件: businessId={}, fileName={}, category={}, by={}",
                req.getBusinessId(), req.getFileName(), req.getFileCategory(), pushedBy);
        return file;
    }

    /**
     * 客户查看文件
     */
    @Transactional(rollbackFor = Exception.class)
    public PushedFile markViewed(String fileId) {
        PushedFile file = getByFileId(fileId);
        if ("PUSHED".equals(file.getStatus()) || file.getViewedAt() == null) {
            file.setStatus("VIEWED");
            file.setViewedAt(LocalDateTime.now());
            pushedFileRepository.updateById(file);
            pushToClient(file, "FILE_VIEWED");
        }
        return file;
    }

    /**
     * 客户签署文件
     */
    @Transactional(rollbackFor = Exception.class)
    public PushedFile signFile(String fileId, SignFileRequest req) {
        PushedFile file = getByFileId(fileId);
        if (Boolean.TRUE.equals(req.getRejected())) {
            file.setStatus("REJECTED");
            file.setRejectedAt(LocalDateTime.now());
            file.setRemark((file.getRemark() != null ? file.getRemark() + " | " : "")
                    + "拒签: " + req.getRejectReason());
        } else {
            file.setStatus("SIGNED");
            file.setSignedAt(LocalDateTime.now());
            file.setSignatureData(req.getSignatureData());
        }
        pushedFileRepository.updateById(file);
        pushToClient(file, Boolean.TRUE.equals(req.getRejected()) ? "FILE_REJECTED" : "FILE_SIGNED");
        log.info("客户{}文件: fileId={}", Boolean.TRUE.equals(req.getRejected()) ? "拒签" : "签署", fileId);
        return file;
    }

    /**
     * 查询业务的所有推送文件
     */
    public List<PushedFile> listByBusiness(String businessId) {
        return pushedFileRepository.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PushedFile>()
                        .eq("business_id", businessId)
                        .orderByDesc("pushed_at"));
    }

    /**
     * 查询单个文件
     */
    public PushedFile getByFileId(String fileId) {
        PushedFile file = pushedFileRepository.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PushedFile>()
                        .eq("file_id", fileId));
        if (file == null) {
            throw new BusinessException("FILE_NOT_FOUND", "文件不存在: " + fileId);
        }
        return file;
    }

    /**
     * 推送 WebSocket 通知给客户/坐席
     */
    private void pushToClient(PushedFile file, String type) {
        try {
            Map<String, Object> msg = new HashMap<>();
            msg.put("type", type);
            msg.put("businessId", file.getBusinessId());
            msg.put("fileId", file.getFileId());
            msg.put("fileName", file.getFileName());
            msg.put("fileType", file.getFileType());
            msg.put("fileUrl", file.getFileUrl());
            msg.put("fileSize", file.getFileSize());
            msg.put("fileCategory", file.getFileCategory());
            msg.put("status", file.getStatus());
            msg.put("pushedAt", file.getPushedAt() != null ? file.getPushedAt().toString() : null);
            msg.put("viewedAt", file.getViewedAt() != null ? file.getViewedAt().toString() : null);
            msg.put("signedAt", file.getSignedAt() != null ? file.getSignedAt().toString() : null);
            String json = objectMapper.writeValueAsString(msg);
            webSocketHandler.broadcast(file.getBusinessId(), json);
        } catch (Exception e) {
            log.warn("WebSocket 推送失败: fileId={}, type={}", file.getFileId(), type, e);
        }
    }
}
