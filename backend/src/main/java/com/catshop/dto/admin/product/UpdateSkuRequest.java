package com.catshop.dto.admin.product;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record UpdateSkuRequest(
        @NotBlank(message = "規格名稱不可為空") @Size(max = 200) String specName,
        @NotNull(message = "價格不可為空") @DecimalMin(value = "0", message = "價格不可為負") BigDecimal price,
        @Min(value = 0, message = "庫存不可為負") Integer stock,
        @Min(value = 0, message = "安全庫存不可為負") Integer safetyStock,
        Boolean isActive,
        @NotNull(message = "請提供目前規格版本") Integer version
) {}
