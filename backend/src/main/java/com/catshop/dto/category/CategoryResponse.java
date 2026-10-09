package com.catshop.dto.category;


import java.util.List;

public record CategoryResponse(
        Long id,
        String name,
        String slug,
        Long parentId,
        Integer sortOrder,
        Boolean isActive,
        List<CategoryResponse> children
) {}
