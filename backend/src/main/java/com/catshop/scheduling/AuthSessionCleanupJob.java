package com.catshop.scheduling;

import com.catshop.config.AuthSessionCleanupProperties;
import com.catshop.service.AuthSessionCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.Clock;
import java.time.LocalDateTime;

@Slf4j
@RequiredArgsConstructor
public class AuthSessionCleanupJob {
    private final AuthSessionCleanupService cleanup;
    private final AuthSessionCleanupProperties settings;

    @Scheduled(fixedDelayString = "${app.auth.cleanup.delay-ms:3600000}",
            initialDelayString = "${app.auth.cleanup.initial-delay-ms:60000}")
    public void run() {
        // Match AuthSessionService's UTC timestamps, and hold one cutoff across all batches.
        LocalDateTime cutoff = LocalDateTime.now(Clock.systemUTC());
        int deleted = 0;
        try {
            for (int batch = 0; batch < settings.maxBatches(); batch++) {
                int count = cleanup.deleteExpiredBatch(cutoff, settings.batchSize());
                deleted += count;
                if (count < settings.batchSize()) break;
            }
            if (deleted > 0) log.info("Expired auth-session cleanup removed {} records", deleted);
        } catch (RuntimeException e) {
            // Earlier batches are already committed; a subsequent scheduled run can resume safely.
            log.error("Expired auth-session cleanup failed after removing {} records", deleted, e);
        }
    }
}
