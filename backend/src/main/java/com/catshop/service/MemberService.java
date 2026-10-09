package com.catshop.service;

import com.catshop.dto.member.ChangePasswordRequest;
import com.catshop.dto.member.MemberResponse;
import com.catshop.dto.member.UpdateMemberRequest;
import com.catshop.entity.Member;
import com.catshop.exception.BusinessException;
import com.catshop.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final com.catshop.mapper.MemberMapper memberMapper;

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.catshop.repository.AuthSessionRepository sessions;
    private final com.catshop.repository.PasswordResetRepository resets;

    @Transactional(readOnly = true)
    public MemberResponse getProfile(Long memberId) {
        return memberMapper.member(find(memberId));
    }

    @Transactional
    public MemberResponse updateProfile(Long memberId, UpdateMemberRequest req) {
        // Serialize whole-row profile writes with password changes to avoid restoring a stale password hash.
        Member member = memberRepository.lockForPasswordChange(memberId).orElseThrow(() -> BusinessException.notFound("找不到會員"));
        member.setName(req.name());
        member.setPhone(req.phone());
        return memberMapper.member(memberRepository.save(member));
    }

    @Transactional
    public void changePassword(Long memberId, ChangePasswordRequest req) {
        Member member = memberRepository.lockForPasswordChange(memberId).orElseThrow(() -> BusinessException.notFound("找不到會員"));
        if (!passwordEncoder.matches(req.currentPassword(), member.getPasswordHash())) {
            throw BusinessException.badRequest("目前密碼不正確");
        }
        member.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        memberRepository.save(member);
        sessions.revokeMember(memberId);
        resets.deleteById(memberId);
    }

    private Member find(Long id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("找不到會員"));
    }
}
