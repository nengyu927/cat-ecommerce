package com.catshop.dto.admin.member;
import jakarta.validation.constraints.*;
public record ChangeMemberStatusRequest(
 @NotNull @Pattern(regexp="ACTIVE|DISABLED") String status,
 @NotNull @Min(0) Long version,
 @NotBlank @Size(max=200) String reason) {}
