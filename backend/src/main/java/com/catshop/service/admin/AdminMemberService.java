package com.catshop.service.admin;
import com.catshop.common.PageResult;
import com.catshop.dto.admin.member.*;
import com.catshop.entity.MemberStatusAudit;
import com.catshop.repository.*;
import com.catshop.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import java.util.*;
@Service @RequiredArgsConstructor
public class AdminMemberService {
 private final MemberRepository members;
 private final MemberStatusAuditRepository audits;
 private final AuthSessionRepository sessions;
 private final PasswordResetRepository resets;
 private void paging(int page,int size){if(page<0 || size<1 || size>100)throw BusinessException.badRequest("頁碼不可為負數，分頁大小須為 1 至 100");}
 private void id(Long id){if(id==null || id<1)throw BusinessException.badRequest("會員編號須為正整數");}
 private String filter(String value,Set<String> allowed){
  if(value==null || value.isBlank())return null;
  String cleaned=value.trim().toUpperCase(Locale.ROOT);
  if(!allowed.contains(cleaned))throw BusinessException.badRequest("不支援的會員篩選條件");
  return cleaned;
 }
 @Transactional(readOnly=true)
 public PageResult<AdminMemberResponse> list(String keyword,String status,String role,int page,int size){
  paging(page,size);
  if(keyword!=null && keyword.length()>200)throw BusinessException.badRequest("搜尋字詞不可超過 200 字");
  String term=keyword==null?"":keyword.trim();
  term=term.isEmpty()?null:term.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_").replace("[","![");
  return PageResult.of(members.adminSearch(term,filter(status,Set.of("ACTIVE","DISABLED")),filter(role,Set.of("CUSTOMER","ADMIN")),PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"id"))),AdminMemberResponse::of);
 }
 @Transactional(readOnly=true)
 public AdminMemberResponse detail(Long memberId){
  id(memberId);
  return AdminMemberResponse.of(members.findById(memberId).orElseThrow(()->BusinessException.notFound("找不到會員")));
 }
 @Transactional(readOnly=true)
 public PageResult<MemberStatusAuditResponse> history(Long memberId,int page,int size){
  id(memberId);paging(page,size);
  if(!members.existsById(memberId))throw BusinessException.notFound("找不到會員");
  return PageResult.of(audits.findByMemberId(memberId,PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"id"))),MemberStatusAuditResponse::of);
 }
 @Transactional
 public AdminMemberResponse changeStatus(Long memberId,ChangeMemberStatusRequest req,Long actorId){
  id(memberId);id(actorId);
  members.findById(actorId).filter(a->"ADMIN".equals(a.getRole()) && "ACTIVE".equals(a.getStatus()))
   .orElseThrow(()->new BusinessException("FORBIDDEN","僅限啟用中的管理員操作",HttpStatus.FORBIDDEN));
  var member=members.lockForPasswordChange(memberId).orElseThrow(()->BusinessException.notFound("找不到會員"));
  if(memberId.equals(actorId) || "ADMIN".equals(member.getRole()))
   throw new BusinessException("PROTECTED_MEMBER","管理員帳號受保護，不允許從會員管理變更狀態",HttpStatus.FORBIDDEN);
  if(req.version()==null || !Objects.equals(req.version(),member.getStatusVersion()))
   throw BusinessException.conflict("會員狀態已變更，請重新載入後再操作");
  if(req.status()==null || !Set.of("ACTIVE","DISABLED").contains(req.status()) || req.reason()==null || req.reason().isBlank() || req.reason().length()>200)
   throw BusinessException.badRequest("請提供有效狀態與 1 至 200 字的操作原因");
  if(req.status().equals(member.getStatus()))return AdminMemberResponse.of(member);
  if(member.getStatusVersion()==Long.MAX_VALUE)throw BusinessException.conflict("狀態版本已達上限");
  String from=member.getStatus();member.setStatus(req.status());member.setStatusVersion(member.getStatusVersion()+1);members.saveAndFlush(member);
  if("DISABLED".equals(req.status())){sessions.revokeMember(memberId);resets.deleteById(memberId);}
  audits.saveAndFlush(MemberStatusAudit.builder().memberId(memberId).actorMemberId(actorId).fromStatus(from)
   .toStatus(req.status()).statusVersion(member.getStatusVersion()).reason(req.reason().trim()).build());
  return AdminMemberResponse.of(member);
 }
}
