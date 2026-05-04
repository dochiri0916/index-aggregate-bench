package com.dochiri.indexaggregatebench.application.service;

import com.dochiri.indexaggregatebench.application.dto.AppendVehicleCommand;
import com.dochiri.indexaggregatebench.application.dto.CompareVehicleStatsResult;
import com.dochiri.indexaggregatebench.application.dto.RebuildVehicleDailyStatsResult;
import com.dochiri.indexaggregatebench.application.dto.SeedVehicleCondition;
import com.dochiri.indexaggregatebench.application.dto.SeedVehicleResult;
import com.dochiri.indexaggregatebench.application.dto.VehicleStatsBackend;
import com.dochiri.indexaggregatebench.application.dto.VehicleStatsQuery;
import com.dochiri.indexaggregatebench.application.dto.TimedVehicleStats;
import com.dochiri.indexaggregatebench.infrastructure.persistence.JdbcVehicleDailyStatsCommandAdapter;
import com.dochiri.indexaggregatebench.infrastructure.persistence.VehiclePersistenceAdapter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class VehicleService {

    private final VehiclePersistenceAdapter vehiclePersistenceAdapter;
    private final JdbcVehicleDailyStatsCommandAdapter vehicleDailyStatsAdapter;
    private final VehicleStatsService vehicleStatsService;

    public VehicleService(VehiclePersistenceAdapter vehiclePersistenceAdapter,
                          JdbcVehicleDailyStatsCommandAdapter vehicleDailyStatsAdapter,
                          VehicleStatsService vehicleStatsService) {
        this.vehiclePersistenceAdapter = vehiclePersistenceAdapter;
        this.vehicleDailyStatsAdapter = vehicleDailyStatsAdapter;
        this.vehicleStatsService = vehicleStatsService;
    }

    public SeedVehicleResult seed(SeedVehicleCondition condition) {
        long started = System.nanoTime();

        if (condition.truncate()) {
            vehicleDailyStatsAdapter.truncate();
            vehiclePersistenceAdapter.truncate();
            vehicleStatsService.clearCache();
        }

        long insertedRows = vehiclePersistenceAdapter.seed(condition);
        return new SeedVehicleResult(insertedRows, elapsedMillis(started));
    }

    @Transactional
    public RebuildVehicleDailyStatsResult rebuildDailyStats(LocalDate from, LocalDate to) {
        long started = System.nanoTime();

        int rows = vehicleDailyStatsAdapter.rebuild(from, to);
        vehicleStatsService.clearCache();

        return new RebuildVehicleDailyStatsResult(rows, elapsedMillis(started));
    }

    public CompareVehicleStatsResult compare(VehicleStatsQuery query, int iterations, boolean cache) {
        int count = Math.max(1, iterations);

        TimedVehicleStats lastRaw = null;
        TimedVehicleStats lastSummary = null;
        long rawTotal = 0;
        long vehicleDailyStatsTotal = 0;
        long rawMin = Long.MAX_VALUE;
        long vehicleDailyStatsMin = Long.MAX_VALUE;

        for (int i = 0; i < count; i++) {
            lastRaw = vehicleStatsService.getStats(VehicleStatsBackend.RAW, query, cache);
            lastSummary = vehicleStatsService.getStats(VehicleStatsBackend.DAILY_STATS, query, cache);

            rawTotal += lastRaw.elapsedMillis();
            vehicleDailyStatsTotal += lastSummary.elapsedMillis();
            rawMin = Math.min(rawMin, lastRaw.elapsedMillis());
            vehicleDailyStatsMin = Math.min(vehicleDailyStatsMin, lastSummary.elapsedMillis());
        }

        return new CompareVehicleStatsResult(
                count,
                rawTotal / count,
                vehicleDailyStatsTotal / count,
                rawMin,
                vehicleDailyStatsMin,
                lastRaw,
                lastSummary
        );
    }

    @Transactional
    public void append(AppendVehicleCommand command) {
        vehiclePersistenceAdapter.append(command);
        vehicleDailyStatsAdapter.increase(command);
        vehicleStatsService.evictRelated(command.id(), command.batteryId());
    }

    private long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }
}
