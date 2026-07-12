package com.njydsz.pmis.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * JDBC 层自动配置
 *
 * <p>聚合 jdbc 模块所有配置类，通过 Spring Boot 3 自动装配机制注册。
 *
 * <p>包含：
 * <ul>
 *   <li>{@link MybatisPlusAutoConfiguration} - MyBatis-Plus 拦截器 + 雪花 ID</li>
 *   <li>{@link DruidMonitorEnabler} - Druid 监控开关</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
@Configuration
@ConditionalOnClass(name = "com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor")
@Import({
    MybatisPlusAutoConfiguration.class,
    DruidMonitorEnabler.class
})
public class JdbcAutoConfiguration {
}
