package com.catshop.service;

import com.catshop.repository.AuthSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthSessionCleanupService {
    private final AuthSessionRepository sessions;

    // Each batch commits independently; do not keep one long transaction across the entire job.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int deleteExpiredBatch(LocalDateTime cutoff, int batchSize) {
        if (cutoff == null || batchSize < 1 || batchSize > 1000)
            throw new IllegalArgumentException("Cleanup requires a cutoff and batch size between 1 and 1000");
        var ids = sessions.findExpiredIds(cutoff, PageRequest.of(0, batchSize));
        if (ids.isEmpty()) return 0;
        // Recheck expiry at deletion, so an expiry changed after selection is not accidentally removed.
        return sessions.deleteExpiredIds(ids, cutoff);
    }
}
