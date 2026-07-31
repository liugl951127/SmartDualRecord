package com.minimax.dualrecord.dto;

import com.minimax.dualrecord.domain.enums.BusinessType;
import com.minimax.dualrecord.domain.enums.Channel;
import com.minimax.dualrecord.domain.enums.SellerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 创建业务请求
 */
@Data
public class StartBusinessRequest {
    @NotNull
    private BusinessType businessType;
    @NotBlank
    private String productId;
    @NotBlank
    private String customerIdHash;
    private String sellerIdHash;
    @NotNull
    private Channel channel;
    @NotNull
    private SellerType sellerType;
    private BigDecimal amount;
}
