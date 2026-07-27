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
 * 文档处理模块 Micrometer 指标采集。
 *
 * <p>采集文档解析、PII 检测、安全扫描和预处理等关键路径的指标，
 * 通过 {@link MeterRegistry} 暴露到 Prometheus 供 Grafana 仪表盘展示。
 *
 * <h3>指标列表</h3>
 * <ul>
 *   <li>{@code docs.parse.total}（Counter）：文档解析次数（按格式/状态标签）</li>
 *   <li>{@code docs.parse.duration}（Timer）：文档解析耗时</li>
 *   <li>{@code docs.pii.detected}（Counter）：PII 检测命中次数（按类型标签）</li>
 *   <li>{@code docs.security.scan}（Counter）：安全扫描次数（按级别标签）</li>
 *   <li>{@code docs.preprocess.duration}（Timer）：预处理耗时</li>
 *   <li>{@code docs.async.queue.size}（Gauge）：异步解析队列大小</li>
 * </ul>
 *
 * <p>当 {@link MeterRegistry} 不在 Classpath 时，指标采集静默降级为空操作。
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
    private MeterRegistry cachedRegistry;

    public DocsMetrics(ObjectProvider<MeterRegistry> registryProvider) {
        this.registryProvider = registryProvider;
        MeterRegistry registry = registryProvider.getIfAvailable();
        if (registry != null) {
            this.cachedRegistry = registry;
            registry.gauge("docs.async.queue.size", asyncQueueSize);
            log.info("[DocsMetrics] Micrometer enabled");
        } else {
            log.warn("[DocsMetrics] MeterRegistry not available, metrics degraded");
        }
    }

    private MeterRegistry registry() {
        if (cachedRegistry != null) {
            return cachedRegistry;
        }
        cachedRegistry = registryProvider.getIfAvailable();
        return cachedRegistry;
    }

    public void recordParse(DocumentFormat format, boolean success, long durationMs) {
        MeterRegistry r = registry();
        if (r == null) return;
        Counter.builder("docs.parse.total")
                .tags(Tags.of(Tag.of("format", format.name()),
                        Tag.of("result", success ? "success" : "failure")))
                .register(r).increment();
        Timer.builder("docs.parse.duration")
                .tags(Tags.of(Tag.of("format", format.name())))
                .register(r).record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordPiiDetected(PiiType type, int count) {
        MeterRegistry r = registry();
        if (r == null) return;
        Counter.builder("docs.pii.detected")
                .tags(Tags.of(Tag.of("type", type.name())))
                .register(r).increment(count);
    }

    public void recordSecurityScan(SecurityLevel level) {
        MeterRegistry r = registry();
        if (r == null) return;
        Counter.builder("docs.security.scan")
                .tags(Tags.of(Tag.of("level", level.name())))
                .register(r).increment();
    }

    public void recordPreprocess(String processorName, long durationMs) {
        MeterRegistry r = registry();
        if (r == null) return;
        Timer.builder("docs.preprocess.duration")
                .tags(Tags.of(Tag.of("processor", processorName)))
                .register(r).record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void updateAsyncQueueSize(int size) {
        asyncQueueSize.set(size);
    }
}
