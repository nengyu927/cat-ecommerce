package com.catshop.scheduling;

import com.catshop.config.AuthSessionCleanupProperties;
import com.catshop.config.AuthSessionCleanupConfig;
import com.catshop.service.AuthSessionCleanupService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthSessionCleanupJobTest {
    private final AuthSessionCleanupService service = mock(AuthSessionCleanupService.class);
    private AuthSessionCleanupJob job(int maxBatches) {
        return new AuthSessionCleanupJob(service, new AuthSessionCleanupProperties(2, maxBatches, 60000, 1000));
    }

    @Test void stopsAfterPartialBatchAndUsesOneCutoff() {
        when(service.deleteExpiredBatch(any(), eq(2))).thenReturn(2, 1);
        job(10).run();
        var cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(service, times(2)).deleteExpiredBatch(cutoff.capture(), eq(2));
        assertThat(cutoff.getAllValues().get(0)).isEqualTo(cutoff.getAllValues().get(1));
    }
    @Test void limitsMaximumBatches() {
        when(service.deleteExpiredBatch(any(), eq(2))).thenReturn(2);
        job(3).run();
        verify(service, times(3)).deleteExpiredBatch(any(), eq(2));
    }
    @Test void stopsOnEmptyBatch() {
        job(10).run();
        verify(service).deleteExpiredBatch(any(), eq(2));
    }
    @Test void retriesAtNextRunAfterDatabaseFailure() {
        when(service.deleteExpiredBatch(any(), eq(2))).thenThrow(new IllegalStateException("test database failure")).thenReturn(0);
        var job = job(10);
        assertThatCode(job::run).doesNotThrowAnyException();
        job.run();
        verify(service, times(2)).deleteExpiredBatch(any(), eq(2));
    }
    @Test void disabledConfigurationHasNoScheduledJob() {
        new ApplicationContextRunner().withUserConfiguration(AuthSessionCleanupConfig.class)
                .withPropertyValues("app.auth.cleanup.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(AuthSessionCleanupJob.class));
    }
    @Test void enabledConfigurationBindsDefaultsAndRegistersJob() {
        new ApplicationContextRunner().withUserConfiguration(AuthSessionCleanupConfig.class)
                .withBean(AuthSessionCleanupService.class, () -> service)
                .run(context -> {
                    assertThat(context).hasSingleBean(AuthSessionCleanupJob.class);
                    var settings = context.getBean(AuthSessionCleanupProperties.class);
                    assertThat(settings.batchSize()).isEqualTo(200);
                    assertThat(settings.maxBatches()).isEqualTo(10);
                });
    }
    @Test void invalidConfigurationFailsAtStartup() {
        new ApplicationContextRunner().withUserConfiguration(AuthSessionCleanupConfig.class)
                .withBean(AuthSessionCleanupService.class, () -> service)
                .withPropertyValues("app.auth.cleanup.batch-size=0")
                .run(context -> assertThat(context).hasFailed());
    }
    @Test void rejectsUnsafeBatchAndTimingConfiguration() {
        try (var validatorFactory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var violations = validatorFactory.getValidator().validate(new AuthSessionCleanupProperties(1001, 0, 1, 0));
            assertThat(violations).hasSize(4);
        }
    }
}
