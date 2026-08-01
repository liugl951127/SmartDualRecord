package com.minimax.dualrecord.dto;

import lombok.Data;

/**
 * 客户签署文件请求 DTO
 */
@Data
public class SignFileRequest {
    /** 签字图片 base64 (PNG) */
    private String signatureData;
    /** 是否拒签 (true=拒签, false=签署) */
    private Boolean rejected = false;
    /** 拒签原因 (rejected=true 时必填) */
    private String rejectReason;
}
