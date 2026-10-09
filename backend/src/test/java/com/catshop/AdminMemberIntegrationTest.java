package com.catshop;
import com.catshop.entity.*;
import com.catshop.repository.*;
import com.catshop.service.*;
import com.catshop.service.admin.AdminMemberService;
import com.catshop.dto.auth.LoginRequest;
import com.catshop.dto.admin.member.ChangeMemberStatusRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.profiles.active=test","spring.datasource.url=jdbc:h2:mem:adminmember;MODE=MSSQLServer;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
 "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver",
 "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop","spring.flyway.enabled=false",
 "app.auth.cleanup.enabled=false","app.auth.login-limit=1000","logging.level.org.hibernate.SQL=warn"})
@AutoConfigureMockMvc
class AdminMemberIntegrationTest {
 @Autowired MemberRepository members;
 @Autowired PasswordResetRepository resets;
 @Autowired AuthSessionRepository sessions;
 @Autowired AdminMemberService service;
 @Autowired AuthService auth;
 @Autowired AuthSessionService authSessions;
 @Autowired PasswordEncoder encoder;
 @Autowired MockMvc mvc;
 @SpyBean MemberStatusAuditRepository audits;
 Member admin,customer;
 @BeforeEach void setup(){
  reset(audits);audits.deleteAll();resets.deleteAll();sessions.deleteAll();members.deleteAll();
  admin=member("admin@member.test","Admin","ADMIN");customer=member("customer@member.test","Customer","CUSTOMER");
 }
 Member member(String email,String name,String role){return members.saveAndFlush(Member.builder().email(email).name(name).role(role).phone("0912345678").passwordHash(encoder.encode("password123")).build());}
 ChangeMemberStatusRequest change(String status,long version){return new ChangeMemberStatusRequest(status,version,"測試狀態管理");}
 String login(Member m){return "Bearer "+auth.login(new LoginRequest(m.getEmail(),"password123")).response().token();}
 @Test void filtersLiteralWildcardsStablePagingAndValidation(){
  member("literal@member.test","Literal%_[!","CUSTOMER");
  assertThat(service.list("%_[!",null,null,0,20).totalElements()).isEqualTo(1);
  assertThat(service.list(null,"ACTIVE","CUSTOMER",0,1).totalElements()).isEqualTo(2);
  var first=service.list(null,null,null,0,1);var second=service.list(null,null,null,1,1);
  assertThat(first.content().get(0).id()).isGreaterThan(second.content().get(0).id());
  assertThat(service.list("0912345678",null,null,0,20).totalElements()).isEqualTo(3);
  for(String invalid:List.of("UNKNOWN","%"))assertThatThrownBy(()->service.list(null,invalid,null,0,20)).isInstanceOf(com.catshop.exception.BusinessException.class);
  assertThatThrownBy(()->service.list("x".repeat(201),null,null,0,20)).isInstanceOf(com.catshop.exception.BusinessException.class);
 }
 @Test void disabledMemberLosesAccessRefreshAndResetButDataAndPasswordRemain() throws Exception {
  var logged=auth.login(new LoginRequest(customer.getEmail(),"password123"));
  var row=new PasswordReset();row.setMemberId(customer.getId());row.setTokenHash("a".repeat(64));row.setRequestedAt(LocalDateTime.now());row.setExpiresAt(LocalDateTime.now().plusMinutes(15));resets.saveAndFlush(row);
  var updated=service.changeStatus(customer.getId(),change("DISABLED",0),admin.getId());
  assertThat(updated.status()).isEqualTo("DISABLED");assertThat(updated.statusVersion()).isEqualTo(1);
  assertThat(resets.findById(customer.getId())).isEmpty();assertThat(members.count()).isEqualTo(2);
  assertThat(encoder.matches("password123",members.findById(customer.getId()).orElseThrow().getPasswordHash())).isTrue();
  mvc.perform(get("/api/members/me").header("Authorization","Bearer "+logged.response().token())).andExpect(status().isUnauthorized());
  assertThatThrownBy(()->authSessions.refresh(logged.refreshToken())).hasMessageContaining("停用");
  assertThatThrownBy(()->auth.login(new LoginRequest(customer.getEmail(),"password123"))).hasMessageContaining("停用");
  var history=service.history(customer.getId(),0,20);
  assertThat(history.totalElements()).isEqualTo(1);assertThat(history.content().get(0).actorMemberId()).isEqualTo(admin.getId());
 }
 @Test void reactivationDoesNotReviveOldSessionAndAuditIsAppendOnly() throws Exception {
  var logged=auth.login(new LoginRequest(customer.getEmail(),"password123"));
  service.changeStatus(customer.getId(),change("DISABLED",0),admin.getId());
  service.changeStatus(customer.getId(),change("ACTIVE",1),admin.getId());
  assertThatThrownBy(()->authSessions.refresh(logged.refreshToken())).hasMessageContaining("失效");
  mvc.perform(get("/api/members/me").header("Authorization","Bearer "+logged.response().token())).andExpect(status().isUnauthorized());
  assertThat(auth.login(new LoginRequest(customer.getEmail(),"password123")).response().member().id()).isEqualTo(customer.getId());
  assertThat(service.history(customer.getId(),0,1).content().get(0).toStatus()).isEqualTo("ACTIVE");
  assertThat(service.history(customer.getId(),1,1).content().get(0).toStatus()).isEqualTo("DISABLED");
 }
 @Test void protectsSelfLastAndOtherAdminsAndRejectsCustomerActor(){
  assertThatThrownBy(()->service.changeStatus(admin.getId(),change("DISABLED",0),admin.getId())).hasMessageContaining("受保護");
  var other=member("other@member.test","Other Admin","ADMIN");
  assertThatThrownBy(()->service.changeStatus(other.getId(),change("DISABLED",0),admin.getId())).hasMessageContaining("受保護");
  assertThatThrownBy(()->service.changeStatus(customer.getId(),change("DISABLED",0),customer.getId())).hasMessageContaining("僅限");
  assertThat(audits.count()).isZero();
 }
 @Test void staleVersionCannotOverwriteAnAbaStatusChangeAndNoopIsNotAudited(){
  service.changeStatus(customer.getId(),change("ACTIVE",0),admin.getId());assertThat(audits.count()).isZero();
  service.changeStatus(customer.getId(),change("DISABLED",0),admin.getId());service.changeStatus(customer.getId(),change("ACTIVE",1),admin.getId());
  assertThatThrownBy(()->service.changeStatus(customer.getId(),change("DISABLED",0),admin.getId())).hasMessageContaining("已變更");
  assertThat(members.findById(customer.getId()).orElseThrow().getStatusVersion()).isEqualTo(2);assertThat(audits.count()).isEqualTo(2);
 }
 @Test void auditFailureRollsBackStatusVersionAndSessionRevocation(){
  auth.login(new LoginRequest(customer.getEmail(),"password123"));
  doThrow(new IllegalStateException("audit failed")).when(audits).saveAndFlush(any(MemberStatusAudit.class));
  assertThatThrownBy(()->service.changeStatus(customer.getId(),change("DISABLED",0),admin.getId())).hasMessage("audit failed");
  var current=members.findById(customer.getId()).orElseThrow();
  assertThat(current.getStatus()).isEqualTo("ACTIVE");assertThat(current.getStatusVersion()).isZero();
  assertThat(sessions.findAll()).allSatisfy(s->assertThat(s.isRevoked()).isFalse());assertThat(audits.count()).isZero();
 }
 @Test void concurrentDisableAllowsOneChangeOnly() throws Exception {
  var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
  try {
   Callable<Boolean> task=()->{start.await();try{service.changeStatus(customer.getId(),change("DISABLED",0),admin.getId());return true;}catch(com.catshop.exception.BusinessException e){assertThat(e.getStatus().value()).isEqualTo(409);return false;}};
   var a=pool.submit(task);var b=pool.submit(task);start.countDown();
   assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
   assertThat(audits.count()).isEqualTo(1);
  }finally{start.countDown();pool.shutdownNow();}
 }
 @Test void adminOnlyDtosDoNotExposeCredentialsAndBadInputsAreRejected() throws Exception {
  String path="/api/admin/members",token=login(admin);
  mvc.perform(get(path)).andExpect(status().isUnauthorized());
  mvc.perform(get(path).header("Authorization",login(customer))).andExpect(status().isForbidden());
  var response=mvc.perform(get(path).header("Authorization",token)).andExpect(status().isOk()).andReturn();
  assertThat(response.getResponse().getContentAsString()).doesNotContain("passwordHash","password_hash","tokenHash","refreshToken");
  String detail=path+"/"+customer.getId();
  mvc.perform(get(detail)).andExpect(status().isUnauthorized());
  mvc.perform(get(detail).header("Authorization",login(customer))).andExpect(status().isForbidden());
  var single=mvc.perform(get(detail).header("Authorization",token)).andExpect(status().isOk())
   .andExpect(jsonPath("$.data.id").value(customer.getId())).andExpect(jsonPath("$.data.statusVersion").value(0)).andReturn();
  assertThat(single.getResponse().getContentAsString()).doesNotContain("passwordHash","password_hash","token","session","reset");
  mvc.perform(get(path+"/0").header("Authorization",token)).andExpect(status().isBadRequest());
  mvc.perform(get(path+"/9223372036854775807").header("Authorization",token)).andExpect(status().isNotFound());
  mvc.perform(get(path+"/9223372036854775808").header("Authorization",token)).andExpect(status().isBadRequest());
  assertThat(audits.count()).isZero();
  for(String[] pair:new String[][]{{"size","101"},{"page","-1"},{"role","OWNER"},{"status","UNKNOWN"}})
   mvc.perform(get(path).header("Authorization",token).param(pair[0],pair[1])).andExpect(status().isBadRequest());
  mvc.perform(patch(path+"/"+customer.getId()+"/status").header("Authorization",token).contentType("application/json").content("{\"status\":\"DISABLED\",\"version\":0,\"reason\":\" \"}")).andExpect(status().isBadRequest());
  mvc.perform(patch(path+"/"+admin.getId()+"/status").header("Authorization",token).contentType("application/json").content("{\"status\":\"DISABLED\",\"version\":0,\"reason\":\"test\"}")).andExpect(status().isForbidden());
  mvc.perform(get(path+"/0/history").header("Authorization",token)).andExpect(status().isBadRequest());
  mvc.perform(get(path+"/999999/history").header("Authorization",token)).andExpect(status().isNotFound());
 }
}
