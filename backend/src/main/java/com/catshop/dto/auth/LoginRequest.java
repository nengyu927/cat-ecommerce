package com.catshop.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "電子郵件不可為空")
        @Email(message = "電子郵件格式不正確")
        String email,

        @NotBlank(message = "密碼不可為空")
        String password
) {}
