package com.catshop.controller;

import com.catshop.common.ApiResponse;
import com.catshop.dto.cart.AddCartItemRequest;
import com.catshop.dto.cart.CartResponse;
import com.catshop.dto.cart.UpdateCartItemRequest;
import com.catshop.security.SecurityUtils;
import com.catshop.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
@Tag(name = "Cart", description = "購物車")
public class CartController {

    private final CartService cartService;

    @GetMapping
    @Operation(summary = "取得購物車")
    public ApiResponse<CartResponse> getCart() {
        return ApiResponse.ok(cartService.getCart(SecurityUtils.requireMemberId()));
    }

    @PostMapping("/items")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    @Operation(summary = "加入商品至購物車")
    public ApiResponse<CartResponse> addItem(@Valid @RequestBody AddCartItemRequest req) {
        return ApiResponse.ok(cartService.addItem(SecurityUtils.requireMemberId(), req));
    }

    @RequestMapping(value = "/items/{itemId}", method = {RequestMethod.PATCH, RequestMethod.PUT})
    @Operation(summary = "更新購物車項目數量")
    public ApiResponse<CartResponse> updateItem(@PathVariable Long itemId,
                                                @Valid @RequestBody UpdateCartItemRequest req) {
        return ApiResponse.ok(cartService.updateItem(SecurityUtils.requireMemberId(), itemId, req));
    }

    @DeleteMapping("/items/{itemId}")
    @Operation(summary = "移除購物車項目")
    public ApiResponse<CartResponse> removeItem(@PathVariable Long itemId) {
        return ApiResponse.ok(cartService.removeItem(SecurityUtils.requireMemberId(), itemId));
    }

    @DeleteMapping
    @Operation(summary = "清空購物車")
    public ApiResponse<Void> clear() {
        cartService.clear(SecurityUtils.requireMemberId());
        return ApiResponse.ok();
    }
}
