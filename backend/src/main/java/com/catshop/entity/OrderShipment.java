package com.catshop.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name="order_shipment") @Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class OrderShipment {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="order_id",nullable=false,unique=true) private Long orderId;
    @Column(nullable=false,length=80) private String carrier;
    @Column(name="tracking_number",nullable=false,length=100) private String trackingNumber;
    @Column(name="actor_member_id",nullable=false) private Long actorMemberId;
    @Column(name="shipped_at",nullable=false) private LocalDateTime shippedAt;
}
