package com.minimax.dualrecord.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 产品话术模板请求 DTO
 *
 * 用于接收前端 JSON 数组 (List) / Map 字段
 * 在 controller 层转 ScriptTemplate 实体 (String 存 JSON)
 */
@Data
public class ScriptTemplateRequest {

    /** 模板 ID (更新时必填) */
    private String id;

    private String productId;
    private String productType;
    private String version;
    private String riskLevel;

    /** 必播项 (JSON 数组) */
    private List<String> mandatoryDisclosure;

    /** 禁播词 (JSON 数组) */
    private List<String> forbiddenPhrases;

    /** 必问问题 (JSON 数组) */
    private List<String> requiredQuestions;

    /** 渠道差分 (JSON object) */
    private Map<String, Object> channelOverrides;

    private String contentHash;
    private String status;
    private String approvedBy;
    private String approvedAt;
}
