package com.catshop.dto.admin.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateOrderStatusRequest(
        @NotBlank(message = "狀態不可為空") String status,
        @Size(max = 500) String remark
) {}
