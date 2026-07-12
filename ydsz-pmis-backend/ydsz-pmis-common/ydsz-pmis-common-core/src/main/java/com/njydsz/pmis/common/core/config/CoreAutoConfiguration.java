package com.njydsz.pmis.common.core.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Core 模块自动配置类
 *
 * <p>激活 {@link CoreProperties} 配置属性绑定，
 * 使 {@code remi.core.*} 配置项在 IDE 中获得自动补全和类型校验支持。</p>
 *
 * <p><b>启用条件：</b>当 {@code remi.core.enabled=true} 时生效（默认启用）。</p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * # application.yml
 * remi:
 *   core:
 *     max-page-size: 500
 *     default-page-size: 20
 *     cache:
 *       default-expire-seconds: 3600
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "remi.core", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CoreProperties.class)
public class CoreAutoConfiguration {

    /**
     * CoreProperties Bean 由 {@code @EnableConfigurationProperties} 自动注册。
     *
     * <p>如需在运行时访问配置，注入 {@link CoreProperties} 即可：
     * <pre>{@code
     * @Autowired
     * private CoreProperties coreProperties;
     *
     * int maxPageSize = coreProperties.getMaxPageSize();
     * }</pre>
     */
}
