package com.catshop.dto.admin.category;


import java.time.LocalDateTime;

public record AdminCategoryResponse(
        Long id,
        String name,
        String slug,
        Long parentId,
        String parentName,
        Integer sortOrder,
        Boolean isActive,
        long productCount,
        LocalDateTime createdAt
) {}
