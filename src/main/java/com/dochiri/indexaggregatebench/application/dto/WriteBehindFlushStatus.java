package com.dochiri.indexaggregatebench.application.dto;

import java.time.Instant;

public record WriteBehindFlushStatus(
        int pendingDeltaCount,
        int attemptCount,
        Instant lastSuccessAt,
        Instant lastFailureAt,
        String lastFailureReason,
        Instant nextRetryAt,
        boolean finalFailure
) {
}
