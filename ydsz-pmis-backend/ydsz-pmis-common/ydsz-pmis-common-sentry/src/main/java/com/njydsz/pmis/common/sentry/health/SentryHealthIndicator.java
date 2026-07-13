package com.njydsz.pmis.common.sentry.health;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.njydsz.pmis.common.sentry.spi.LogPublisher;
import com.njydsz.pmis.common.sentry.spi.MetricsCollector;
import com.njydsz.pmis.common.sentry.spi.TraceContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Sentry 模块整体健康检查
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Slf4j
@RequiredArgsConstructor
public class SentryHealthIndicator implements HealthIndicator {

    private final MetricsCollector metricsCollector;
    private final LogPublisher logPublisher;
    private final TraceContext traceContext;

    @Override
    public Health health() {
        Health.Builder builder = Health.up();

        if (metricsCollector != null) {
            builder.withDetail("metrics.collector", metricsCollector.getName())
                    .withDetail("metrics.available", metricsCollector.isAvailable());
        }

        if (logPublisher != null) {
            builder.withDetail("logging.publisher", logPublisher.getName())
                    .withDetail("logging.scheme", logPublisher.getScheme())
                    .withDetail("logging.available", logPublisher.isAvailable());
            if (!logPublisher.isAvailable()) {
                builder.down();
            }
        }

        if (traceContext != null) {
            builder.withDetail("tracing.tracer", traceContext.getTracerName())
                    .withDetail("tracing.tracing", traceContext.isTracing());
        }

        return builder.build();
    }
}
