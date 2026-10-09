package com.catshop.dto.admin.order;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record CreateShipmentRequest(
    @NotBlank @Size(max=80) String carrier,
    @NotBlank @Size(max=100) String trackingNumber
) {}
