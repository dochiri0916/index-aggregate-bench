package com.dochiri.indexaggregatebench.application.dto;

import java.time.LocalDateTime;

public record AppendEventCommand(
        long targetId,
        long segmentId,
        LocalDateTime occurredAt,
        int durationSeconds,
        int metricValue,
        int costValue
) {
    public AppendEventCommand {
        if (occurredAt == null) {
            occurredAt = LocalDateTime.now();
        }
        if (durationSeconds <= 0) {
            throw new IllegalArgumentException("durationSeconds must be positive");
        }
    }
}
