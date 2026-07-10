package com.dochiri.indexaggregatebench.infrastructure.cache;

import com.dochiri.indexaggregatebench.application.dto.EventStats;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.atomic.AtomicLong;

public class AtomicAggregate {

    private final AtomicLong logCount = new AtomicLong(0);
    private final AtomicLong totalDurationSeconds = new AtomicLong(0);
    private final AtomicLong totalMetricValue = new AtomicLong(0);
    private final AtomicLong totalCostValue = new AtomicLong(0);

    public void add(int durationSeconds, int metricValue, int costValue) {
        logCount.incrementAndGet();
        totalDurationSeconds.addAndGet(durationSeconds);
        totalMetricValue.addAndGet(metricValue);
        totalCostValue.addAndGet(costValue);
    }

    public void add(long logCount, long durationSeconds, long metricValue, long costValue) {
        this.logCount.addAndGet(logCount);
        this.totalDurationSeconds.addAndGet(durationSeconds);
        this.totalMetricValue.addAndGet(metricValue);
        this.totalCostValue.addAndGet(costValue);
    }

    public EventStats toEventStats() {
        long count = logCount.get();
        long duration = totalDurationSeconds.get();
        long metric = totalMetricValue.get();
        long cost = totalCostValue.get();

        BigDecimal avgDuration;
        if (count == 0) {
            avgDuration = BigDecimal.ZERO;
        } else {
            avgDuration = BigDecimal.valueOf(duration)
                    .divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        }

        BigDecimal metricPerCost;
        if (cost == 0) {
            metricPerCost = BigDecimal.ZERO;
        } else {
            metricPerCost = BigDecimal.valueOf(metric)
                    .divide(BigDecimal.valueOf(cost), 2, RoundingMode.HALF_UP);
        }

        return new EventStats(count, duration, metric, cost, avgDuration, metricPerCost);
    }

    public long logCount() {
        return logCount.get();
    }

    public long totalDurationSeconds() {
        return totalDurationSeconds.get();
    }

    public long totalMetricValue() {
        return totalMetricValue.get();
    }

    public long totalCostValue() {
        return totalCostValue.get();
    }
}
