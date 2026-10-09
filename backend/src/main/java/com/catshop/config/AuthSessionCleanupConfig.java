package com.catshop.config;

import com.catshop.service.AuthSessionCleanupService;
import com.catshop.scheduling.AuthSessionCleanupJob;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(AuthSessionCleanupProperties.class)
@ConditionalOnProperty(name = "app.auth.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class AuthSessionCleanupConfig {
    @Bean
    AuthSessionCleanupJob authSessionCleanupJob(AuthSessionCleanupService service, AuthSessionCleanupProperties properties) {
        return new AuthSessionCleanupJob(service, properties);
    }
}
