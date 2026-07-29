package com.njydsz.common.domain.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * Domain 模块统一配置属性
 *
 * <p>集中管理 domain 模块的所有配置项，包括模块开关、SpEL 评估器等。
 *
 * <p><b>配置项（application.yml）：</b>
 * <pre>{@code
 * ydsz:
 *   domain:
 *     enabled: true                  # 启用 domain 模块自动配置（默认 true）
 *     spel:
 *       cache-enabled: true          # 是否启用 SpEL 表达式缓存（默认 true）
 *       cache-max-size: 1024        # 缓存最大容量（0 表示无限制）
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.3.0
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
     * SpEL 评估器配置
     */
    private Spel spel = new Spel();

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