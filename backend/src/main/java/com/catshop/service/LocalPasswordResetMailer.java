package com.catshop.service;
import com.catshop.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.UUID;
/** Operator-only development mailbox. Never served via HTTP. */
@Component
public class LocalPasswordResetMailer implements PasswordResetMailer {
 private final boolean enabled;
 private final Path directory;
 public LocalPasswordResetMailer(@Value("${app.password-reset.local-mail-enabled:false}") boolean enabled,
  @Value("${app.password-reset.mail-directory:.local/password-reset-mails}") String directory) {
  this.enabled=enabled;this.directory=Path.of(directory).toAbsolutePath().normalize();
 }
 private BusinessException unavailable(){return new BusinessException("MAIL_UNAVAILABLE","密碼重設郵件服務尚未啟用或暫時無法使用",HttpStatus.SERVICE_UNAVAILABLE);}
 public void ensureAvailable(){
  if(!enabled) throw unavailable();
  try {Files.createDirectories(directory);if(!Files.isWritable(directory))throw unavailable();}
  catch(IOException e){throw unavailable();}
 }
 public void send(String email,String link){
  ensureAvailable();
  try {Files.writeString(directory.resolve(UUID.randomUUID()+".txt"),"本機測試信件（未寄出真實郵件）\n收件人："+email+"\n密碼重設連結（15 分鐘內有效）：\n"+link+"\n請勿分享此檔案，使用後刪除。\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}
  catch(IOException e){throw unavailable();}
 }
}
