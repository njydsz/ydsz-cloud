package com.njydsz.pmis.common.core.chaos;

import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChaosMonkey {

    private static final Logger log = LoggerFactory.getLogger(ChaosMonkey.class);
    private final double failureRate;
    private final long maxDelayMs;
    private final boolean enabled;

    public ChaosMonkey(double failureRate, long maxDelayMs, boolean enabled) {
        this.failureRate = failureRate;
        this.maxDelayMs = maxDelayMs;
        this.enabled = enabled;
    }

    public void maybeInjectDelay(String op) {
        if (!enabled) { return; }
        if (ThreadLocalRandom.current().nextDouble() < failureRate) {
            long delay = ThreadLocalRandom.current().nextLong(0, maxDelayMs);
            log.warn("Chaos delay injected: op={}, delay={}ms", op, delay);
            try { Thread.sleep(delay); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }
}
