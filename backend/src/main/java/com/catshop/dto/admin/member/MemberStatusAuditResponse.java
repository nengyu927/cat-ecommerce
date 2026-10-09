package com.catshop.dto.admin.member;
import com.catshop.entity.MemberStatusAudit;
import java.time.LocalDateTime;
public record MemberStatusAuditResponse(Long id,Long memberId,Long actorMemberId,String fromStatus,String toStatus,
 Long statusVersion,String reason,LocalDateTime createdAt) {
 public static MemberStatusAuditResponse of(MemberStatusAudit a){return new MemberStatusAuditResponse(a.getId(),a.getMemberId(),a.getActorMemberId(),a.getFromStatus(),a.getToStatus(),a.getStatusVersion(),a.getReason(),a.getCreatedAt());}
}
