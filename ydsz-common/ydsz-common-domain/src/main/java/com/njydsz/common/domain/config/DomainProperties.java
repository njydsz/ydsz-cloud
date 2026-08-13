package com.njydsz.common.domain.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Domain 模块统一配置属性
 *
 * <p>集中管理 domain 模块的所有配置项。对齐 Spring Boot 配置命名风格（ydsz.domain.分组.属性）。
 *
 * <p><b>配置项（application.yml）：</b>
 * <pre>{@code
 * ydsz:
 *   domain:
 *     page:
 *       cursor-warning-threshold: 10000         # 触发游标警告的 offset 阈值（默认 10000）
 *       cursor-reject-threshold: 50000          # 强制拒绝的 offset 阈值（默认 50000）
 * }</pre>
 *
 * <p><b>v1.8.0</b>：移除失效的 PageQueryFactory 运行时注入，阈值由消费方通过 {@link DomainProperties} 直接读取。
 * <p><b>v1.8.0</b>：移除从不被消费的 tree / idempotent 配置分组（TreeDepthExceededException、
 * IdempotentOperation 等对应能力未落地，相关幽灵引用一并清理）。
 * <p><b>v1.4.0</b>：SpEL 评估器缓存配置（spel.cache-*）随 DAG 引擎迁移至 ydsz-cronjob 模块。
 *
 * @author ydsz-team
 * @since 1.3.0
 * @since 1.6.0 扩展为嵌套分组（page），对齐 Spring Boot 配置命名风格
 * @since 1.7.0 移除 enabled 虚假开关，改为实例级注入
 */
@Data
@ConfigurationProperties(prefix = "ydsz.domain")
public class DomainProperties {

    /**
     * 分页查询配置
     */
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
