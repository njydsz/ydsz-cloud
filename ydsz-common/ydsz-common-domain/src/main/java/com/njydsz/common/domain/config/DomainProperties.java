package com.njydsz.common.domain.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

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
 *       max-search-key-length: 200              # 搜索关键字最长长度（1~500，默认 200）
 *       cursor-warning-threshold: 10000         # 触发游标警告的 offset 阈值（默认 10000）
 *       cursor-reject-threshold: 50000          # 强制拒绝的 offset 阈值（默认 50000）
 *     tree:
 *       max-depth: 10                           # 树构建最大深度限制（1~100，默认 10）
 *     idempotent:
 *       default-expire-seconds: 86400           # 幂等键默认过期（秒，默认 86400=24h）
 * }</pre>
 *
 * <p><b>v1.7.0</b>：移除虚假的 `enabled` 开关，配置通过 {@code PageQueryFactory} 实例级注入。
 * <p><b>v1.4.0</b>：SpEL 评估器缓存配置（spel.cache-*）随 DAG 引擎迁移至 ydsz-cronjob 模块。
 *
 * @author ydsz-team
 * @since 1.3.0
 * @since 1.6.0 扩展为嵌套分组（page/tree/idempotent），对齐 Spring Boot 配置命名风格
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
     * 树形结构配置
     */
    private Tree tree = new Tree();

    /**
     * 幂等操作配置
     */
    private Idempotent idempotent = new Idempotent();

    /**
     * 分页查询配置
     */
    @Data
    public static class Page {
        /**
         * 搜索关键字最长长度（1~500，默认 200）
         */
        private int maxSearchKeyLength = 200;

        /**
         * 触发游标警告的 offset 阈值（默认 10000）
         *
         * <p>超过此值的深度分页将在日志中发出 WARN，提醒改用游标分页。
         * <p>消费方：{@code com.njydsz.common.jdbc.interceptor.SafeQueryInnerInterceptor}
         */
        private long cursorWarningThreshold = 10000L;

        /**
         * 强制拒绝的 offset 阈值（默认 50000）
         *
         * <p>超过此值的深度分页将被直接拒绝，抛出 DeepPaginationException，
         * 防止慢查询拖垮数据库。必须改用游标分页。
         * <p>消费方：{@code com.njydsz.common.jdbc.interceptor.SafeQueryInnerInterceptor}
         */
        private long cursorRejectThreshold = 50000L;
    }

    /**
     * 树形结构配置
     */
    @Data
    public static class Tree {
        /**
         * 树构建最大深度限制（1~100，默认 10）
         *
         * <p>超过此深度的树构建将抛出 TreeDepthExceededException，防止递归过深导致栈溢出。
         */
        private int maxDepth = 10;
    }

    /**
     * 幂等操作配置
     */
    @Data
    public static class Idempotent {
        /**
         * 幂等键默认过期时间（秒，默认 86400 = 24 小时）
         *
         * <p>与 Stripe / 支付宝 / 微信支付业界惯例对齐。业务方可通过
         * {@link com.njydsz.common.domain.contract.IdempotentOperation#getExpireSeconds()} 覆盖单个操作的过期时间。
         */
        private long defaultExpireSeconds = 86400L;
    }
}
