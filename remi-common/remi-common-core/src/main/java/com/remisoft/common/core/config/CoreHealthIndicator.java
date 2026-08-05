package com.remisoft.common.core.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;

import com.remisoft.common.core.constant.PageConstants;
import com.remisoft.common.core.response.BaseResponse;

/**
 * Core 模块健康检查指示器。
 *
 * <p>暴露 Core 模块的运行状态，便于监控系统探测和运维排查。
 * 检查项包括：国际化解析器注册状态、分页配置运行时同步状态、
 * 以及 {@link PageConstants} 是否已注入运行时配置。</p>
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
 * <p><b>状态说明：</b></p>
 * <ul>
 *   <li>{@code UP} — 国际化解析器已注册、分页配置已注入运行时值、配置范围合理</li>
 *   <li>{@code UNKNOWN} — 分页配置未由 CoreAutoConfiguration 注入，
 *       回退到编译期常量或硬编码默认值。通常是由于 {@code remi.core.enabled=false}
 *       或 core 模块加载不完整，需排查应用上下文</li>
 *   <li>{@code DOWN} — 分页范围非法（maxPageSize &lt; defaultPageSize）</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.7.0
 * @see CoreAutoConfiguration
 * @see PageConstants#isInitialized()
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
        details.put("maxPageSize", PageConstants.getMaxPageSize());
        details.put("defaultPageSize", PageConstants.getDefaultPageSize());

        // 配置范围合理性
        boolean configValid = properties.getMaxPageSize() >= properties.getDefaultPageSize();
        details.put("paginationConfigValid", configValid);

        if (!configValid) {
            return Health.down()
                    .withDetails(details)
                    .withDetail("error", "maxPageSize < defaultPageSize")
                    .build();
        }

        // 检测 PageConstants 是否已由 CoreAutoConfiguration 注入运行时配置
        boolean pageConstantsInitialized = PageConstants.isInitialized();
        details.put("pageConstantsInitialized", pageConstantsInitialized);
        if (!pageConstantsInitialized) {
            return new Health.Builder(Status.UNKNOWN, details)
                    .withDetail("warning", "PageConstants not initialized by CoreAutoConfiguration; "
                            + "falling back to compile-time defaults. "
                            + "Check remi.core.enabled or context loader configuration.")
                    .build();
        }

        return Health.up()
                .withDetails(details)
                .build();
    }
}
