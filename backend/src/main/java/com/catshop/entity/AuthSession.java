package com.catshop.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "auth_session")
@Getter @Setter
public class AuthSession {
    @Id @Column(length = 36) private String id;
    @Column(name = "member_id", nullable = false) private Long memberId;
    @Column(name = "token_hash", nullable = false, length = 64) private String tokenHash;
    @Column(name = "previous_hash", length = 64) private String previousHash;
    @Column(name = "expires_at", nullable = false) private LocalDateTime expiresAt;
    @Column(nullable = false) private boolean revoked;
}
