package com.catshop.dto.order;
import java.time.LocalDateTime;
public record ShipmentResponse(String carrier, String trackingNumber, LocalDateTime shippedAt) {}
