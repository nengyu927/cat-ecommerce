package com.catshop.controller;

import com.catshop.common.ApiResponse;
import com.catshop.common.PageResult;
import com.catshop.dto.order.CreateOrderRequest;
import com.catshop.dto.order.OrderResponse;
import com.catshop.security.SecurityUtils;
import com.catshop.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Order", description = "訂單與結帳")
public class OrderController {

    private final OrderService orderService;
    private final com.catshop.service.ShipmentService shipmentService;
    private final com.catshop.service.CouponService coupons;

    @GetMapping("/request-identity")
    @Operation(summary = "下單識別碼契約版本；前端送出前確認後端已更新")
    public ApiResponse<java.util.Map<String, Integer>> requestIdentity() {
        return ApiResponse.ok(java.util.Map.of("version", 3));
    }

    @GetMapping("/checkout-quote")
    @Operation(summary = "無優惠券的結帳報價；不保留價格或庫存")
    public ApiResponse<com.catshop.dto.order.CouponQuoteResponse> checkoutQuote() {
        return ApiResponse.ok(coupons.quote(SecurityUtils.requireMemberId(),null));
    }

    @PostMapping("/quote")
    public ApiResponse<com.catshop.dto.order.CouponQuoteResponse> quote(
            @Valid @RequestBody com.catshop.dto.order.CouponQuoteRequest request) {
        return ApiResponse.ok(coupons.quote(SecurityUtils.requireMemberId(),request.code()));
    }

    @GetMapping("/{id}/shipment")
    public ApiResponse<com.catshop.dto.order.ShipmentResponse> shipment(@PathVariable Long id) {
        return ApiResponse.ok(shipmentService.get(id, SecurityUtils.requireMemberId()));
    }

    @PostMapping
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    @Operation(summary = "結帳：由購物車建立訂單")
    public ApiResponse<OrderResponse> create(@Valid @RequestBody CreateOrderRequest req) {
        return ApiResponse.ok(orderService.createOrder(SecurityUtils.requireMemberId(), req));
    }

    @GetMapping
    @Operation(summary = "我的訂單列表")
    public ApiResponse<PageResult<OrderResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(orderService.listMyOrders(SecurityUtils.requireMemberId(), page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "訂單詳情")
    public ApiResponse<OrderResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok(orderService.getMyOrder(SecurityUtils.requireMemberId(), id));
    }

    @GetMapping("/requests/{requestId}")
    @Operation(summary = "以本會員的下單識別碼查回已成立訂單")
    public ApiResponse<OrderResponse> byRequestId(@PathVariable String requestId) {
        return ApiResponse.ok(orderService.getByRequestId(SecurityUtils.requireMemberId(), requestId));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "取消訂單")
    public ApiResponse<OrderResponse> cancel(@PathVariable Long id) {
        return ApiResponse.ok(orderService.cancelMyOrder(SecurityUtils.requireMemberId(), id));
    }
}
