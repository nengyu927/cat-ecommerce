package com.catshop.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity @Table(name="stock_movement") @Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class StockMovement {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="sku_id",nullable=false) private Long skuId;
 @Column(name="product_id",nullable=false) private Long productId;
 @Column(name="product_name",nullable=false,length=200) private String productName;
 @Column(name="sku_code",nullable=false,length=64) private String skuCode;
 @Column(nullable=false,length=30) private String reason;
 @Column(name="actor_member_id") private Long actorMemberId;
 @Column(name="before_stock",nullable=false) private Integer beforeStock;
 @Column(name="after_stock",nullable=false) private Integer afterStock;
 @Column(name="quantity_delta",nullable=false) private Integer quantityDelta;
 @Column(name="order_id") private Long orderId;
 @Column(name="order_no",length=32) private String orderNo;
 @CreationTimestamp @Column(name="created_at",nullable=false,updatable=false) private LocalDateTime createdAt;
}
