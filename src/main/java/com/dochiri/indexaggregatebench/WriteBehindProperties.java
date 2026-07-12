package com.dochiri.indexaggregatebench;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bench.write-behind")
public record WriteBehindProperties(
        int maxAttempts,
        long retryBackoffMs
) {
    public WriteBehindProperties {
        if (maxAttempts <= 0) {
            maxAttempts = 3;
        }
        if (retryBackoffMs < 0) {
            retryBackoffMs = 0;
        }
    }
}
