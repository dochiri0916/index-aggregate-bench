package com.dochiri.indexaggregatebench.application.dto;

public record SeedEventResult(
        long insertedRows,
        long elapsedMillis
) {
}
