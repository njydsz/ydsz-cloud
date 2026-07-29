package com.njydsz.common.domain.config;

import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.Data;

/**
 * Domain 模块统一配置属性
 *
 * <p>集中管理 domain 模块的所有配置项，包括模块开关、事件发布、SpEL 评估器等。
 *
 * <p><b>配置项（application.yml）：</b>
 * <pre>{@code
 * ydsz:
 *   domain:
 *     enabled: true                  # 启用 domain 模块自动配置（默认 true）
 *     event:
 *       async-enabled: true          # 是否启用异步事件发布（需 TaskExecutor，默认 true）
 *       default-phase: AFTER_COMMIT  # 默认事务发布阶段（默认 AFTER_COMMIT）
 *     spel:
 *       cache-enabled: true          # 是否启用 SpEL 表达式缓存（默认 true）
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.1.0
 */
@Data
@Validated
@ConfigurationProperties(prefix = "ydsz.domain")
public class DomainProperties {

    /**
     * 是否启用 domain 模块自动配置
     */
    private boolean enabled = true;

    /**
     * 事件配置
     */
    private Event event = new Event();

    /**
     * SpEL 评估器配置
     */
    private Spel spel = new Spel();

    /**
     * 事件发布配置
     */
    @Data
    @Validated
    public static class Event {

        /**
         * 是否启用异步事件发布（需容器中存在 TaskExecutor）
         */
        private boolean asyncEnabled = true;

        /**
         * 默认事务发布阶段
         *
         * <p>可选值：BEFORE_COMMIT, AFTER_COMMIT, AFTER_ROLLBACK, AFTER_COMPLETION
         */
        private String defaultPhase = "AFTER_COMMIT";
    }

    /**
     * SpEL 评估器配置
     */
    @Data
    @Validated
    public static class Spel {

        /**
         * 是否启用 SpEL 表达式解析缓存
         */
        private boolean cacheEnabled = true;

        /**
         * 缓存最大容量（0 表示无限制）
         */
        @Min(0)
        private int cacheMaxSize = 1024;
    }
}
