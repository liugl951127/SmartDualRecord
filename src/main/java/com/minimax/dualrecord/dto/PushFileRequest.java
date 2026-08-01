package com.minimax.dualrecord.dto;

import lombok.Data;

/**
 * 推送文件请求 DTO
 */
@Data
public class PushFileRequest {
    private String businessId;
    private String fileName;
    private String fileType;       // PDF / PNG / JPG / MP4 / TXT
    private String fileCategory;   // BROCHURE / DISCLOSURE / CONTRACT / ID_CARD / OTHER
    private String fileUrl;        // 已上传的文件 URL (CDN / 静态资源)
    private Long fileSize;         // 字节
    private String remark;
}
