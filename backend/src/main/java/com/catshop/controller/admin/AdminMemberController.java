package com.catshop.controller.admin;
import com.catshop.common.*;
import com.catshop.dto.admin.member.*;
import com.catshop.service.admin.AdminMemberService;
import com.catshop.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
@RestController @RequestMapping("/api/admin/members") @RequiredArgsConstructor @PreAuthorize("hasRole('ADMIN')")
public class AdminMemberController {
 private final AdminMemberService service;
 @GetMapping
 public ApiResponse<PageResult<AdminMemberResponse>> list(@RequestParam(required=false) String keyword,
  @RequestParam(required=false) String status,@RequestParam(required=false) String role,
  @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return ApiResponse.ok(service.list(keyword,status,role,page,size));}
 @GetMapping("/{id}/history")
 public ApiResponse<PageResult<MemberStatusAuditResponse>> history(@PathVariable Long id,
  @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return ApiResponse.ok(service.history(id,page,size));}
 @GetMapping("/{id}")
 public ApiResponse<AdminMemberResponse> detail(@PathVariable Long id){return ApiResponse.ok(service.detail(id));}
 @PatchMapping("/{id}/status")
 public ApiResponse<AdminMemberResponse> change(@PathVariable Long id,@Valid @RequestBody ChangeMemberStatusRequest req){
  return ApiResponse.ok(service.changeStatus(id,req,SecurityUtils.requireMemberId()));
 }
}
