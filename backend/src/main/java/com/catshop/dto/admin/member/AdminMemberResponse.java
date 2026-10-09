package com.catshop.dto.admin.member;
import com.catshop.entity.Member;
import java.time.LocalDateTime;
public record AdminMemberResponse(Long id,String email,String name,String phone,String role,String status,
 Long statusVersion,LocalDateTime createdAt,LocalDateTime updatedAt) {
 public static AdminMemberResponse of(Member m){return new AdminMemberResponse(m.getId(),m.getEmail(),m.getName(),m.getPhone(),m.getRole(),m.getStatus(),m.getStatusVersion(),m.getCreatedAt(),m.getUpdatedAt());}
}
