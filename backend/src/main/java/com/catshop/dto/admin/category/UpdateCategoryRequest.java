package com.catshop.dto.admin.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateCategoryRequest(
        @NotBlank(message = "分類名稱不可為空") @Size(max = 100) String name,
        @NotBlank(message = "slug 不可為空")
        @Pattern(regexp = "^[a-z0-9-]+$", message = "slug 僅允許小寫英數字與連字號")
        @Size(max = 120) String slug,
        Long parentId,
        Integer sortOrder,
        Boolean isActive
) {}
