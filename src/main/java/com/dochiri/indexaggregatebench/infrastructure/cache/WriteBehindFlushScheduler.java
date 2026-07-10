package com.dochiri.indexaggregatebench.infrastructure.cache;

import com.dochiri.indexaggregatebench.application.port.in.FlushEventDailyStatsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WriteBehindFlushScheduler {

    private static final Logger log = LoggerFactory.getLogger(WriteBehindFlushScheduler.class);

    private final FlushEventDailyStatsUseCase flushEventDailyStatsUseCase;

    public WriteBehindFlushScheduler(FlushEventDailyStatsUseCase flushEventDailyStatsUseCase) {
        this.flushEventDailyStatsUseCase = flushEventDailyStatsUseCase;
    }

    @Scheduled(fixedDelayString = "${bench.write-behind.flush-interval-ms:5000}")
    public void flush() {
        try {
            int applied = flushEventDailyStatsUseCase.flush();
            if (applied == 0) {
                return;
            }
            log.debug("Write-behind flushed {} daily aggregate keys", applied);
        } catch (RuntimeException exception) {
            log.error("Write-behind flush failed", exception);
        }
    }
}
