package com.catshop.entity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Entity @Table(name="coupon") @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Coupon {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false,unique=true,length=32) private String code;
 @Column(name="discount_amount",nullable=false,precision=10,scale=2) private BigDecimal discountAmount;
 @Column(name="minimum_amount",nullable=false,precision=10,scale=2) private BigDecimal minimumAmount;
 @Column(name="starts_at",nullable=false) private LocalDateTime startsAt;
 @Column(name="ends_at",nullable=false) private LocalDateTime endsAt;
 @Column(name="max_uses",nullable=false) private Integer maxUses;
 @Column(nullable=false) private Boolean active;
 @Version @Column(nullable=false) private Integer version;
}
