package com.catshop.service;
public interface PasswordResetMailer {
 void ensureAvailable();
 void send(String email,String link);
}
