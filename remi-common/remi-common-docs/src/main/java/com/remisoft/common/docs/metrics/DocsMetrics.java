package com.remisoft.common.docs.metrics;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import com.remisoft.common.docs.enums.DocumentFormat;
import com.remisoft.common.docs.enums.PiiType;
import com.remisoft.common.docs.enums.SecurityLevel;

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
 * @author remi-team
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

    /**
     * 记录一次文档解析的次数与耗时。
     *
     * <p>次数 Counter 带 {@code format} + {@code result} 双标签，可直接算出各格式解析失败率；
     * 耗时 Timer 只带 {@code format} 标签，成功与失败的耗时合并统计。
     * {@link MeterRegistry} 缺失时静默返回，调用方无需判空。
     *
     * @param format     文档格式，不可为 {@code null}；未知格式请传 {@code UNKNOWN} 而非 null
     * @param success    是否解析成功，映射为 result 标签的 success/failure
     * @param durationMs 解析耗时（毫秒）
     */
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

    /**
     * 累加某一类 PII 的命中条数。
     *
     * <p>只上报类型与数量，不上报命中的敏感内容本身，避免指标系统二次泄露隐私数据。
     * {@link MeterRegistry} 缺失时静默返回。
     *
     * @param type  PII 类型，不可为 {@code null}
     * @param count 本次命中条数，应为非负数；传 0 时 Counter 不变化
     */
    public void recordPiiDetected(PiiType type, int count) {
        MeterRegistry r = registry();
        if (r == null) return;
        Counter.builder("docs.pii.detected")
                .tags(Tags.of(Tag.of("type", type.name())))
                .register(r).increment(count);
    }

    /**
     * 记录一次安全扫描结果，按风险等级分桶计数。
     *
     * <p>HIGH / CRITICAL 桶的突增通常意味着遭遇恶意文件投放，应据此配置告警。
     * {@link MeterRegistry} 缺失时静默返回。
     *
     * @param level 扫描判定的安全等级，不可为 {@code null}
     */
    public void recordSecurityScan(SecurityLevel level) {
        MeterRegistry r = registry();
        if (r == null) return;
        Counter.builder("docs.security.scan")
                .tags(Tags.of(Tag.of("level", level.name())))
                .register(r).increment();
    }

    /**
     * 记录单个预处理器的执行耗时，用于定位流水线中的性能瓶颈节点。
     *
     * <p>{@code processorName} 会直接作为标签值，必须使用有限枚举集（处理器类名/别名），
     * 切勿传入文件名等高基数字符串，否则会导致时序库指标爆炸。
     * {@link MeterRegistry} 缺失时静默返回。
     *
     * @param processorName 预处理器名称，取值必须可枚举
     * @param durationMs    执行耗时（毫秒）
     */
    public void recordPreprocess(String processorName, long durationMs) {
        MeterRegistry r = registry();
        if (r == null) return;
        Timer.builder("docs.preprocess.duration")
                .tags(Tags.of(Tag.of("processor", processorName)))
                .register(r).record(durationMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 刷新异步解析队列积压量 Gauge。
     *
     * <p>Gauge 在构造函数中已绑定到 {@link AtomicLong} 实例，此处只更新数值，
     * 不会重复注册 meter。基于 {@link AtomicLong}，可由采集线程与业务线程并发调用。
     * 该值持续增长说明消费能力不足，是扩容或限流的判断依据。
     *
     * @param size 当前队列内待处理任务数
     */
    public void updateAsyncQueueSize(int size) {
        asyncQueueSize.set(size);
    }
}
