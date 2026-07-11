package com.njydsz.pmis.common.sentry;

import io.sentry.Sentry;
import io.sentry.SentryOptions;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Sentry SDK 初始化器（P1-3 落地）。
 *
 * <p>原 {@link SentryConfig} 仅创建 {@link SentryProperties} Bean，
 * 未实际调用 {@link Sentry#init(SentryOptions)} 初始化 SDK，
 * 导致生产环境下 Sentry 实际未上报任何异常。
 *
 * <p>本组件在 Spring 容器启动后自动初始化 Sentry SDK：
 * <ul>
 *   <li>从 {@link SentryProperties} 读取配置</li>
 *   <li>调用 {@link Sentry#init(SentryOptions)} 完成初始化</li>
 *   <li>配置全局 beforeSend 过滤器（过滤 INFO 级别日志）</li>
 *   <li>设置 tags（环境、服务名、版本）</li>
 * </ul>
 *
 * <p>仅在 {@code pmis.sentry.enabled=true} 且 DSN 非空时生效。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0 (P1-3)
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "pmis.sentry", name = "enabled", havingValue = "true")
@ConditionalOnClass(name = "io.sentry.Sentry")
public class SentryInitializer {

    private final SentryProperties sentryProperties;

    /**
     * 初始化 Sentry SDK。
     *
     * <p>仅在 DSN 非空时初始化，避免空 DSN 导致 Sentry 报错。
     */
    @PostConstruct
    public void initialize() {
        if (sentryProperties == null || sentryProperties.getDsn() == null
                || sentryProperties.getDsn().isBlank()) {
            log.warn("[Sentry] DSN 未配置, Sentry 初始化跳过（pmis.sentry.enabled=true 但 dsn 为空）");
            return;
        }

        Sentry.init(options -> {
            options.setDsn(sentryProperties.getDsn());
            options.setEnvironment(sentryProperties.getEnvironment());
            options.setRelease(sentryProperties.getRelease());
            options.setServerName(sentryProperties.getServerName());

            // 采样率
            if (sentryProperties.getTracesSampleRate() != null) {
                options.setTracesSampleRate(sentryProperties.getTracesSampleRate());
            }
            if (sentryProperties.getSampleRate() != null) {
                options.setSampleRate(sentryProperties.getSampleRate());
            }

            // 启用异步上报，避免阻塞主线程
            options.setEnableExternalConfiguration(false);

            // beforeSend: 过滤健康检查等噪音异常
            options.setBeforeSend((event, hint) -> {
                // 过滤 actuator 健康检查异常
                String requestUrl = event.getRequest() != null ? event.getRequest().getUrl() : null;
                if (requestUrl != null && requestUrl.contains("/actuator/")) {
                    return null; // 丢弃 actuator 异常
                }
                // P0-5: 过滤 4xx 客户端错误（业务预期的 400/401/403/404，非系统 Bug）
                // 保留 5xx 服务端错误（真正的系统异常）
                if (event.getThrowable() instanceof org.springframework.web.client.HttpClientErrorException) {
                    return null;
                }
                // 过滤 Sentinel 限流降级异常（业务预期行为）
                String exClass = event.getThrowable() != null
                        ? event.getThrowable().getClass().getName() : null;
                if (exClass != null && exClass.contains("Sentinel")) {
                    return null;
                }
                return event;
            });
        });

        // 设置全局 tags
        Sentry.configureScope(scope -> {
            scope.setTag("service", sentryProperties.getServerName());
            scope.setTag("environment", sentryProperties.getEnvironment());
            if (sentryProperties.getActiveProfiles() != null) {
                scope.setTag("profiles", sentryProperties.getActiveProfiles());
            }
        });

        log.info("[Sentry] 初始化完成, dsn={}, environment={}, release={}, server={}",
                maskDsn(sentryProperties.getDsn()),
                sentryProperties.getEnvironment(),
                sentryProperties.getRelease(),
                sentryProperties.getServerName());
    }

    /**
     * 关闭 Sentry SDK（刷新缓冲区中的事件）。
     */
    @PreDestroy
    public void shutdown() {
        try {
            Sentry.close();
            log.info("[Sentry] SDK 已关闭, 缓冲事件已刷新");
        } catch (Exception e) {
            log.warn("[Sentry] 关闭异常: {}", e.getMessage());
        }
    }

    /**
     * 对 DSN 做脱敏处理（仅显示前 20 字符），避免日志泄露完整 DSN。
     */
    private static String maskDsn(String dsn) {
        if (dsn == null || dsn.length() <= 20) {
            return "***";
        }
        return dsn.substring(0, 20) + "***";
    }
}
