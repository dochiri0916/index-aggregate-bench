package com.dochiri.indexaggregatebench.application.dto;

import java.util.List;
import java.util.UUID;

public record MonthlyStatsFlushBatch(String batchId, List<MonthlyStatsDelta> deltas) {

    public MonthlyStatsFlushBatch {
        if (batchId == null || batchId.isBlank()) {
            throw new IllegalArgumentException("batchId is required");
        }
        if (deltas == null || deltas.stream().anyMatch(delta -> delta == null)) {
            throw new IllegalArgumentException("deltas are required");
        }
        deltas = List.copyOf(deltas);
    }

    public static MonthlyStatsFlushBatch of(List<MonthlyStatsDelta> deltas) {
        return new MonthlyStatsFlushBatch(UUID.randomUUID().toString(), deltas);
    }

    public static MonthlyStatsFlushBatch empty() {
        return of(List.of());
    }

    public boolean isEmpty() {
        return deltas.isEmpty();
    }
}
