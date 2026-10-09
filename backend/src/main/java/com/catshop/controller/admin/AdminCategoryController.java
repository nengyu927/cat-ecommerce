package com.catshop.controller.admin;

import com.catshop.common.ApiResponse;
import com.catshop.common.PageResult;
import com.catshop.dto.admin.category.AdminCategoryResponse;
import com.catshop.dto.admin.category.CreateCategoryRequest;
import com.catshop.dto.admin.category.UpdateCategoryRequest;
import com.catshop.service.admin.AdminCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/categories")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin · Category", description = "後台分類管理（ADMIN）")
public class AdminCategoryController {

    private final AdminCategoryService adminCategoryService;

    @GetMapping
    @Operation(summary = "AC1 後台分類列表（含停用、含商品數）")
    public ApiResponse<PageResult<AdminCategoryResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(adminCategoryService.list(keyword, isActive, page, size));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "AC2 新增分類")
    public ApiResponse<AdminCategoryResponse> create(@Valid @RequestBody CreateCategoryRequest req) {
        return ApiResponse.ok(adminCategoryService.create(req));
    }

    @PutMapping("/{id}")
    @Operation(summary = "AC3 更新分類")
    public ApiResponse<AdminCategoryResponse> update(@PathVariable Long id,
                                                     @Valid @RequestBody UpdateCategoryRequest req) {
        return ApiResponse.ok(adminCategoryService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "AC4 停用分類（軟刪除）")
    public ApiResponse<Void> deactivate(@PathVariable Long id) {
        adminCategoryService.deactivate(id);
        return ApiResponse.ok();
    }
}
