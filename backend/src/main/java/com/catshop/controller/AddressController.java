package com.catshop.controller;

import com.catshop.common.ApiResponse;
import com.catshop.dto.address.AddressRequest;
import com.catshop.dto.address.AddressResponse;
import com.catshop.security.SecurityUtils;
import com.catshop.service.AddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/members/me/addresses")
@RequiredArgsConstructor
@Tag(name = "Address", description = "收件地址管理")
public class AddressController {

    private final AddressService addressService;

    @GetMapping
    @Operation(summary = "列出收件地址")
    public ApiResponse<List<AddressResponse>> list() {
        return ApiResponse.ok(addressService.list(SecurityUtils.requireMemberId()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "取得本人收件地址")
    public ApiResponse<AddressResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok(addressService.detail(SecurityUtils.requireMemberId(), id));
    }

    @PostMapping
    @Operation(summary = "新增收件地址")
    public ApiResponse<AddressResponse> create(@Valid @RequestBody AddressRequest req) {
        return ApiResponse.ok(addressService.create(SecurityUtils.requireMemberId(), req));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新收件地址")
    public ApiResponse<AddressResponse> update(@PathVariable Long id, @Valid @RequestBody AddressRequest req) {
        return ApiResponse.ok(addressService.update(SecurityUtils.requireMemberId(), id, req));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "刪除收件地址")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        addressService.delete(SecurityUtils.requireMemberId(), id);
        return ApiResponse.ok();
    }
}
