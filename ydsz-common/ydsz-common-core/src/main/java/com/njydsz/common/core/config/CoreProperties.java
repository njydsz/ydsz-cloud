package com.njydsz.common.core.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.validation.annotation.Validated;

import lombok.Data;

/**
 * Core 模块运行时配置属性。
 *
 * <p>通过 {@code ydsz.core.*} 前缀绑定 application.yml 中的配置项，
 * 提供分页参数运行时覆盖能力。</p>
 *
 * <h3>配置示例</h3>
 * <pre>{@code
 * ydsz:
 *   core:
 *     enabled: true
 *     max-page-size: 1000
 *     default-page-size: 20
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see CoreAutoConfiguration
 * @see com.njydsz.common.core.constant.PageConstants
 */
@Data
@ConfigurationProperties(prefix = "ydsz.core")
@Validated
public class CoreProperties {

    /**
     * 是否启用 Core 模块自动配置。
     *
     * <p>设为 {@code false} 可完全禁用 CoreAutoConfiguration 注册的 Bean
     * （RequestContext 工具类仍可直接使用，其为纯静态工具不依赖自动配置）。</p>
     */
    private boolean enabled = true;

    /**
     * 运行时最大每页记录数上限。
     *
     * <p>由 {@link com.njydsz.common.core.constant.PageConstants#getMaxPageSize()} 读取，
     * 防止客户端一次性拉取过多数据导致内存/CPS 压力。默认 1000。</p>
     */
    @Min(1)
    @Max(5000)
    private int maxPageSize = 1000;

    /**
     * 运行时默认每页记录数。
     *
     * <p>由 {@link com.njydsz.common.core.constant.PageConstants#getDefaultPageSize()} 读取，
     * 分页查询未指定 pageSize 时使用。默认 20。</p>
     */
    @Min(1)
    @Max(5000)
    private int defaultPageSize = 20;

    /**
     * 租户 MDC 过滤器执行顺序。
     *
     * <p>默认 {@code HIGHEST_PRECEDENCE + 100}，高于业务过滤器。
     * 由 web/auth 模块的 FilterRegistrationBean 消费。</p>
     */
    private int tenantMdcFilterOrder = Ordered.HIGHEST_PRECEDENCE + 100;

    /**
     * 校验分页范围合法性：defaultPageSize 不应大于 maxPageSize。
     *
     * <p>该校验在应用启动时执行（@Validated + @AssertTrue），
     * 配置不合法时快速失败阻止启动。</p>
     */
    @AssertTrue(message = "ydsz.core.default-page-size must be <= ydsz.core.max-page-size")
    public boolean isPaginationRangeValid() {
        return defaultPageSize <= maxPageSize;
    }
}
