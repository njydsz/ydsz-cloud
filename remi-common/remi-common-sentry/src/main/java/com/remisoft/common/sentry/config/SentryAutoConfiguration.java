package com.remisoft.common.sentry.config;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.ObjectProvider;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.remisoft.common.sentry.alerting.AlertConverger;
import com.remisoft.common.sentry.alerting.DefaultAlertPublisher;
import com.remisoft.common.notify.core.NotifyService;
import com.remisoft.common.sentry.alerting.NotifyAlertHandler;
import com.remisoft.common.sentry.domain.AlertSeverity;
import com.remisoft.common.sentry.health.SentryHealthIndicator;
import com.remisoft.common.sentry.health.SystemResourceHealthIndicator;
import com.remisoft.common.sentry.logging.AsyncLogPublisher;
import com.remisoft.common.sentry.logging.DualLogPublisher;
import com.remisoft.common.sentry.logging.ElkLogPublisher;
import com.remisoft.common.sentry.logging.LokiLogPublisher;
import com.remisoft.common.sentry.metrics.InMemoryMetricsCollector;
import com.remisoft.common.sentry.metrics.MicrometerMetricsCollector;
import com.remisoft.common.sentry.metrics.SystemMetricsCollector;
import com.remisoft.common.sentry.resilience.CircuitBreaker;
import com.remisoft.common.sentry.sla.DefaultSlaCollector;
import com.remisoft.common.sentry.sla.SlaMetricAspect;
import com.remisoft.common.sentry.spi.AlertPublisher;
import com.remisoft.common.sentry.spi.LogPublisher;
import com.remisoft.common.sentry.spi.MetricsCollector;
import com.remisoft.common.sentry.spi.SlaCollector;
import com.remisoft.common.sentry.spi.TraceContext;
import com.remisoft.common.sentry.tracing.DefaultTraceContext;
import com.remisoft.common.sentry.tracing.OpenTelemetryTraceContext;
import com.remisoft.common.sentry.tracing.SkyWalkingTraceContext;
import com.remisoft.common.sentry.tracing.SlowTraceDetector;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * Sentry 可观测性模块自动配置。
 *
 * <p>自动装配以下核心组件（均通过 {@code @ConditionalOnMissingBean} 支持覆盖）：
 *
 * <h3>指标采集层</h3>
 * <ul>
 *   <li>{@link MicrometerMetricsCollector}：Micrometer 指标采集（优先）</li>
 *   <li>{@link InMemoryMetricsCollector}：内存指标采集（降级）</li>
 *   <li>{@link SystemMetricsCollector}：系统资源指标（CPU/内存/磁盘/GC）</li>
 * </ul>
 *
 * <h3>日志发布层</h3>
 * <ul>
 *   <li>{@link ElkLogPublisher}：ELK + Logstash TCP/UDP 推送</li>
 *   <li>{@link LokiLogPublisher}：Loki HTTP 推送</li>
 *   <li>{@link DualLogPublisher}：双发模式（ELK + Loki 同时推送）</li>
 *   <li>{@link AsyncLogPublisher}：异步发布包装器（有界队列 + 降级）</li>
 * </ul>
 *
 * <h3>链路追踪层</h3>
 * <ul>
 *   <li>{@link SkyWalkingTraceContext}：SkyWalking 链路追踪（优先）</li>
 *   <li>{@link OpenTelemetryTraceContext}：OpenTelemetry 链路追踪</li>
 *   <li>{@link DefaultTraceContext}：默认 MDC 链路追踪（降级）</li>
 *   <li>{@link SlowTraceDetector}：慢追踪检测与告警</li>
 * </ul>
 *
 * <h3>告警与 SLA 层</h3>
 * <ul>
 *   <li>{@link AlertConverger}：告警收敛（时间窗口 + 去重 + 静默期）</li>
 *   <li>{@link DefaultAlertPublisher}：告警发布</li>
 *   <li>{@link NotifyAlertHandler}：告警 → IM 通知桥接</li>
 *   <li>{@link DefaultSlaCollector} + {@link SlaMetricAspect}：SLA 指标采集 AOP</li>
 * </ul>
 *
 * <p>支持通过 {@code remi.sentry.*} 配置快速切换 ELK / Loki 双方案。
 *
 * @author remi-team
 * @since 1.0.0
 * @see SentryProperties
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(SentryProperties.class)
@EnableScheduling
@ConditionalOnProperty(prefix = "remi.sentry", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SentryAutoConfiguration {

    private ScheduledExecutorService systemMetricsScheduler;
    private AsyncLogPublisher asyncLogPublisher;

    // ==================== 指标采集 ====================

    @Configuration
    @ConditionalOnClass(MeterRegistry.class)
    static class MicrometerMetricsConfiguration {

        /**
         * 装配基于 Micrometer 的指标采集器，作为默认（首选）实现。
         *
         * <p>仅在 classpath 存在 {@link MeterRegistry} 且未显式配置其他 primary 时生效，
         * 指标最终落到 Micrometer 已注册的后端（通常是 Prometheus）。
         *
         * @param meterRegistry Micrometer 注册中心，由 Spring Boot Actuator 提供
         * @return 指标采集器实现
         */
        @Bean
        @ConditionalOnMissingBean(MetricsCollector.class)
        @ConditionalOnProperty(prefix = "remi.sentry.metrics", name = "primary",
                havingValue = "micrometer", matchIfMissing = true)
        public MetricsCollector micrometerMetricsCollector(MeterRegistry meterRegistry) {
            return new MicrometerMetricsCollector(meterRegistry);
        }
    }

    /**
     * 装配纯内存指标采集器，作为无 Micrometer 环境下的降级实现。
     *
     * <p>指标只存活于当前 JVM、进程重启即丢失，且不对外暴露 scrape 端点，
     * 仅适用于单机部署、单元测试或临时排障场景，需显式配置
     * {@code remi.sentry.metrics.primary=memory} 才会启用。
     *
     * @return 内存指标采集器实现
     */
    @Bean
    @ConditionalOnMissingBean(MetricsCollector.class)
    @ConditionalOnProperty(prefix = "remi.sentry.metrics", name = "primary", havingValue = "memory")
    public MetricsCollector inMemoryMetricsCollector() {
        return new InMemoryMetricsCollector();
    }

    /**
     * 装配系统资源指标采集器，并启动独立守护线程周期性采集 CPU / 内存 / 磁盘 / GC。
     *
     * <p>采集线程与业务线程池隔离，避免采集卡顿拖垮业务；线程为 daemon，
     * 不阻止 JVM 退出。首次采集延迟 5 秒，规避应用启动期指标失真。
     * 调度器引用保存在字段中，由 {@link #destroy()} 统一关闭。
     *
     * @param metricsCollector 指标写出目标
     * @param properties       监控配置，读取 metrics.systemMetricsIntervalSeconds 作为采集周期
     * @return 系统指标采集器；同时已注册到内部调度器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "remi.sentry.metrics", name = "enable-system-metrics",
            havingValue = "true", matchIfMissing = true)
    public SystemMetricsCollector systemMetricsCollector(MetricsCollector metricsCollector,
                                                          SentryProperties properties) {
        SystemMetricsCollector collector = new SystemMetricsCollector(metricsCollector);
        int interval = properties.getMetrics().getSystemMetricsIntervalSeconds();
        systemMetricsScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sentry-system-metrics");
            t.setDaemon(true);
            return t;
        });
        systemMetricsScheduler.scheduleAtFixedRate(collector::collect, 5, interval, TimeUnit.SECONDS);
        log.info("[Sentry] 系统资源指标定时采集已启动, interval={}s", interval);
        return collector;
    }

    // ==================== 熔断器 ====================

    /**
     * 为 ELK（Logstash）日志通道装配独立熔断器，名称固定为 {@code elk-logstash}。
     *
     * <p>Logstash 不可用时，熔断器在滑动窗口内失败率超阈值后打开，
     * 使 {@link ElkLogPublisher} 快速失败而非逐条阻塞在网络超时上，保护业务线程。
     * 熔断打开后经过 halfOpenAfterSeconds 自动进入半开态试探恢复。
     * 名称是 {@link #logPublisher} 挑选熔断器的唯一依据，修改需同步。
     *
     * @param properties 监控配置，读取 metrics.circuitBreaker 阈值
     * @return ELK 通道专用熔断器
     */
    @Bean("elkCircuitBreaker")
    @ConditionalOnMissingBean(name = "elkCircuitBreaker")
    @ConditionalOnProperty(prefix = "remi.sentry.logging.elk", name = "enabled", havingValue = "true")
    public CircuitBreaker elkCircuitBreaker(SentryProperties properties) {
        SentryProperties.CircuitBreakerConfig cb = properties.getMetrics().getCircuitBreaker();
        return new CircuitBreaker("elk-logstash",
                cb.getFailureRateThreshold(), cb.getSlidingWindowSize(),
                cb.getHalfOpenAfterSeconds() * 1000L);
    }

    /**
     * 为 Loki 日志通道装配独立熔断器，名称固定为 {@code loki}。
     *
     * <p>与 ELK 熔断器相互独立，保证单一后端故障不会连带切断另一条日志通道。
     * 由于 Loki 是默认日志后端（{@code matchIfMissing = true}），该 Bean 在未做任何配置时也会存在。
     * 名称是 {@link #logPublisher} 挑选熔断器的唯一依据，修改需同步。
     *
     * @param properties 监控配置，读取 metrics.circuitBreaker 阈值
     * @return Loki 通道专用熔断器
     */
    @Bean("lokiCircuitBreaker")
    @ConditionalOnMissingBean(name = "lokiCircuitBreaker")
    @ConditionalOnProperty(prefix = "remi.sentry.logging.loki", name = "enabled", havingValue = "true", matchIfMissing = true)
    public CircuitBreaker lokiCircuitBreaker(SentryProperties properties) {
        SentryProperties.CircuitBreakerConfig cb = properties.getMetrics().getCircuitBreaker();
        return new CircuitBreaker("loki",
                cb.getFailureRateThreshold(), cb.getSlidingWindowSize(),
                cb.getHalfOpenAfterSeconds() * 1000L);
    }

    /**
     * 把容器内所有熔断器的状态与失败计数绑定为 Micrometer Gauge，供 Prometheus 抓取告警。
     *
     * <p>返回值为 {@code void}，本方法只做副作用注册、不产生 Bean 实例；
     * 声明为 {@code @Bean} 仅是为了借助容器保证在所有熔断器 Bean 就绪后被调用一次。
     * {@link MeterRegistry} 缺失时静默跳过，不影响熔断器本身工作。
     *
     * @param circuitBreakers       容器内全部熔断器；无熔断器时不注册任何指标
     * @param meterRegistryProvider Micrometer 注册中心提供者，可能为空
     */
    @Bean
    @ConditionalOnClass(MeterRegistry.class)
    public void circuitBreakerMetricsBinder(ObjectProvider<CircuitBreaker> circuitBreakers,
                                             ObjectProvider<MeterRegistry> meterRegistryProvider) {
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry != null) {
            circuitBreakers.stream().forEach(cb -> {
                String name = cb.getName();
                Gauge.builder("remi.sentry.circuitbreaker.state", cb,
                        st -> st.getState().ordinal())
                        .description("熔断器状态 (0=CLOSED,1=OPEN,2=HALF_OPEN)")
                        .tag("name", name)
                        .register(registry);
                Gauge.builder("remi.sentry.circuitbreaker.failures", cb,
                        CircuitBreaker::getFailureCount)
                        .description("熔断器失败计数")
                        .tag("name", name)
                        .register(registry);
            });
        }
    }


    // ==================== 日志发布 ====================

    /**
     * 按配置组装日志发布链路：单通道直连 / ELK+Loki 双写 / 异步批量包装。
     *
     * <p>组装规则：
     * <ol>
     *   <li>按 {@code logging.elk.enabled}、{@code logging.loki.enabled} 收集启用的通道，
     *       并按熔断器名称（{@code elk-logstash} / {@code loki}）为各通道绑定对应熔断器</li>
     *   <li>一个通道都未启用时兜底使用 Loki 默认配置，保证日志不会完全无出口</li>
     *   <li>多通道时包装为 {@link DualLogPublisher} 双写，
     *       是否要求全部成功由 {@code logging.dual.failOnAllError} 决定</li>
     *   <li>开启 {@code logging.async.enabled} 时再包一层 {@link AsyncLogPublisher}：
     *       有界队列 + 批量 flush + 限速，队列满时丢弃日志而非阻塞业务线程</li>
     * </ol>
     *
     * <p>异步包装器引用保存在字段中，由 {@link #destroy()} 关闭以 flush 残留日志。
     *
     * @param properties      监控配置
     * @param circuitBreakers 容器内熔断器集合，按名称匹配；匹配不到时对应通道无熔断保护
     * @return 日志发布器，永不为 {@code null}
     */
    @Bean
    @ConditionalOnMissingBean(LogPublisher.class)
    public LogPublisher logPublisher(SentryProperties properties,
                                     ObjectProvider<CircuitBreaker> circuitBreakers) {
        List<LogPublisher> publishers = new ArrayList<>();

        SentryProperties.ElkConfig elkConfig = properties.getLogging().getElk();
        if (elkConfig.isEnabled()) {
            CircuitBreaker elkCb = circuitBreakers.stream()
                    .filter(cb -> "elk-logstash".equals(cb.getName()))
                    .findFirst()
                    .orElse(null);
            publishers.add(new ElkLogPublisher(
                    elkConfig.getHost(), elkConfig.getPort(), elkConfig.getProtocol(),
                    elkConfig.getConnectTimeoutMillis(), elkConfig.getReadTimeoutMillis(),
                    elkConfig.getMaxRetryAttempts(), elkCb));
        }

        SentryProperties.LokiConfig lokiConfig = properties.getLogging().getLoki();
        if (lokiConfig.isEnabled()) {
            CircuitBreaker lokiCb = circuitBreakers.stream()
                    .filter(cb -> "loki".equals(cb.getName()))
                    .findFirst()
                    .orElse(null);
            publishers.add(new LokiLogPublisher(
                    lokiConfig.getUrl(), lokiConfig.getConnectTimeoutSeconds(),
                    lokiConfig.getMaxRetryAttempts(), lokiCb));
        }

        if (publishers.isEmpty()) {
            log.warn("[Sentry] 未启用任何日志发布器, 使用 Loki 默认配置");
            CircuitBreaker lokiCb = circuitBreakers.stream()
                    .filter(cb -> "loki".equals(cb.getName()))
                    .findFirst()
                    .orElse(null);
            publishers.add(new LokiLogPublisher(
                    lokiConfig.getUrl(), lokiConfig.getConnectTimeoutSeconds(),
                    lokiConfig.getMaxRetryAttempts(), lokiCb));
        }

        LogPublisher delegate;
        if (publishers.size() == 1) {
            delegate = publishers.get(0);
        } else {
            delegate = new DualLogPublisher(publishers, properties.getLogging().getDual().isFailOnAllError());
        }

        // 异步包装
        SentryProperties.AsyncConfig asyncConfig = properties.getLogging().getAsync();
        if (asyncConfig.isEnabled()) {
            asyncLogPublisher = new AsyncLogPublisher(delegate,
                    asyncConfig.getExecutorQueueCapacity(),
                    asyncConfig.getBatchSize(),
                    asyncConfig.getFlushIntervalMillis(),
                    asyncConfig.getMaxRatePerSecond());
            return asyncLogPublisher;
        }
        return delegate;
    }

    // ==================== 链路追踪 ====================

    /**
     * 按 {@code tracing.primary} 选择链路上下文实现，并逐级降级保证始终有可用实现。
     *
     * <p>降级链路：SkyWalking（需探针已挂载）→ OpenTelemetry（需 SDK 可用）→
     * {@link DefaultTraceContext}（纯 MDC，仅本进程内 traceId 透传，无跨服务串联能力）。
     * 探测通过 {@code Class.forName} 与可用性检查完成，任何探测失败只记录 info 日志、不抛异常，
     * 避免可观测性组件缺失导致应用启动失败。
     *
     * @param properties 监控配置，读取 tracing.primary
     * @return 链路上下文实现，永不为 {@code null}
     */
    @Bean
    @ConditionalOnMissingBean(TraceContext.class)
    public TraceContext traceContext(SentryProperties properties) {
        String primary = properties.getTracing().getPrimary();
        if ("skywalking".equals(primary)) {
            try {
                Class.forName("org.apache.skywalking.apm.toolkit.trace.TraceContext");
                return new SkyWalkingTraceContext();
            } catch (ClassNotFoundException e) {
                log.info("[Sentry] SkyWalking agent 未检测到, 尝试 OpenTelemetry");
            }
        }
        if ("opentelemetry".equals(primary) || "skywalking".equals(primary)) {
            try {
                if (OpenTelemetryTraceContext.isAvailable()) {
                    return new OpenTelemetryTraceContext();
                }
            } catch (Exception e) {
                log.info("[Sentry] OpenTelemetry SDK 不可用, 降级到 DefaultTraceContext");
            }
        }
        return new DefaultTraceContext();
    }

    /**
     * 装配慢链路检测器，对超过阈值的调用打点并附带 traceId 便于反查。
     *
     * @param metricsCollector 慢链路计数写出目标
     * @param traceContext     用于提取当前 traceId
     * @param properties       监控配置，读取 tracing.slowTraceThresholdMillis 作为慢调用阈值
     * @return 慢链路检测器
     */
    @Bean
    @ConditionalOnMissingBean
    public SlowTraceDetector slowTraceDetector(MetricsCollector metricsCollector,
                                                TraceContext traceContext,
                                                SentryProperties properties) {
        return new SlowTraceDetector(metricsCollector, traceContext,
                properties.getTracing().getSlowTraceThresholdMillis());
    }

    // ==================== 告警 ====================

    /**
     * 装配告警发布链路：{@link DefaultAlertPublisher} 外层包一层 {@link AlertConverger} 收敛。
     *
     * <p>返回的是收敛器而非原始发布器，同一告警指纹在静默期
     * （{@code alerting.silencePeriodMillis}）内只会真正外发一次，防止故障期间告警风暴。
     *
     * <p><b>降级策略</b>：{@link NotifyService} 存在时才注册 {@link NotifyAlertHandler}，
     * 把 P0/P1/P2 级告警桥接到钉钉 / 邮件；common-notify 未引入时告警仅落日志，
     * 不抛异常也不阻断启动。P3 及以下级别不外发通知。
     *
     * @param properties           监控配置，读取 alerting 子树
     * @param notifyServiceProvider 通知服务提供者，可能为空（触发仅日志降级）
     * @return 带收敛能力的告警发布器
     */
    @Bean
    @ConditionalOnMissingBean(AlertPublisher.class)
    @ConditionalOnProperty(prefix = "remi.sentry.alerting", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public AlertPublisher alertPublisher(SentryProperties properties,
                                         ObjectProvider<NotifyService> notifyServiceProvider) {
        DefaultAlertPublisher publisher = new DefaultAlertPublisher(
                properties.getAlerting().isLogAlerts());

        // 当 NotifyService 可用时注册通知处理器
        NotifyService notifyService = notifyServiceProvider.getIfAvailable();
        if (notifyService != null) {
            NotifyAlertHandler handler = new NotifyAlertHandler(
                    notifyService,
                    properties.getAlerting().getDingtalkReceiver(),
                    properties.getAlerting().getEmailReceiver());
            publisher.registerHandler(AlertSeverity.P0, handler);
            publisher.registerHandler(AlertSeverity.P1, handler);
            publisher.registerHandler(AlertSeverity.P2, handler);
            log.info("[Sentry] NotifyAlertHandler 已注册, 告警将通过 common-notify 发送");
        } else {
            log.info("[Sentry] NotifyService 不可用, 告警仅记录日志");
        }

        return new AlertConverger(publisher, properties.getAlerting().getSilencePeriodMillis());
    }

    // ==================== SLA ====================

    /**
     * 装配 SLA 指标采集器，把可用率 / 成功率 / 耗时分位统一写入 {@link MetricsCollector}。
     *
     * @param metricsCollector 指标写出目标
     * @return SLA 采集器
     */
    @Bean
    @ConditionalOnMissingBean(SlaCollector.class)
    @ConditionalOnProperty(prefix = "remi.sentry.sla", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public SlaCollector slaCollector(MetricsCollector metricsCollector) {
        return new DefaultSlaCollector(metricsCollector);
    }

    /**
     * 装配 SLA 埋点切面，拦截标注了 SLA 注解的方法自动统计成功率与耗时。
     *
     * <p>切面运行在业务调用链路上，采集逻辑内部已做异常吞噬，
     * 采集失败不会影响原方法返回值与异常传播。
     *
     * @param slaCollector SLA 采集器
     * @return SLA 埋点切面
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "remi.sentry.sla", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public SlaMetricAspect slaMetricAspect(SlaCollector slaCollector) {
        return new SlaMetricAspect(slaCollector);
    }

    // ==================== 自监控指标 ====================

    /**
     * 装配 Sentry 自监控器，解决"监控系统本身挂了没人知道"的问题。
     *
     * <p>周期性把各组件可用性、异步日志队列积压与丢弃数上报为 Gauge，
     * Prometheus 侧据此配置"监控失联"告警。
     *
     * @param metricsCollector 指标采集器，同时也是被监控对象
     * @param logPublisher     日志发布器，同时也是被监控对象
     * @param alertPublisher   告警发布器，同时也是被监控对象
     * @param properties       监控配置（保留参数，便于后续扩展自监控开关）
     * @return 自监控器
     */
    @Bean
    @ConditionalOnMissingBean
    public SentrySelfMonitor sentrySelfMonitor(MetricsCollector metricsCollector,
                                                 LogPublisher logPublisher,
                                                 AlertPublisher alertPublisher,
                                                 SentryProperties properties) {
        return new SentrySelfMonitor(metricsCollector, logPublisher, alertPublisher);
    }

    // ==================== 健康检查 ====================

    /**
     * 装配 Sentry 组件健康探针，聚合指标 / 日志 / 链路三条通道的可用性到 Actuator health 端点。
     *
     * <p>仅在 Actuator health 相关类存在时装配，避免非 Web 或未引入 Actuator 的模块启动失败。
     *
     * @param metricsCollector 指标采集器
     * @param logPublisher     日志发布器
     * @param traceContext     链路上下文
     * @return 健康探针
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
    public SentryHealthIndicator sentryHealthIndicator(MetricsCollector metricsCollector,
                                                        LogPublisher logPublisher,
                                                        TraceContext traceContext) {
        return new SentryHealthIndicator(metricsCollector, logPublisher, traceContext);
    }

    /**
     * 装配系统资源健康探针，把 CPU / 内存 / 磁盘水位映射为 health 状态。
     *
     * <p>资源超过阈值时该探针会置 DOWN，进而影响 Kubernetes readiness 判定，
     * 使实例暂时摘出流量，属于有实际影响的探针，调整阈值需评估摘流风险。
     *
     * @param systemMetricsCollector 系统指标采集器，提供最近一次采集快照
     * @return 系统资源健康探针
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
    @ConditionalOnProperty(prefix = "remi.sentry.metrics", name = "enable-system-metrics",
            havingValue = "true", matchIfMissing = true)
    public SystemResourceHealthIndicator systemResourceHealthIndicator(
            SystemMetricsCollector systemMetricsCollector) {
        return new SystemResourceHealthIndicator(systemMetricsCollector);
    }

    // ==================== 生命周期 ====================

    /**
     * 容器关闭时释放监控侧资源，确保缓冲中的日志不丢失。
     *
     * <p>先关闭异步日志发布器触发残留队列 flush，再优雅停止系统指标采集线程：
     * 最多等待 5 秒，超时则强制 {@code shutdownNow}；等待被中断时同样强制关闭
     * 并恢复线程中断标志，避免吞掉中断信号。
     */
    @PreDestroy
    public void destroy() {
        if (asyncLogPublisher != null) {
            asyncLogPublisher.close();
        }
        if (systemMetricsScheduler != null) {
            systemMetricsScheduler.shutdown();
            try {
                if (!systemMetricsScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    systemMetricsScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                systemMetricsScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log.info("[Sentry] 系统资源指标定时采集已停止");
        }
    }

    /**
     * 自监控指标上报器
     *
     * <p>定时上报 Sentry 各组件的可用性指标到 MetricsCollector，
     * 供 Prometheus 告警规则使用。
     */
    @Slf4j
    public static class SentrySelfMonitor {

        private final MetricsCollector metricsCollector;
        private final LogPublisher logPublisher;
        private final AlertPublisher alertPublisher;

        public SentrySelfMonitor(MetricsCollector metricsCollector,
                                  LogPublisher logPublisher,
                                  AlertPublisher alertPublisher) {
            this.metricsCollector = metricsCollector;
            this.logPublisher = logPublisher;
            this.alertPublisher = alertPublisher;
            log.info("[Sentry] SentrySelfMonitor 初始化完成");
        }

        /**
         * 每 15 秒上报一次 Sentry 各组件的自监控 Gauge 指标。
         *
         * <p>上报内容包括：指标采集器 / 日志发布器 / 告警发布器可用性（1=可用，0=不可用）；
         * 异步日志的队列积压数、丢弃总数、已发布总数；告警收敛的抑制率与总量。
         * 其中日志丢弃总数是判断"日志后端扛不住"的关键信号，应配置告警。
         *
         * <p>整个方法用 try-catch 兜底并只打 debug 日志：自监控失败绝不能影响业务，
         * 也不能因为反复打印 error 日志形成新的噪音源。
         */
        @Scheduled(fixedRate = 15000)
        public void reportSelfMetrics() {
            try {
                if (metricsCollector != null) {
                    metricsCollector.setGauge("remi.sentry.metrics.available",
                            "指标采集器可用性", null, metricsCollector.isAvailable() ? 1.0 : 0.0);
                }
                if (logPublisher != null) {
                    metricsCollector.setGauge("remi.sentry.logging.available",
                            "日志发布器可用性", null, logPublisher.isAvailable() ? 1.0 : 0.0);
                    if (logPublisher instanceof AsyncLogPublisher async) {
                        metricsCollector.setGauge("remi.sentry.logging.queue_size",
                                "异步日志队列积压数", null, async.getQueueSize());
                        metricsCollector.setGauge("remi.sentry.logging.dropped_total",
                                "异步日志丢弃总数", null, async.getDroppedCount());
                        metricsCollector.setGauge("remi.sentry.logging.published_total",
                                "异步日志已发布总数", null, async.getTotalPublished());
                    }
                }
                if (alertPublisher != null) {
                    metricsCollector.setGauge("remi.sentry.alerting.available",
                            "告警发布器可用性", null, alertPublisher.isAvailable() ? 1.0 : 0.0);
                }
                if (alertPublisher instanceof AlertConverger converger) {
                    metricsCollector.setGauge("remi.sentry.alert.suppression_rate",
                            "告警抑制率", null, converger.getSuppressionRate());
                    metricsCollector.setGauge("remi.sentry.alert.total",
                            "告警总数", null, converger.getTotalAlerts());
                    metricsCollector.setGauge("remi.sentry.alert.suppressed",
                            "被抑制告警数", null, converger.getSuppressedAlerts());
                }
            } catch (Exception e) {
                log.debug("[Sentry] 自监控指标上报异常: {}", e.getMessage());
            }
        }
    }
}
