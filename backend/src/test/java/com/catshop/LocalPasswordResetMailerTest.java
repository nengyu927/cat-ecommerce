package com.catshop;
import com.catshop.service.LocalPasswordResetMailer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;
class LocalPasswordResetMailerTest {
 @TempDir Path temp;
 @Test void localMailboxWritesPrivateOperatorFileWithoutNetwork() throws Exception {
  var mailer=new LocalPasswordResetMailer(true,temp.resolve("mail").toString());
  mailer.send("member@example.com","http://127.0.0.1:5173/reset-password#token=test");
  try(var files=Files.list(temp.resolve("mail"))){
   var list=files.toList();assertThat(list).hasSize(1);
   assertThat(Files.readString(list.get(0))).contains("未寄出真實郵件","member@example.com","reset-password#token=test");
  }
 }
 @Test void disabledMailboxCreatesNoFiles(){
  var mailer=new LocalPasswordResetMailer(false,temp.resolve("disabled").toString());
  assertThatThrownBy(mailer::ensureAvailable).hasMessageContaining("尚未啟用");
  assertThat(Files.exists(temp.resolve("disabled"))).isFalse();
 }
}
