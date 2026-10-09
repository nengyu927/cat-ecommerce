package com.catshop;
import com.catshop.entity.*;
import com.catshop.repository.*;
import com.catshop.service.*;
import com.catshop.dto.auth.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.profiles.active=test","spring.datasource.url=jdbc:h2:mem:passwordreset;MODE=MSSQLServer;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
 "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver",
 "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop","spring.flyway.enabled=false",
 "app.auth.cleanup.enabled=false","app.auth.login-limit=1000","logging.level.org.hibernate.SQL=warn"})
@AutoConfigureMockMvc
class PasswordResetIntegrationTest {
 @Autowired MemberRepository members;
 @Autowired PasswordResetRepository resets;
 @Autowired AuthSessionRepository sessions;
 @Autowired PasswordResetService service;
 @Autowired AuthService auth;
 @Autowired AuthSessionService authSessions;
 @Autowired MemberService memberService;
 @Autowired PasswordEncoder encoder;
 @Autowired MockMvc mvc;
 @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
 @MockBean PasswordResetMailer mailer;
 Member member;
 final List<String> links=new CopyOnWriteArrayList<>();
 @BeforeEach void setup(){
  resets.deleteAll();sessions.deleteAll();members.deleteAll();reset(mailer);links.clear();
  doAnswer(i->{links.add(i.getArgument(1));return null;}).when(mailer).send(anyString(),anyString());
  member=members.saveAndFlush(Member.builder().email("reset@local.test").name("Reset").passwordHash(encoder.encode("password123")).build());
 }
 String token(){return links.get(links.size()-1).split("#token=")[1];}
 void age(){var row=resets.findById(member.getId()).orElseThrow();row.setRequestedAt(LocalDateTime.now(Clock.systemUTC()).minusMinutes(2));resets.saveAndFlush(row);}
 @Test void unknownDisabledAndKnownAccountsHaveSameMessageAndNoTokenInResponse() throws Exception {
  String message=null;
  for(String email:List.of("unknown@local.test",member.getEmail())){
   var response=mvc.perform(post("/api/auth/forgot-password").contentType("application/json").content("{\"email\":\""+email+"\"}")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn();
   var node=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getResponse().getContentAsString());
   if(message==null)message=node.path("data").asText();else assertThat(node.path("data").asText()).isEqualTo(message);
   assertThat(response.getResponse().getContentAsString()).doesNotContain("token=");
  }
  assertThat(links).hasSize(1);assertThat(resets.findById(member.getId()).orElseThrow().getTokenHash()).hasSize(64).isNotEqualTo(token());
  member.setStatus("DISABLED");members.saveAndFlush(member);age();service.request(member.getEmail());assertThat(links).hasSize(1);
 }
 @Test void cooldownAndReplacementInvalidateOldLink(){
  service.request(member.getEmail());String old=token();service.request(member.getEmail());assertThat(links).hasSize(1);
  age();service.request(member.getEmail());assertThat(links).hasSize(2);assertThat(token()).isNotEqualTo(old);
  assertThatThrownBy(()->service.reset(old,"new-password")).hasMessageContaining("無效");
  service.reset(token(),"new-password");assertThat(resets.count()).isZero();
 }
 @Test void resetRevokesAccessAndRefreshAndDoesNotAutomaticallyLogin(){
  var login=auth.login(new LoginRequest(member.getEmail(),"password123"));
  service.request(member.getEmail());String secret=token();service.reset(secret,"new-password");
  assertThatThrownBy(()->auth.login(new LoginRequest(member.getEmail(),"password123"))).hasMessageContaining("密碼錯誤");
  assertThatThrownBy(()->authSessions.refresh(login.refreshToken())).hasMessageContaining("失效");
  assertThat(sessions.findAll()).allSatisfy(s->assertThat(s.isRevoked()).isTrue());
  assertThatThrownBy(()->service.reset(secret,"other-password")).hasMessageContaining("無效");
  assertThat(auth.login(new LoginRequest(member.getEmail(),"new-password")).response().member().id()).isEqualTo(member.getId());
 }
 @Test void expiryAndInactiveAccountRejectWithoutChangingPassword(){
  service.request(member.getEmail());var row=resets.findById(member.getId()).orElseThrow();row.setExpiresAt(LocalDateTime.now(Clock.systemUTC()).minusSeconds(1));resets.saveAndFlush(row);
  assertThatThrownBy(()->service.reset(token(),"new-password")).hasMessageContaining("過期");
  assertThat(encoder.matches("password123",members.findById(member.getId()).orElseThrow().getPasswordHash())).isTrue();
  row.setExpiresAt(LocalDateTime.now(Clock.systemUTC()).plusMinutes(1));resets.saveAndFlush(row);member.setStatus("DISABLED");members.saveAndFlush(member);
  assertThatThrownBy(()->service.reset(token(),"new-password")).hasMessageContaining("無效");
 }
 @Test void ordinaryPasswordChangeInvalidatesOutstandingLink(){
  service.request(member.getEmail());String secret=token();
  memberService.changePassword(member.getId(),new com.catshop.dto.member.ChangePasswordRequest("password123","changed-password"));
  assertThat(resets.count()).isZero();assertThatThrownBy(()->service.reset(secret,"other-password")).hasMessageContaining("無效");
 }
 @Test void simultaneousUseAllowsExactlyOneReset() throws Exception {
  service.request(member.getEmail());String secret=token();
  var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
  try {
   Callable<Boolean> call=()->{start.await();try{service.reset(secret,"new-password");return true;}catch(com.catshop.exception.BusinessException e){return false;}};
   var a=pool.submit(call);var b=pool.submit(call);start.countDown();
   assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
   assertThat(resets.count()).isZero();
  }finally{start.countDown();pool.shutdownNow();}
 }
 @Test void originValidationPasswordValidationAndCookieRemoval() throws Exception {
  mvc.perform(post("/api/auth/forgot-password").header("Origin","https://evil.test").contentType("application/json").content("{\"email\":\"reset@local.test\"}")).andExpect(status().isForbidden());
  mvc.perform(post("/api/auth/forgot-password").contentType("application/json").content("{\"email\":\"invalid\"}")).andExpect(status().isBadRequest());
  service.request(member.getEmail());
  mvc.perform(post("/api/auth/reset-password").contentType("application/json").content("{\"token\":\""+token()+"\",\"newPassword\":\"short\"}")).andExpect(status().isBadRequest());
  mvc.perform(post("/api/auth/reset-password").header("Sec-Fetch-Site","cross-site").contentType("application/json").content("{\"token\":\""+token()+"\",\"newPassword\":\"new-password\"}")).andExpect(status().isForbidden());
  mvc.perform(post("/api/auth/reset-password").contentType("application/json").content("{\"token\":\""+token()+"\",\"newPassword\":\"new-password\"}")).andExpect(status().isOk()).andExpect(header().string("Set-Cookie",org.hamcrest.Matchers.containsString("Max-Age=0")));
 }
 @Test void disabledMailServiceFailsEquallyBeforeAccountLookup(){
  doThrow(new com.catshop.exception.BusinessException("MAIL_UNAVAILABLE","unavailable",org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)).when(mailer).ensureAvailable();
  for(String email:List.of(member.getEmail(),"unknown@local.test"))assertThatThrownBy(()->service.request(email)).hasMessage("unavailable");
  assertThat(resets.count()).isZero();assertThat(links).isEmpty();
 }
 @Test void rollbackNeverDeliversLink(){
  assertThatThrownBy(()->new org.springframework.transaction.support.TransactionTemplate(transactions).execute(status->{
   service.request(member.getEmail());assertThat(links).isEmpty();throw new IllegalStateException("rollback");
  })).hasMessage("rollback");
  assertThat(resets.count()).isZero();assertThat(links).isEmpty();
 }
 @Test void adminRecoveryUsesOnlyConfiguredAdminOriginAndRejectsArbitraryRedirect() throws Exception {
  mvc.perform(post("/api/auth/forgot-password").header("Host","evil.test").contentType("application/json").content("{\"email\":\"reset@local.test\",\"surface\":\"admin\"}")).andExpect(status().isOk());
  assertThat(links.get(0)).startsWith("http://127.0.0.1:5174/reset-password#token=");
  mvc.perform(post("/api/auth/forgot-password").contentType("application/json").content("{\"email\":\"reset@local.test\",\"surface\":\"https://evil.test\"}")).andExpect(status().isBadRequest());
  assertThat(links).hasSize(1);
 }
 @Test void postCommitMailboxFailureReturnsUnavailableButRetainsCommittedToken() throws Exception {
  doThrow(new com.catshop.exception.BusinessException("MAIL_UNAVAILABLE","mail unavailable",org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)).when(mailer).send(anyString(),anyString());
  mvc.perform(post("/api/auth/forgot-password").contentType("application/json").content("{\"email\":\"reset@local.test\"}")).andExpect(status().isServiceUnavailable());
  assertThat(resets.count()).isEqualTo(1);assertThat(links).isEmpty();
 }
 @Test void simultaneousProfileUpdateCannotRestoreOldPassword() throws Exception {
  service.request(member.getEmail());String secret=token();
  var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
  try {
   var profile=pool.submit(()->{start.await();return memberService.updateProfile(member.getId(),new com.catshop.dto.member.UpdateMemberRequest("Updated","0912345678"));});
   var password=pool.submit(()->{start.await();service.reset(secret,"new-password");return true;});
   start.countDown();profile.get(15,TimeUnit.SECONDS);assertThat(password.get(15,TimeUnit.SECONDS)).isTrue();
   var current=members.findById(member.getId()).orElseThrow();
   assertThat(current.getName()).isEqualTo("Updated");assertThat(encoder.matches("new-password",current.getPasswordHash())).isTrue();
  }finally{start.countDown();pool.shutdownNow();}
 }
}
