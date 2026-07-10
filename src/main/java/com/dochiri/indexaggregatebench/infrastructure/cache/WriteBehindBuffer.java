package com.dochiri.indexaggregatebench.infrastructure.cache;

import com.dochiri.indexaggregatebench.application.dto.AppendEventCommand;
import com.dochiri.indexaggregatebench.application.dto.DailyStatsKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

@Component
public class WriteBehindBuffer {

    private static final Logger log = LoggerFactory.getLogger(WriteBehindBuffer.class);

    private final ConcurrentLinkedQueue<AppendEventCommand> buffer = new ConcurrentLinkedQueue<>();

    public void record(AppendEventCommand command) {
        buffer.add(command);
    }

    public List<MergedCommand> drain() {
        List<AppendEventCommand> batch = new ArrayList<>();
        AppendEventCommand cmd;
        while ((cmd = buffer.poll()) != null) {
            batch.add(cmd);
        }
        if (batch.isEmpty()) {
            return List.of();
        }
        return merge(batch);
    }

    public int size() {
        return buffer.size();
    }

    private List<MergedCommand> merge(List<AppendEventCommand> batch) {
        LinkedHashMap<DailyStatsKey, MergedCommand> merged = new LinkedHashMap<>();
        for (AppendEventCommand cmd : batch) {
            DailyStatsKey key = DailyStatsKey.from(cmd);
            merged.compute(key, (k, existing) -> {
                if (existing == null) {
                    return new MergedCommand(k, 1,
                            cmd.durationSeconds(),
                            cmd.metricValue(),
                            cmd.costValue());
                }
                return existing.add(cmd);
            });
        }
        log.debug("Write-behind flush: {} events merged into {} keys", batch.size(), merged.size());
        return List.copyOf(merged.values());
    }

    public record MergedCommand(
            DailyStatsKey key,
            long logCount,
            long totalDurationSeconds,
            long totalMetricValue,
            long totalCostValue
    ) {
        MergedCommand add(AppendEventCommand cmd) {
            return new MergedCommand(
                    key,
                    logCount + 1,
                    totalDurationSeconds + cmd.durationSeconds(),
                    totalMetricValue + cmd.metricValue(),
                    totalCostValue + cmd.costValue()
            );
        }
    }
}
