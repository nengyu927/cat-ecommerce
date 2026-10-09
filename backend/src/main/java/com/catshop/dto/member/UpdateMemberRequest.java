package com.catshop.dto.member;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateMemberRequest(
        @NotBlank(message = "姓名不可為空")
        @Size(max = 100, message = "姓名長度不可超過 100 字元")
        String name,

        @Size(max = 30, message = "電話長度不可超過 30 字元")
        String phone
) {}
