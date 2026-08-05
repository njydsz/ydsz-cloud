package com.remisoft.common.core.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.remisoft.common.core.response.BaseResponse;

/**
 * Core 模块健康检查指示器。
 *
 * <p>暴露 Core 模块的运行状态，便于监控系统探测和运维排查。
 * 检查项包括：国际化解析器注册状态、分页配置运行时同步状态。</p>
 *
 * <p><b>使用示例（Prometheus/Grafana）：</b></p>
 * <pre>{@code
 * management:
 *   endpoints:
 *     web:
 *       exposure:
 *         include: health
 *   endpoint:
 *     health:
 *       show-details: always
 * }</pre>
 *
 * @author remi-team
 * @since 1.7.0
 * @see CoreAutoConfiguration
 */
public class CoreHealthIndicator implements HealthIndicator {

    private final CoreProperties properties;

    public CoreHealthIndicator(CoreProperties properties) {
        this.properties = properties;
    }

    @Override
    public Health health() {
        Map<String, Object> details = new HashMap<>(4);

        // 国际化解析器注册状态
        boolean resolverRegistered = BaseResponse.isResolverRegistered();
        details.put("i18nResolverRegistered", resolverRegistered);

        // 分页配置状态
        details.put("maxPageSize", PageConstantsRuntimeInfo.getMaxPageSize());
        details.put("defaultPageSize", PageConstantsRuntimeInfo.getDefaultPageSize());

        // 配置范围合理性
        boolean configValid = properties.getMaxPageSize() >= properties.getDefaultPageSize();
        details.put("paginationConfigValid", configValid);

        if (!configValid) {
            return Health.down()
                    .withDetails(details)
                    .withDetail("error", "maxPageSize < defaultPageSize")
                    .build();
        }

        return Health.up()
                .withDetails(details)
                .build();
    }

    /**
     * 运行时配置信息内部类，避免直接访问 PageConstants 单例。
     */
    static class PageConstantsRuntimeInfo {
        static int getMaxPageSize() {
            return com.remisoft.common.core.constant.PageConstants.getMaxPageSize();
        }

        static int getDefaultPageSize() {
            return com.remisoft.common.core.constant.PageConstants.getDefaultPageSize();
        }
    }
}
