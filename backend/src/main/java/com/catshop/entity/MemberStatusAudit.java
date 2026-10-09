package com.catshop.entity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
@Entity @Table(name="member_status_audit") @Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class MemberStatusAudit {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="member_id",nullable=false) private Long memberId;
 @Column(name="actor_member_id",nullable=false) private Long actorMemberId;
 @Column(name="from_status",nullable=false,length=20) private String fromStatus;
 @Column(name="to_status",nullable=false,length=20) private String toStatus;
 @Column(name="status_version",nullable=false) private Long statusVersion;
 @Column(nullable=false,length=200) private String reason;
 @CreationTimestamp @Column(name="created_at",nullable=false,updatable=false) private LocalDateTime createdAt;
}
