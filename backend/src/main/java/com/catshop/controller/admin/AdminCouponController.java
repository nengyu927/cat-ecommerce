package com.catshop.controller.admin;
import com.catshop.common.*;
import com.catshop.dto.admin.CouponResponse;
import com.catshop.dto.admin.CouponRequest;
import com.catshop.service.CouponService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
@RestController @RequestMapping("/api/admin/coupons") @RequiredArgsConstructor @PreAuthorize("hasRole('ADMIN')")
public class AdminCouponController {
 private final CouponService service;
 @GetMapping public ApiResponse<PageResult<CouponResponse>> list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {return ApiResponse.ok(service.list(page,size));}
 @GetMapping("/{id}") public ApiResponse<CouponResponse> detail(@PathVariable Long id) {return ApiResponse.ok(service.detail(id));}
 @PostMapping @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
 public ApiResponse<CouponResponse> create(@Valid @RequestBody CouponRequest request) {return ApiResponse.ok(service.save(null,request));}
 @PutMapping("/{id}") public ApiResponse<CouponResponse> update(@PathVariable Long id,@Valid @RequestBody CouponRequest request) {return ApiResponse.ok(service.save(id,request));}
}
