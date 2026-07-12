package com.dochiri.indexaggregatebench.infrastructure.cache;

import com.dochiri.indexaggregatebench.WriteBehindProperties;
import com.dochiri.indexaggregatebench.application.dto.WriteBehindFlushStatus;
import com.dochiri.indexaggregatebench.application.port.in.FlushEventMonthlyStatsUseCase;
import com.dochiri.indexaggregatebench.application.port.out.EventStatsWriteBehindPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class WriteBehindFlushScheduler {

    private static final Logger log = LoggerFactory.getLogger(WriteBehindFlushScheduler.class);

    private final FlushEventMonthlyStatsUseCase flushEventMonthlyStatsUseCase;
    private final EventStatsWriteBehindPort writeBehindPort;
    private final WriteBehindProperties properties;
    private int attemptCount;
    private Instant lastSuccessAt;
    private Instant lastFailureAt;
    private String lastFailureReason;
    private Instant nextRetryAt;
    private boolean finalFailure;

    public WriteBehindFlushScheduler(FlushEventMonthlyStatsUseCase flushEventMonthlyStatsUseCase,
                                     EventStatsWriteBehindPort writeBehindPort,
                                     WriteBehindProperties properties) {
        this.flushEventMonthlyStatsUseCase = flushEventMonthlyStatsUseCase;
        this.writeBehindPort = writeBehindPort;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${bench.write-behind.flush-interval-ms:5000}")
    public synchronized void flush() {
        Instant now = Instant.now();
        if (finalFailure || (nextRetryAt != null && now.isBefore(nextRetryAt))) {
            return;
        }
        try {
            int applied = flushEventMonthlyStatsUseCase.flush();
            attemptCount = 0;
            nextRetryAt = null;
            finalFailure = false;
            lastSuccessAt = now;
            if (applied == 0) {
                return;
            }
            log.debug("Write-behind flushed {} monthly aggregate keys", applied);
        } catch (RuntimeException exception) {
            attemptCount++;
            lastFailureAt = now;
            lastFailureReason = exception.getClass().getSimpleName() + ": " + exception.getMessage();
            if (attemptCount >= properties.maxAttempts()) {
                finalFailure = true;
                nextRetryAt = null;
                log.error("Write-behind flush reached final failure after {} attempts", attemptCount, exception);
                return;
            }
            nextRetryAt = now.plusMillis(backoffMillis(attemptCount));
            log.error("Write-behind flush failed; retry scheduled at {}", nextRetryAt, exception);
        }
    }

    public synchronized WriteBehindFlushStatus status() {
        return new WriteBehindFlushStatus(
                writeBehindPort.size(), attemptCount, lastSuccessAt, lastFailureAt,
                lastFailureReason, nextRetryAt, finalFailure
        );
    }

    private long backoffMillis(int attempt) {
        int shift = Math.min(attempt - 1, 30);
        long multiplier = 1L << shift;
        return Math.min(Long.MAX_VALUE / multiplier, properties.retryBackoffMs()) * multiplier;
    }
}
