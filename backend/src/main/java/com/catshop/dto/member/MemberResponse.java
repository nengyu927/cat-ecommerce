package com.catshop.dto.member;


import java.time.LocalDateTime;

public record MemberResponse(
        Long id,
        String email,
        String name,
        String phone,
        String role,
        String status,
        LocalDateTime createdAt
) {}
