package com.catshop.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "電子郵件不可為空")
        @Email(message = "電子郵件格式不正確")
        String email,

        @NotBlank(message = "密碼不可為空")
        @Size(min = 8, max = 64, message = "密碼長度需介於 8-64 字元")
        String password,

        @NotBlank(message = "姓名不可為空")
        @Size(max = 100, message = "姓名長度不可超過 100 字元")
        String name,

        @Size(max = 30, message = "電話長度不可超過 30 字元")
        String phone
) {}
