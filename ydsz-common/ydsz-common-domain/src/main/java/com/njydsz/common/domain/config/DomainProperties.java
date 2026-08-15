package com.njydsz.common.domain.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Domain 模块统一配置属性。
 *
 * <p>集中管理 domain 模块的所有配置项（{@code ydsz.domain.*}）。
 *
 * @author ydsz-team
 * @since 1.10.0
 */
@Data
@ConfigurationProperties(prefix = "ydsz.domain")
public class DomainProperties {

    /** 是否启用 domain 模块自动装配 */
    private boolean enabled = true;

    /** 分页查询配置 */
    private Page page = new Page();

    /**
     * 分页查询配置
     */
    @Data
    public static class Page {

        /** 默认触发游标警告的 offset 阈值。 */
        private static final long DEFAULT_CURSOR_WARNING_THRESHOLD = 10000L;

        /** 默认强制拒绝的 offset 阈值。 */
        private static final long DEFAULT_CURSOR_REJECT_THRESHOLD = 50000L;

        /**
         * 触发游标警告的 offset 阈值（默认 10000）
         *
         * <p>超过此值的深度分页将在日志中发出 WARN，提醒改用游标分页。
         * <p>消费方：{@code com.njydsz.common.jdbc.interceptor.SafeQueryInnerInterceptor}
         */
        private long cursorWarningThreshold = DEFAULT_CURSOR_WARNING_THRESHOLD;

        /**
         * 强制拒绝的 offset 阈值（默认 50000）
         *
         * <p>超过此值的深度分页将被直接拒绝，抛出 DeepPaginationException，
         * 防止慢查询拖垮数据库。必须改用游标分页。
         * <p>消费方：{@code com.njydsz.common.jdbc.interceptor.SafeQueryInnerInterceptor}
         */
        private long cursorRejectThreshold = DEFAULT_CURSOR_REJECT_THRESHOLD;
    }
}
