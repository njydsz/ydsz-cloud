package com.njydsz.pmis.common.core.quota;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
public class QuotaManager {
    private final Map<String, AtomicLong> usage = new ConcurrentHashMap<>();
    private final Map<String, Long> limits = new ConcurrentHashMap<>();
    public void setLimit(String resource, long limit) { limits.put(resource, limit); usage.putIfAbsent(resource, new AtomicLong(0)); }
    public boolean tryAcquire(String resource, long amount) { Long limit = limits.get(resource); if (limit == null) return true; AtomicLong cur = usage.get(resource); if (cur == null) return true; return cur.addAndGet(amount) <= limit; }
    public void release(String resource, long amount) { AtomicLong cur = usage.get(resource); if (cur != null) cur.addAndGet(-amount); }
}