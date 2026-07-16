package com.njydsz.common.file.storage;

import java.util.function.Supplier;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.file.exception.FileExceptionCode;

import lombok.extern.slf4j.Slf4j;

/**
 * Storage operation retry helper with exponential backoff.
 *
 * @author ydsz-team
 * @since 1.1.0
 */
@Slf4j
public class StorageRetryHelper {

    private final int maxRetries;
    private final long initialBackoffMillis;

    public StorageRetryHelper(int maxRetries, long initialBackoffMillis) {
        this.maxRetries = Math.max(0, maxRetries);
        this.initialBackoffMillis = Math.max(100, initialBackoffMillis);
    }

    public <T> T executeWithRetry(Supplier<T> action, String operationName) {
        int attempts = 0;
        while (attempts <= maxRetries) {
            try {
                return action.get();
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                attempts++;
                if (attempts > maxRetries) break;
                long backoff = initialBackoffMillis * (1L << Math.min(attempts - 1, 10));
                log.warn("StorageRetry {} attempt {} failed, retrying in {}ms: {}", operationName, attempts, backoff, e.getMessage());
                try { Thread.sleep(backoff); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }
        }
        log.error("StorageRetry {} failed after {} attempts", operationName, attempts);
        throw new BusinessException(FileExceptionCode.UNKNOWN);
    }

    public void executeRunnableWithRetry(Runnable action, String operationName) {
        executeWithRetry(() -> { action.run(); return null; }, operationName);
    }

    public int getMaxRetries() {
        return maxRetries;
    }
}
