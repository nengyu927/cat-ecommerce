package com.catshop.dto.member;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "目前密碼不可為空")
        String currentPassword,

        @NotBlank(message = "新密碼不可為空")
        @Size(min = 8, max = 64, message = "新密碼長度需介於 8-64 字元")
        String newPassword
) {}
