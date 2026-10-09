package com.catshop.dto.address;


public record AddressResponse(
        Long id,
        String receiverName,
        String phone,
        String city,
        String district,
        String line1,
        String line2,
        String postalCode,
        Boolean isDefault
) {}
