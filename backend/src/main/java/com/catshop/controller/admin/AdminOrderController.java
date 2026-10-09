package com.catshop.controller.admin;

import com.catshop.common.ApiResponse;
import com.catshop.common.PageResult;
import com.catshop.dto.admin.order.AdminOrderDetailResponse;
import com.catshop.dto.admin.order.AdminOrderSummaryResponse;
import com.catshop.dto.admin.order.UpdateOrderStatusRequest;
import com.catshop.service.admin.AdminOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin · Order", description = "後台訂單管理（ADMIN）")
public class AdminOrderController {

    private final AdminOrderService adminOrderService;
    private final com.catshop.service.ShipmentService shipmentService;

    @GetMapping("/{id}/shipment")
    public ApiResponse<com.catshop.dto.order.ShipmentResponse> shipment(@PathVariable Long id) {
        return ApiResponse.ok(shipmentService.get(id, null));
    }

    @PostMapping("/{id}/shipment")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public ApiResponse<com.catshop.dto.order.ShipmentResponse> ship(@PathVariable Long id,
            @Valid @RequestBody com.catshop.dto.admin.order.CreateShipmentRequest request) {
        return ApiResponse.ok(shipmentService.ship(id, com.catshop.security.SecurityUtils.requireMemberId(), request));
    }

    @GetMapping
    @Operation(summary = "AO1 後台訂單列表（依狀態／會員／日期篩選）")
    public ApiResponse<PageResult<AdminOrderSummaryResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return ApiResponse.ok(adminOrderService.list(status, memberId, orderNo, from, to, page, size, sort));
    }

    @GetMapping("/{id}")
    @Operation(summary = "AO2 後台訂單詳情（含明細、收件快照、會員資訊）")
    public ApiResponse<AdminOrderDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok(adminOrderService.detail(id));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "AO3 更新訂單狀態（出貨／完成／取消）")
    public ApiResponse<AdminOrderDetailResponse> updateStatus(@PathVariable Long id,
                                                              @Valid @RequestBody UpdateOrderStatusRequest req) {
        return ApiResponse.ok(adminOrderService.updateStatus(id, req));
    }
}
