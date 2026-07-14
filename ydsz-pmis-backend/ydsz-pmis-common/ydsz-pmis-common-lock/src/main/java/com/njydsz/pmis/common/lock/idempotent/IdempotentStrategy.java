package com.njydsz.pmis.common.lock.idempotent;

public interface IdempotentStrategy {
    boolean acquire(String key, long expireMillis);
    void release(String key);
    boolean exists(String key);
}