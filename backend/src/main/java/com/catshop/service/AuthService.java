package com.catshop.service;

import com.catshop.dto.auth.LoginRequest;
import com.catshop.dto.auth.RegisterRequest;
import com.catshop.entity.Member;
import com.catshop.exception.BusinessException;
import com.catshop.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthSessionService sessions;

    @Transactional
    public AuthSessionService.Tokens register(RegisterRequest req) {
        if (memberRepository.existsByEmail(req.email())) {
            throw BusinessException.conflict("此電子郵件已被註冊");
        }
        Member member = Member.builder()
                .email(req.email())
                .passwordHash(passwordEncoder.encode(req.password()))
                .name(req.name())
                .phone(req.phone())
                .role("CUSTOMER")
                .status("ACTIVE")
                .build();
        memberRepository.save(member);
        return sessions.create(member);
    }

    @Transactional
    public AuthSessionService.Tokens login(LoginRequest req) {
        Member member = memberRepository.lockForLogin(req.email())
                .orElseThrow(() -> BusinessException.unauthorized("電子郵件或密碼錯誤"));
        if (!passwordEncoder.matches(req.password(), member.getPasswordHash())) {
            throw BusinessException.unauthorized("電子郵件或密碼錯誤");
        }
        if (!"ACTIVE".equals(member.getStatus())) {
            throw BusinessException.unauthorized("帳號已被停用");
        }
        return sessions.create(member);
    }
}
