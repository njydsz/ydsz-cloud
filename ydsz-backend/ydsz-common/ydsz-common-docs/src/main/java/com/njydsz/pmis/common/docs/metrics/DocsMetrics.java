package com.njydsz.common.docs.metrics;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import com.njydsz.common.docs.enums.DocumentFormat;
import com.njydsz.common.docs.enums.PiiType;
import com.njydsz.common.docs.enums.SecurityLevel;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

import lombok.extern.slf4j.Slf4j;

/**
 * 文档处理模块 Micrometer 指标采集
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
public class DocsMetrics {

    private final ObjectProvider<MeterRegistry> registryProvider;
    private final AtomicLong asyncQueueSize = new AtomicLong(0);

    public DocsMetrics(ObjectProvider<MeterRegistry> registryProvider) {
        this.registryProvider = registryProvider;
        MeterRegistry registry = registryProvider.getIfAvailable();
        if (registry != null) {
            registry.gauge("docs.async.queue.size", asyncQueueSize);
            log.info("[DocsMetrics] Micrometer enabled");
        } else {
            log.warn("[DocsMetrics] MeterRegistry not available, metrics degraded");
        }
    }

    public void recordParse(DocumentFormat format, boolean success, long durationMs) {
        MeterRegistry r = registryProvider.getIfAvailable();
        if (r == null) return;
        Counter.builder("docs.parse.total").tags(Tags.of(Tag.of("format", format.name()), Tag.of("result", success ? "success" : "failure"))).register(r).increment();
        Timer.builder("docs.parse.duration").tags(Tags.of(Tag.of("format", format.name()))).register(r).record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordPiiDetected(PiiType type, int count) {
        MeterRegistry r = registryProvider.getIfAvailable();
        if (r == null) return;
        Counter.builder("docs.pii.detected").tags(Tags.of(Tag.of("type", type.name()))).register(r).increment(count);
    }

    public void recordSecurityScan(SecurityLevel level) {
        MeterRegistry r = registryProvider.getIfAvailable();
        if (r == null) return;
        Counter.builder("docs.security.scan").tags(Tags.of(Tag.of("level", level.name()))).register(r).increment();
    }

    public void recordPreprocess(String processorName, long durationMs) {
        MeterRegistry r = registryProvider.getIfAvailable();
        if (r == null) return;
        Timer.builder("docs.preprocess.duration").tags(Tags.of(Tag.of("processor", processorName))).register(r).record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void updateAsyncQueueSize(int size) {
        asyncQueueSize.set(size);
    }
}
