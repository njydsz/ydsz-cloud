package com.njydsz.pmis.common.jdbc.monitor;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public class SlowSqlAnalyzer {
    private static final Logger log = LoggerFactory.getLogger(SlowSqlAnalyzer.class);
    private final long thresholdMs;
    private final AtomicLong slowCount = new AtomicLong(0);
    public SlowSqlAnalyzer(long thresholdMs) { this.thresholdMs = thresholdMs; }
    public void analyze(String sql, long elapsedMs) { if (elapsedMs > thresholdMs) { slowCount.incrementAndGet(); log.warn(sql); } }
    public long getSlowCount() { return slowCount.get(); }
}