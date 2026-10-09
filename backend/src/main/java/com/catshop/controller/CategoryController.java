package com.catshop.controller;

import com.catshop.common.ApiResponse;
import com.catshop.dto.category.CategoryResponse;
import com.catshop.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Tag(name = "Category", description = "商品分類")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @Operation(summary = "列出所有分類")
    public ApiResponse<List<CategoryResponse>> list() {
        return ApiResponse.ok(categoryService.tree());
    }

    @GetMapping("/tree")
    @Operation(summary = "取得分類樹")
    public ApiResponse<List<CategoryResponse>> tree() {
        return ApiResponse.ok(categoryService.tree());
    }

    @GetMapping("/slug/{slug}")
    @Operation(summary = "依 slug 取得分類")
    public ApiResponse<CategoryResponse> getBySlug(@PathVariable String slug) {
        return ApiResponse.ok(categoryService.getBySlug(slug));
    }

    @GetMapping("/{id}")
    public ApiResponse<CategoryResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(categoryService.getById(id));
    }
}
