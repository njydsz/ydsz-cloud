package com.njydsz.common.sentry.config;

import java.util.ArrayList;
import java.util.List;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.njydsz.common.sentry.domain.AlertSeverity;
import com.njydsz.common.sentry.logging.AsyncLogPublisher;
import com.njydsz.common.sentry.logging.DualLogPublisher;
import com.njydsz.common.sentry.logging.ElkLogPublisher;
import com.njydsz.common.sentry.logging.LokiLogPublisher;
import com.njydsz.common.sentry.logging.NoOpLogPublisher;
import com.njydsz.common.sentry.resilience.CircuitBreaker;
import com.njydsz.common.sentry.spi.LogPublisher;

/**
 * 日志发布器自动配置。
 *
 * <p>按配置组装日志发布链路：单通道直连 / ELK+Loki 双写 / 异步批量包装。
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
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(MetricsAutoConfiguration.class)
@EnableConfigurationProperties(SentryProperties.class)
public class LoggingAutoConfiguration {

    private AsyncLogPublisher asyncLogPublisher;

    /**
     * 按配置组装日志发布链路。
     *
     * @param properties      监控配置
     * @param circuitBreakers 容器内熔断器集合，按名称匹配
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
            // v2.0.0 变更：不再隐式降级到 Loki，改为使用 NoOpLogPublisher
            // 避免日志被意外发送到未配置的 Loki 实例
            log.warn("[Sentry] 未启用任何日志发布器（elk.enabled=false, loki.enabled=false），"
                    + "使用 NoOpLogPublisher，日志将不会上报到任何外部系统");
            return NoOpLogPublisher.INSTANCE;
        }

        LogPublisher delegate;
        if (publishers.size() == 1) {
            delegate = publishers.get(0);
        } else {
            delegate = new DualLogPublisher(publishers,
                    properties.getLogging().getDual().isFailOnAllError());
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

    /**
     * 容器关闭时释放日志发布器资源。
     */
    @PreDestroy
    public void destroy() {
        if (asyncLogPublisher != null) {
            asyncLogPublisher.close();
        }
    }
}
