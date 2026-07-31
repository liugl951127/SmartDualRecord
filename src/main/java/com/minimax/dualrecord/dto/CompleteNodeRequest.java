package com.minimax.dualrecord.dto;

import com.minimax.dualrecord.domain.enums.RecordingNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 节点完成请求
 */
@Data
public class CompleteNodeRequest {
    @NotBlank
    private String businessId;
    @NotBlank
    private String recId;
    @NotNull
    private RecordingNode node;
    /** 节点 ASR 转写（用于禁播词扫描 + 关键节点肯定词识别） */
    @NotBlank
    private String asrText;
}
