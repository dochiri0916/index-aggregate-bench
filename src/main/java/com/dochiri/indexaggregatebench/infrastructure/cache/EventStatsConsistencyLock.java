package com.dochiri.indexaggregatebench.infrastructure.cache;

import com.dochiri.indexaggregatebench.application.port.out.EventStatsConsistencyPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

@Component
public class EventStatsConsistencyLock implements EventStatsConsistencyPort {

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);

    @Override
    public <T> T executeSharedUntilCompletion(Supplier<T> action) {
        return executeUntilCompletion(lock.readLock(), action);
    }

    @Override
    public <T> T executeExclusiveUntilCompletion(Supplier<T> action) {
        return executeUntilCompletion(lock.writeLock(), action);
    }

    @Override
    public <T> T executeExclusive(Supplier<T> action) {
        Lock writeLock = lock.writeLock();
        writeLock.lock();
        try {
            return action.get();
        } finally {
            writeLock.unlock();
        }
    }

    private <T> T executeUntilCompletion(Lock selectedLock, Supplier<T> action) {
        selectedLock.lock();
        boolean synchronizedWithTransaction = TransactionSynchronizationManager.isSynchronizationActive();
        if (synchronizedWithTransaction) {
            registerUnlock(selectedLock);
        }
        try {
            return action.get();
        } finally {
            if (!synchronizedWithTransaction) {
                selectedLock.unlock();
            }
        }
    }

    private void registerUnlock(Lock selectedLock) {
        try {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public int getOrder() {
                    return Integer.MAX_VALUE;
                }

                @Override
                public void afterCompletion(int status) {
                    selectedLock.unlock();
                }
            });
        } catch (RuntimeException exception) {
            selectedLock.unlock();
            throw exception;
        }
    }
}
