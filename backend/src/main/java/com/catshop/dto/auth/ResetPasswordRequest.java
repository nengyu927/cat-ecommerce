package com.catshop.dto.auth;
import jakarta.validation.constraints.*;
public record ResetPasswordRequest(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}") String token,
 @NotBlank @Size(min=8,max=64,message="新密碼長度需介於 8-64 字元") String newPassword) {}
