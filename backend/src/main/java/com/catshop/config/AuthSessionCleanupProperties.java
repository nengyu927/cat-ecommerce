package com.catshop.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.auth.cleanup")
public record AuthSessionCleanupProperties(
        @DefaultValue("200") @Min(1) @Max(1000) int batchSize,
        @DefaultValue("10") @Min(1) @Max(100) int maxBatches,
        @DefaultValue("3600000") @Min(60000) long delayMs,
        @DefaultValue("60000") @Min(1000) long initialDelayMs) {}
