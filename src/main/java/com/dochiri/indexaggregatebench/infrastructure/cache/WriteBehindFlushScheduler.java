package com.dochiri.indexaggregatebench.infrastructure.cache;

import com.dochiri.indexaggregatebench.infrastructure.persistence.JdbcEventDailyStatsCommandAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WriteBehindFlushScheduler {

    private static final Logger log = LoggerFactory.getLogger(WriteBehindFlushScheduler.class);

    private final WriteBehindBuffer buffer;
    private final JdbcEventDailyStatsCommandAdapter eventDailyStatsAdapter;
    private final InMemoryEventStatsCache statsCache;

    public WriteBehindFlushScheduler(WriteBehindBuffer buffer,
                                     JdbcEventDailyStatsCommandAdapter eventDailyStatsAdapter,
                                     InMemoryEventStatsCache statsCache) {
        this.buffer = buffer;
        this.eventDailyStatsAdapter = eventDailyStatsAdapter;
        this.statsCache = statsCache;
    }

    @Scheduled(fixedDelayString = "${bench.write-behind.flush-interval-ms:5000}")
    public void flush() {
        List<WriteBehindBuffer.MergedCommand> batch = buffer.drain();
        if (batch.isEmpty()) {
            return;
        }
        try {
            int applied = eventDailyStatsAdapter.increaseBatch(batch);
            statsCache.markFlushed(batch);
            log.debug("Write-behind flushed {} merged keys to event_daily_stats", applied);
        } catch (RuntimeException exception) {
            buffer.restore(batch);
            log.error("Write-behind flush failed. batchSize={}", batch.size(), exception);
        }
    }
}
