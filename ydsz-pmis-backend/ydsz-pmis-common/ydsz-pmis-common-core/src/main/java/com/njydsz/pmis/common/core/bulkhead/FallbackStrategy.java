package com.njydsz.pmis.common.core.bulkhead;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FallbackStrategy<T> {
    private static final Logger log = LoggerFactory.getLogger(FallbackStrategy.class);
    private final Map<String, Supplier<T>> fallbacks = new ConcurrentHashMap<>();
    private final Map<String, Integer> failureCounts = new ConcurrentHashMap<>();
    private final int threshold;
    public FallbackStrategy(int threshold) { this.threshold = threshold; }
    public void registerFallback(String name, Supplier<T> fallback) { fallbacks.put(name, fallback); }
    public T execute(String name, Supplier<T> action) {
        try {
            T result = action.get();
            failureCounts.remove(name);
            return result;
        } catch (Exception e) {
            int count = failureCounts.merge(name, 1, Integer::sum);
            log.warn("Fallback {} failure count: {}", name, count);
            if (count >= threshold) {
                Supplier<T> fallback = fallbacks.get(name);
                if (fallback != null) { log.info("Fallback {} triggered", name); return fallback.get(); }
            }
            throw e;
        }
    }
}