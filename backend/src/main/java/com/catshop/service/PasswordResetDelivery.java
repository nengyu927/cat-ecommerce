package com.catshop.service;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.support.*;
@Component @RequiredArgsConstructor
public class PasswordResetDelivery {
 public record Mail(String email,String link) {}
 private final PasswordResetMailer mailer;
 @EventListener
 public void deliver(Mail mail){
  if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Reset mail requires a transaction");
  TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
   @Override public void afterCommit(){mailer.send(mail.email(),mail.link());}
  });
 }
}
