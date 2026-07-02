/**
 * Sentry 错误监控后端集成 (批次 20 P2-1)
 *
 * 设计:
 *   1. 通过 AOP @SentryCapture 注解切面, 上报异常到 Sentry
 *   2. 异步上报, 不影响主链路性能
 *   3. 默认关闭, 通过 pmis.sentry.enabled=true 开启
 *   4. 自动附加: traceId / userId / module / businessType
 *
 * Maven 依赖 (在 ydsz-pmis-common 中):
 *   <dependency>
 *     <groupId>io.sentry</groupId>
 *     <artifactId>sentry-spring-boot-starter</artifactId>
 *     <version>7.13.0</version>
 *   </dependency>
 */
package com.njydsz.pmis.common.sentry;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

/**
 * Sentry 自动配置 (Spring Boot 3)
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Configuration
@ConditionalOnProperty(prefix = "pmis.sentry", name = "enabled", havingValue = "true")
@ConditionalOnClass(name = "io.sentry.Sentry")
public class SentryConfig {

    @Value("${spring.application.name:pmis-app}")
    private String applicationName;

    @Value("${pmis.sentry.dsn:}")
    private String dsn;

    @Value("${pmis.sentry.environment:dev}")
    private String environment;

    @Value("${pmis.sentry.release:1.0.0}")
    private String release;

    @Value("${pmis.sentry.traces-sample-rate:0.1}")
    private Double tracesSampleRate;

    @Value("${pmis.sentry.sample-rate:1.0}")
    private Double sampleRate;

    /**
     * 返回 Sentry 初始化属性 Map
     * 业务层在 Sentry.init() 之前调用, 把这些属性传入
     *
     * @param env Spring 环境上下文，用于读取激活的 profiles
     * @return Sentry 初始化配置对象
     */
    @Bean
    @Primary
    public SentryProperties sentryProperties(Environment env) {
        return SentryProperties.builder()
                .dsn(dsn)
                .environment(environment)
                .release(release)
                .serverName(applicationName)
                .tracesSampleRate(tracesSampleRate)
                .sampleRate(sampleRate)
                .activeProfiles(String.join(",", env.getActiveProfiles()))
                .build();
    }
}
