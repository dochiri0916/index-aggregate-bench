package com.dochiri.indexaggregatebench.application.port.out;

import java.util.function.Supplier;

public interface EventStatsConsistencyPort {
    <T> T executeSharedUntilCompletion(Supplier<T> action);
    <T> T executeExclusiveUntilCompletion(Supplier<T> action);
    <T> T executeExclusive(Supplier<T> action);
}
