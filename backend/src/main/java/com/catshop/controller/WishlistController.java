package com.catshop.controller;

import com.catshop.common.*;
import com.catshop.dto.wishlist.WishlistItemResponse;
import com.catshop.security.SecurityUtils;
import com.catshop.service.WishlistService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/wishlist") @RequiredArgsConstructor
@Tag(name = "Wishlist", description = "會員收藏商品")
public class WishlistController {
    private final WishlistService service;
    @GetMapping
    public ApiResponse<PageResult<WishlistItemResponse>> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "12") int size) {
        return ApiResponse.ok(service.list(SecurityUtils.requireMemberId(), page, size));
    }
    @GetMapping("/ids")
    public ApiResponse<List<Long>> ids() { return ApiResponse.ok(service.ids(SecurityUtils.requireMemberId())); }
    @PutMapping("/{productId}")
    public ApiResponse<List<Long>> add(@PathVariable Long productId) { return ApiResponse.ok(service.add(SecurityUtils.requireMemberId(), productId)); }
    @DeleteMapping("/{productId}")
    public ApiResponse<List<Long>> remove(@PathVariable Long productId) { return ApiResponse.ok(service.remove(SecurityUtils.requireMemberId(), productId)); }
}
