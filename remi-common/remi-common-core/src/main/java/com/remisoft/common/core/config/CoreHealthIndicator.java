package com.remisoft.common.core.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.remisoft.common.core.constant.PageConstants;
import com.remisoft.common.core.response.BaseResponse;

/**
 * Core 模块健康检查指示器。
 *
 * <p>暴露 Core 模块的运行状态，便于监控系统探测和运维排查。
 * 检查项包括：国际化解析器注册状态、分页配置运行时同步状态。</p>
 *
 * <p><b>使用示例（application.yml）：</b></p>
 * <pre>{@code
 * management:
 *   endpoint:
 *     health:
 *       show-details: always
 * }</pre>
 *
 * <p>响应示例：</p>
 * <pre>{@code
 * {
 *   "status": "UP",
 *   "components": {
 *     "remiCore": {
 *       "status": "UP",
 *       "details": {
 *         "i18nResolverRegistered": true,
 *         "maxPageSize": 1000,
 *         "defaultPageSize": 20,
 *         "paginationConfigValid": true
 *       }
 *     }
 *   }
 * }
 * }</pre>
 *
 * @author remi-team
 * @since 1.4.0
 * @see org.springframework.boot.actuate.health.HealthContributor
 */
public class CoreHealthIndicator implements HealthIndicator {

    private final CoreProperties properties;

    public CoreHealthIndicator(CoreProperties properties) {
        this.properties = properties;
    }

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>(4);

        boolean resolverRegistered = BaseResponse.isResolverRegistered();
        details.put("i18nResolverRegistered", resolverRegistered);

        details.put("maxPageSize", PageConstants.getMaxPageSize());
        details.put("defaultPageSize", PageConstants.getDefaultPageSize());

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
}
