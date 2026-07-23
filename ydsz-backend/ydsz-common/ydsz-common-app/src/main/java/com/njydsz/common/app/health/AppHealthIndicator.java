package com.njydsz.common.app.health;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.njydsz.common.app.config.AppSignatureProperties;
import com.njydsz.common.app.metrics.AppMetrics;

import lombok.extern.slf4j.Slf4j;

/**
 * App 模块健康检查指示器
 *
 * <p>检测 App 端各子系统的健康状态，暴露 {@code /actuator/health/app} 端点。
 *
 * <p><b>检测项：</b>
 * <ul>
 *   <li>签名验证：启用状态、密钥配置、路径白名单数量</li>
 *   <li>Nonce 防重放：Redis 连通性、响应时间</li>
 *   <li>指标采集：Micrometer 是否可用</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnProperty(prefix = "ydsz.app", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AppHealthIndicator implements HealthIndicator {

    private final AppSignatureProperties signatureProperties;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final ObjectProvider<AppMetrics> appMetricsProvider;

    /**
     * 构造方法
     *
     * @param signatureProperties 签名配置属性
     * @param redisTemplateProvider Redis 模板（可选依赖，用于 Nonce 防重放）
     * @param appMetricsProvider    App 指标采集器（可选依赖）
     */
    public AppHealthIndicator(AppSignatureProperties signatureProperties,
                               ObjectProvider<StringRedisTemplate> redisTemplateProvider,
                               ObjectProvider<AppMetrics> appMetricsProvider) {
        this.signatureProperties = signatureProperties;
        this.redisTemplateProvider = redisTemplateProvider;
        this.appMetricsProvider = appMetricsProvider;
    }

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("module", "app");

        // 签名验证状态
        Map<String, Object> signatureStatus = new LinkedHashMap<>();
        signatureStatus.put("enabled", signatureProperties.isEnabled());
        signatureStatus.put("algorithm", signatureProperties.getAlgorithm());
        signatureStatus.put("hasSecret", signatureProperties.hasAnySecretConfigured());
        signatureStatus.put("appSecretsCount", signatureProperties.getAppSecrets().size());
        signatureStatus.put("ignoreUrlsCount", signatureProperties.getIgnoreUrls().size());
        signatureStatus.put("timestampToleranceMs", signatureProperties.getTimestampTolerance());
        signatureStatus.put("nonceCacheTtlSec", signatureProperties.getNonceCacheTtl());
        details.put("signature", signatureStatus);

        // Redis 连通性（Nonce 防重放依赖）
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate != null) {
            try {
                long startTime = System.currentTimeMillis();
                RedisConnectionFactory factory = redisTemplate.getConnectionFactory();
                if (factory != null) {
                    RedisConnection connection = factory.getConnection();
                    try {
                        String pong = connection.ping();
                        long responseTime = System.currentTimeMillis() - startTime;
                        signatureStatus.put("redis", "PONG".equalsIgnoreCase(pong) ? "connected" : "unexpected: " + pong);
                        signatureStatus.put("redisResponseTimeMs", responseTime);
                    } finally {
                        connection.close();
                    }
                }
            } catch (Exception e) {
                signatureStatus.put("redis", "disconnected: " + e.getMessage());
                log.warn("App 模块 Redis 健康检查失败: {}", e.getMessage());
            }
        } else {
            signatureStatus.put("redis", "not configured (nonce anti-replay degraded)");
        }

        // 指标采集状态
        AppMetrics metrics = appMetricsProvider.getIfAvailable();
        details.put("metrics", metrics != null ? "enabled" : "disabled");

        // 如果签名验证启用但密钥未配置，标记为 DOWN
        if (signatureProperties.isEnabled() && !signatureProperties.hasAnySecretConfigured()) {
            return Health.down()
                    .withDetail("module", "app")
                    .withDetail("error", "签名验证已启用但未配置任何密钥")
                    .withDetails(details)
                    .build();
        }

        return Health.up()
                .withDetails(details)
                .build();
    }
}
