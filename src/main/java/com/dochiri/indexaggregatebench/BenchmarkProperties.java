package com.dochiri.indexaggregatebench;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "benchmark")
public record BenchmarkProperties(
        Raw raw
) {
    public BenchmarkProperties {
        if (raw == null) {
            raw = new Raw(0);
        }
    }

    public record Raw(
            int queryTimeoutMillis
    ) {
    }
}
