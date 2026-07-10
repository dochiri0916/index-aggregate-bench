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

    public WriteBehindFlushScheduler(WriteBehindBuffer buffer,
                                     JdbcEventDailyStatsCommandAdapter eventDailyStatsAdapter) {
        this.buffer = buffer;
        this.eventDailyStatsAdapter = eventDailyStatsAdapter;
    }

    @Scheduled(fixedDelayString = "${bench.write-behind.flush-interval-ms:5000}")
    public void flush() {
        List<WriteBehindBuffer.MergedCommand> batch = buffer.drain();
        if (batch.isEmpty()) {
            return;
        }
        int applied = eventDailyStatsAdapter.increaseBatch(batch);
        log.debug("Write-behind flushed {} merged keys to event_daily_stats", applied);
    }
}
