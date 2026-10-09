package com.catshop.service;

import com.catshop.dto.auth.AuthResponse;
import com.catshop.dto.member.MemberResponse;
import com.catshop.entity.*;
import com.catshop.exception.BusinessException;
import com.catshop.repository.*;
import com.catshop.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class AuthSessionService {

    private final com.catshop.mapper.MemberMapper memberMapper;
    private final AuthSessionRepository sessions;
    private final MemberRepository members;
    private final JwtTokenProvider jwt;
    public record Tokens(AuthResponse response, String refreshToken) {}
    private static final SecureRandom RANDOM = new SecureRandom();
    private LocalDateTime now() { return LocalDateTime.now(Clock.systemUTC()); }
    private String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private Tokens rotate(AuthSession session, Member member) {
        byte[] secret = new byte[32]; RANDOM.nextBytes(secret);
        String refresh = session.getId() + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        session.setPreviousHash(session.getTokenHash());
        session.setTokenHash(hash(refresh));
        sessions.save(session);
        return new Tokens(AuthResponse.of(jwt.generateToken(member.getId(), member.getEmail(),
                member.getRole(), session.getId()), jwt.getExpirationMs(), memberMapper.member(member)), refresh);
    }
    @Transactional
    public Tokens create(Member member) {
        AuthSession session = new AuthSession();
        session.setId(UUID.randomUUID().toString()); session.setMemberId(member.getId());
        session.setExpiresAt(now().plusDays(7));
        return rotate(session, member);
    }
    // Replay revocation must commit even though the request is rejected.
    @Transactional(noRollbackFor = BusinessException.class)
    public Tokens refresh(String token) {
        if (token == null || !token.matches("[0-9a-f-]{36}\\.[A-Za-z0-9_-]{43}"))
            throw BusinessException.unauthorized("登入憑證不存在");
        var memberId = sessions.findMemberIdForCredentialLock(token.substring(0,36))
                .orElseThrow(() -> BusinessException.unauthorized("登入已失效"));
        // All credential writers lock member before session; avoids a refresh/reset lock-order cycle.
        Member member = members.lockForPasswordChange(memberId)
                .filter(m -> "ACTIVE".equals(m.getStatus()))
                .orElseThrow(() -> BusinessException.unauthorized("帳號已停用"));
        AuthSession session = lookup(token);
        if (session.isRevoked() || !session.getExpiresAt().isAfter(now()))
            throw BusinessException.unauthorized("登入已失效，請重新登入");
        if (!MessageDigest.isEqual(hash(token).getBytes(StandardCharsets.US_ASCII),
                session.getTokenHash().getBytes(StandardCharsets.US_ASCII))) {
            // Only a known consumed secret proves replay; an arbitrary forged secret must not revoke a session.
            if (hash(token).equals(session.getPreviousHash())) session.setRevoked(true);
            throw BusinessException.unauthorized("偵測到重複使用的登入憑證，請重新登入");
        }
        return rotate(session, member);
    }
    private AuthSession lookup(String token) {
        if (token == null || !token.matches("[0-9a-f-]{36}\\.[A-Za-z0-9_-]{43}"))
            throw BusinessException.unauthorized("登入憑證不存在");
        return sessions.lockById(token.substring(0, 36))
                .orElseThrow(() -> BusinessException.unauthorized("登入已失效"));
    }
    @Transactional
    public void logout(String token) {
        if (token == null) return;
        AuthSession session = lookup(token);
        if (MessageDigest.isEqual(hash(token).getBytes(StandardCharsets.US_ASCII),
                session.getTokenHash().getBytes(StandardCharsets.US_ASCII))
                || hash(token).equals(session.getPreviousHash())) session.setRevoked(true);
    }
    @Transactional(readOnly = true)
    public boolean active(String id, Long memberId) {
        return id != null && sessions.findById(id).filter(s -> s.getMemberId().equals(memberId)
                && !s.isRevoked() && s.getExpiresAt().isAfter(now())).isPresent();
    }
}
