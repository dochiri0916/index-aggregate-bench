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

    private final ConcurrentLinkedQueue<MergedCommand> buffer = new ConcurrentLinkedQueue<>();

    public void record(AppendEventCommand command) {
        DailyStatsKey key = DailyStatsKey.from(command);
        buffer.add(MergedCommand.from(key, command));
    }

    public void restore(List<MergedCommand> batch) {
        buffer.addAll(batch);
        log.debug("Write-behind restore: {} merged keys returned to buffer", batch.size());
    }

    public List<MergedCommand> drain() {
        List<MergedCommand> batch = new ArrayList<>();
        MergedCommand cmd;
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

    private List<MergedCommand> merge(List<MergedCommand> batch) {
        LinkedHashMap<DailyStatsKey, MergedCommand> merged = new LinkedHashMap<>();
        for (MergedCommand cmd : batch) {
            DailyStatsKey key = cmd.key();
            merged.compute(key, (k, existing) -> {
                if (existing == null) {
                    return cmd;
                }
                return existing.add(cmd);
            });
        }
        log.debug("Write-behind flush: {} commands merged into {} keys", batch.size(), merged.size());
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
            return add(from(key, cmd));
        }

        static MergedCommand from(DailyStatsKey key, AppendEventCommand cmd) {
            return new MergedCommand(key, 1,
                    cmd.durationSeconds(), cmd.metricValue(), cmd.costValue());
        }

        MergedCommand add(MergedCommand cmd) {
            return new MergedCommand(
                    key,
                    logCount + cmd.logCount(),
                    totalDurationSeconds + cmd.totalDurationSeconds(),
                    totalMetricValue + cmd.totalMetricValue(),
                    totalCostValue + cmd.totalCostValue()
            );
        }
    }
}
