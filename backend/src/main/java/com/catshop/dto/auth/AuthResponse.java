package com.catshop.dto.auth;

import com.catshop.dto.member.MemberResponse;

public record AuthResponse(
        String token,
        String tokenType,
        long expiresInMs,
        MemberResponse member
) {
    public static AuthResponse of(String token, long expiresInMs, MemberResponse member) {
        return new AuthResponse(token, "Bearer", expiresInMs, member);
    }
}
