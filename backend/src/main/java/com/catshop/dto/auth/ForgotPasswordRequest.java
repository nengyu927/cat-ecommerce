package com.catshop.dto.auth;
import jakarta.validation.constraints.*;
public record ForgotPasswordRequest(@NotBlank @Email @Size(max=200) String email,
 @Pattern(regexp="storefront|admin") String surface) {}
