package com.njydsz.common.core.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.validation.annotation.Validated;

import lombok.Data;

/**
 * Core 模块配置属性。
 *
 * <p>仅包含分页相关的核心配置。所有配置项均通过 JSR-303 校验注解
 * 进行启动时校验，配置非法时应用启动失败（fail-fast）。
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * ydsz:
 *   core:
 *     enabled: true
 *     max-page-size: 1000        # 1-5000
 *     default-page-size: 20      # 1-5000
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "ydsz.core")
@Validated
public class CoreProperties {

    /** 最大每页记录数上限（1-5000），防止一次性拉取过多数据导致性能问题 */
    @Min(1)
    @Max(5000)
    private int maxPageSize = 1000;

    /** 默认每页记录数（≥1），分页查询未指定 pageSize 时使用 */
    @Min(1)
    @Max(5000)
    private int defaultPageSize = 20;

    /** 租户 MDC 过滤器优先级，默认高于业务过滤器 */
    private int tenantMdcFilterOrder = Ordered.HIGHEST_PRECEDENCE + 100;

    /**
     * 交叉校验：默认每页记录数不能超过最大每页记录数上限。
     *
     * <p>避免出现 {@code default-page-size > max-page-size} 的非法组合，
     * 否则归一化逻辑会将默认值截断到上限，产生反直觉行为。</p>
     *
     * @return true=配置合法
     */
    @AssertTrue(message = "default-page-size must be <= max-page-size")
    public boolean isPaginationRangeValid() {
        return defaultPageSize <= maxPageSize;
    }
}
