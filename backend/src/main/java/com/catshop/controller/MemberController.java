package com.catshop.controller;

import com.catshop.common.ApiResponse;
import com.catshop.dto.member.ChangePasswordRequest;
import com.catshop.dto.member.MemberResponse;
import com.catshop.dto.member.UpdateMemberRequest;
import com.catshop.security.SecurityUtils;
import com.catshop.service.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
@Tag(name = "Member", description = "會員中心")
public class MemberController {

    private final MemberService memberService;

    @GetMapping("/me")
    @Operation(summary = "取得目前會員資料")
    public ApiResponse<MemberResponse> me() {
        return ApiResponse.ok(memberService.getProfile(SecurityUtils.requireMemberId()));
    }

    @PutMapping("/me")
    @Operation(summary = "更新會員資料")
    public ApiResponse<MemberResponse> update(@Valid @RequestBody UpdateMemberRequest req) {
        return ApiResponse.ok(memberService.updateProfile(SecurityUtils.requireMemberId(), req));
    }

    @PutMapping("/me/password")
    @Operation(summary = "變更密碼")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        memberService.changePassword(SecurityUtils.requireMemberId(), req);
        return ApiResponse.ok();
    }
}
