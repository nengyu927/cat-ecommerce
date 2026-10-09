package com.catshop.dto.address;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @NotBlank(message = "收件人不可為空") @Size(max = 100) String receiverName,
        @NotBlank(message = "電話不可為空") @Size(max = 30) String phone,
        @NotBlank(message = "城市不可為空") @Size(max = 50) String city,
        @Size(max = 50) String district,
        @NotBlank(message = "地址不可為空") @Size(max = 200) String line1,
        @Size(max = 200) String line2,
        @Size(max = 20) String postalCode,
        Boolean isDefault
) {}
