package com.catshop.service;
import com.catshop.entity.PasswordReset;
import com.catshop.repository.*;
import com.catshop.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.*;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
@Service @RequiredArgsConstructor
public class PasswordResetService {
 private final MemberRepository members;
 private final PasswordResetRepository resets;
 private final AuthSessionRepository sessions;
 private final PasswordEncoder encoder;
 private final PasswordResetMailer mailer;
 private final ApplicationEventPublisher events;
 @Value("${app.password-reset.frontend-url:http://127.0.0.1:5173}") private String frontendUrl;
 @Value("${app.password-reset.admin-url:http://127.0.0.1:5174}") private String adminUrl;
 private static final SecureRandom RANDOM=new SecureRandom();
 private LocalDateTime now(){return LocalDateTime.now(Clock.systemUTC());}
 private String hash(String token){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 private BusinessException invalid(){return BusinessException.badRequest("重設連結無效或已過期，請重新申請");}
 @Transactional
 public void request(String email) {
  request(email,"storefront");
 }
 @Transactional
 public void request(String email,String surface) {
  if(surface!=null && !Set.of("storefront","admin").contains(surface))throw BusinessException.badRequest("不支援的重設介面");
  String base="admin".equals(surface)?adminUrl:frontendUrl;
  mailer.ensureAvailable(); // Same disabled response for known and unknown accounts.
  var found=members.findByEmail(email.trim());
  if(found.isEmpty()) return;
  var member=members.lockForPasswordChange(found.get().getId()).orElseThrow();
  if(!"ACTIVE".equals(member.getStatus())) return;
  LocalDateTime time=now();
  var previous=resets.findById(member.getId());
  if(previous.isPresent() && previous.get().getRequestedAt().plusSeconds(60).isAfter(time)) return;
  var uri=java.net.URI.create(base);
  if(!Set.of("http","https").contains(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null)
   throw new IllegalStateException("Invalid password-reset frontend URL");
  byte[] secret=new byte[32];RANDOM.nextBytes(secret);
  String token=Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
  var reset=previous.orElseGet(PasswordReset::new);
  reset.setMemberId(member.getId());reset.setTokenHash(hash(token));reset.setRequestedAt(time);reset.setExpiresAt(time.plusMinutes(15));resets.saveAndFlush(reset);
  events.publishEvent(new PasswordResetDelivery.Mail(member.getEmail(),base.replaceAll("/+$","")+"/reset-password#token="+token));
 }
 @Transactional
 public void reset(String token,String newPassword){
  if(token==null || !token.matches("[A-Za-z0-9_-]{43}") || newPassword==null || newPassword.length()<8 || newPassword.length()>64)throw invalid();
  String digest=hash(token);
  var candidate=resets.findByTokenHash(digest).orElseThrow(this::invalid);
  var member=members.lockForPasswordChange(candidate.getMemberId()).orElseThrow(this::invalid);
  // Requery scalar state after the shared member lock: a concurrent reset/request may have won.
  var current=resets.currentState(member.getId()).orElseThrow(this::invalid);
  if(!digest.equals(current.getTokenHash()) || !current.getExpiresAt().isAfter(now()) || !"ACTIVE".equals(member.getStatus()))throw invalid();
  member.setPasswordHash(encoder.encode(newPassword));members.saveAndFlush(member);
  sessions.revokeMember(member.getId());resets.deleteById(member.getId());resets.flush();
 }
}
