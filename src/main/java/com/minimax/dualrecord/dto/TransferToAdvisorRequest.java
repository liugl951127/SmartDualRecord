package com.minimax.dualrecord.dto;

import lombok.Data;

/**
 * H5 客户转接理财经理请求 DTO
 */
@Data
public class TransferToAdvisorRequest {
    private String businessId;
    private String customerName;
    private String customerMobile;
    private String reason;          // TECH_ISSUE / PRODUCT_QUESTION / COMPLIANCE_QUERY / OTHER
    private String description;
    private String preferredAdvisorId;  // 客户指定的理财经理 (可选)
}
