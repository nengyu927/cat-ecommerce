package com.catshop.dto.admin.product;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record UpdateProductRequest(
        @NotBlank(message = "商品名稱不可為空") @Size(max = 200) String name,
        @NotBlank(message = "slug 不可為空")
        @Pattern(regexp = "^[a-z0-9-]+$", message = "slug 僅允許小寫英數字與連字號")
        @Size(max = 220) String slug,
        String description,
        @Size(max = 10000) String ingredients,
        @Size(max = 100) String suitableAge,
        @Size(max = 100) String origin,
        @Size(max = 500) String storageInstructions,
        @NotNull(message = "分類不可為空") Long categoryId,
        @Size(max = 100) String brand,
        @NotNull(message = "售價不可為空") @DecimalMin(value = "0", message = "售價不可為負") BigDecimal basePrice,
        String status,
        Boolean isActive,
        @jakarta.validation.Valid java.util.List<CreateProductRequest.ImageItem> images
) {}
