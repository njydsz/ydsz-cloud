package com.njydsz.common.domain.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * Domain 模块统一配置属性
 *
 * <p>集中管理 domain 模块的所有配置项。
 *
 * <p><b>配置项（application.yml）：</b>
 * <pre>{@code
 * ydsz:
 *   domain:
 *     enabled: true                  # 启用 domain 模块自动配置（默认 true）
 * }</pre>
 *
 * <p><b>v1.4.0</b>：SpEL 评估器缓存配置（spel.cache-*）随 DAG 引擎迁移至 ydsz-cronjob 模块。
 *
 * @author ydsz-team
 * @since 1.3.0
 */
@Data
@ConfigurationProperties(prefix = "ydsz.domain")
public class DomainProperties {

    /**
     * 是否启用 domain 模块自动配置
     */
    private boolean enabled = true;
}
