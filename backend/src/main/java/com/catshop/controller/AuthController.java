package com.catshop.controller;

import com.catshop.common.ApiResponse;
import com.catshop.dto.auth.AuthResponse;
import com.catshop.dto.auth.LoginRequest;
import com.catshop.dto.auth.RegisterRequest;
import com.catshop.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "會員註冊與登入")
public class AuthController {

    private final AuthService authService;
    private final com.catshop.service.PasswordResetService passwordResets;
    private final com.catshop.service.AuthSessionService sessions;
    @org.springframework.beans.factory.annotation.Value("${app.auth.cookie-secure:false}")
    private boolean cookieSecure;
    @org.springframework.beans.factory.annotation.Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    private void checkOrigin(jakarta.servlet.http.HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if ("cross-site".equals(request.getHeader("Sec-Fetch-Site")))
            throw new com.catshop.exception.BusinessException("FORBIDDEN", "不允許跨站登入操作", org.springframework.http.HttpStatus.FORBIDDEN);
        String sameOrigin = request.getScheme() + "://" + request.getServerName()
                + ((request.getServerPort() == 80 || request.getServerPort() == 443) ? "" : ":" + request.getServerPort());
        if (origin != null && !origin.equals(sameOrigin)
                && java.util.Arrays.stream(allowedOrigins.split(",")).noneMatch(o -> o.trim().equals(origin)))
            throw new com.catshop.exception.BusinessException("FORBIDDEN", "不允許此登入來源", org.springframework.http.HttpStatus.FORBIDDEN);
    }
    private void cookie(jakarta.servlet.http.HttpServletResponse response, String token) {
        response.addHeader("Set-Cookie", org.springframework.http.ResponseCookie.from("catshop_refresh", token)
                .httpOnly(true).secure(cookieSecure).sameSite("Strict").path("/api/auth")
                .maxAge(token.isEmpty() ? 0 : 604800).build().toString());
        response.setHeader("Cache-Control", "no-store");
    }

    @PostMapping("/register")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    @Operation(summary = "會員註冊")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest req,
            jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response) {
        checkOrigin(request);
        var tokens = authService.register(req); cookie(response, tokens.refreshToken());
        return ApiResponse.ok(tokens.response());
    }

    @PostMapping("/login")
    @Operation(summary = "會員登入")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest req,
            jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response) {
        checkOrigin(request);
        var tokens = authService.login(req); cookie(response, tokens.refreshToken());
        return ApiResponse.ok(tokens.response());
    }

    @PostMapping("/forgot-password")
    public ApiResponse<String> forgotPassword(@Valid @RequestBody com.catshop.dto.auth.ForgotPasswordRequest req,
            jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response) {
        checkOrigin(request);response.setHeader("Cache-Control","no-store");
        passwordResets.request(req.email(),req.surface());
        return ApiResponse.ok("若此電子郵件對應可使用的帳號，將提供重設連結；請稍後查看信件。");
    }
    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody com.catshop.dto.auth.ResetPasswordRequest req,
            jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response) {
        checkOrigin(request);response.setHeader("Cache-Control","no-store");
        passwordResets.reset(req.token(),req.newPassword());cookie(response,"");
        return ApiResponse.ok(null);
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@CookieValue(name = "catshop_refresh", required = false) String token,
            jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response) {
        checkOrigin(request);
        try {
            var tokens = sessions.refresh(token); cookie(response, tokens.refreshToken());
            return ApiResponse.ok(tokens.response());
        } catch (com.catshop.exception.BusinessException e) { cookie(response, ""); throw e; }
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@CookieValue(name = "catshop_refresh", required = false) String token,
            jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response) {
        checkOrigin(request);
        try { sessions.logout(token); }
        finally { cookie(response, ""); }
        return ApiResponse.ok(null);
    }
}
