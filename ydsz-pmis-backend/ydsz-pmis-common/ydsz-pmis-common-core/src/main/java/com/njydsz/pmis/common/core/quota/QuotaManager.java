package com.njydsz.pmis.common.core.quota;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QuotaManager {

    private static final Logger log = LoggerFactory.getLogger(QuotaManager.class);
    private final Map<String, AtomicLong> usage = new ConcurrentHashMap<>();
    private final Map<String, Long> limits = new ConcurrentHashMap<>();

    public void setLimit(String resource, long limit) {
        limits.put(resource, limit);
        usage.putIfAbsent(resource, new AtomicLong(0));
        log.info("Quota limit set: resource={}, limit={}", resource, limit);
    }

    public boolean tryAcquire(String resource, long amount) {
        Long limit = limits.get(resource);
        if (limit == null) { return true; }
        AtomicLong cur = usage.get(resource);
        if (cur == null) { return true; }
        while (true) {
            long current = cur.get();
            long newValue = current + amount;
            if (newValue > limit) {
                log.debug("Quota exceeded: resource={}, current={}, requested={}, limit={}", resource, current, amount, limit);
                return false;
            }
            if (cur.compareAndSet(current, newValue)) {
                return true;
            }
        }
    }

    public void release(String resource, long amount) {
        AtomicLong cur = usage.get(resource);
        if (cur != null) {
            cur.addAndGet(-amount);
        }
    }

    public long getUsage(String resource) {
        AtomicLong cur = usage.get(resource);
        return cur != null ? cur.get() : 0;
    }

    public long getLimit(String resource) {
        Long limit = limits.get(resource);
        return limit != null ? limit : -1;
    }

    public void reset(String resource) {
        AtomicLong cur = usage.get(resource);
        if (cur != null) { cur.set(0); }
    }
}
