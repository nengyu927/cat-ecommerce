package com.catshop.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
@Entity @Table(name="password_reset") @Getter @Setter @NoArgsConstructor
public class PasswordReset {
 @Id @Column(name="member_id") private Long memberId;
 @Column(name="token_hash",nullable=false,length=64,unique=true) private String tokenHash;
 @Column(name="requested_at",nullable=false) private LocalDateTime requestedAt;
 @Column(name="expires_at",nullable=false) private LocalDateTime expiresAt;
}
