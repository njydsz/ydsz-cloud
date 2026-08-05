package com.remisoft.common.core.config;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Core 模块健康检查指示器。
 *
 * <p>暴露 Core 模块的存活状态与版本信息，便于监控系统探测。
 * 运行时配置校验（分页参数、i18n 解析器等）已前移至启动时的
 * {@code @Validated} + {@code @AssertTrue} 校验，健康端点仅负责存活探测。</p>
 *
 * <p>响应示例：</p>
 * <pre>{@code
 * {
 *   "status": "UP",
 *   "components": {
 *     "remiCore": {
 *       "status": "UP",
 *       "details": { "version": "1.0.0" }
 *     }
 *   }
 * }
 * }</pre>
 *
 * @author remi-team
 * @since 1.4.0
 * @see org.springframework.boot.health.contributor.HealthContributor
 */
public class CoreHealthIndicator implements HealthIndicator {

    private static final String VERSION = "1.0.0";

    @Override
    public Health health() {
        return Health.up()
                .withDetail("version", VERSION)
                .build();
    }
}
