package com.njydsz.pmis.common.config;

import com.njydsz.pmis.common.chaos.ChaosAutoConfiguration;
import com.njydsz.pmis.common.featureflag.FeatureFlagAutoConfiguration;
import com.njydsz.pmis.common.filter.SameSiteCookieFilter;
import com.njydsz.pmis.common.filter.StrictContentTypeFilter;
import com.njydsz.pmis.common.interceptor.AuthInterceptor;
import com.njydsz.pmis.common.tx.TransactionPostProcessor;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.TaskDecorator;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.Map;

/**
 * 公共模块自动配置
 *
 * <p>供其他微服务通过 {@code @SpringBootApplication(scanBasePackages)} 引入。
 * Aspects/DataScopeAspect/IdempotentAspect/OperationLogAspect/RateLimiterAspect 等
 * 标注了 {@code @Aspect @Component} 的类，由 Spring 通过 {@link ComponentScan} 自动注入，
 * 不在此处再显式 @Bean 重复声明，以避免构造器签名冲突。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Configuration
@ComponentScan("com.njydsz.pmis.common")
@Import({FeatureFlagAutoConfiguration.class, ChaosAutoConfiguration.class, SentinelAutoConfiguration.class,
        JasyptAutoConfiguration.class})
@EnableAsync
@EnableCaching
public class CommonAutoConfiguration {

    /**
     * 注册 MyBatis-Plus 审计字段填充器
     *
     * @return AuditFieldFiller 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public AuditFieldFiller auditFieldFiller() {
        return new AuditFieldFiller();
    }

    // AuthInterceptor 已标注 @Component 且依赖 JwtTokenProvider（同为 @Component），
    // 由 @ComponentScan 自动注入，此处不再显式 @Bean 重复声明，以避免构造器签名冲突。

    /**
     * 注册 Web MVC 配置（注入鉴权拦截器）
     *
     * @param authInterceptor 鉴权拦截器
     * @return WebMvcConfig 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public WebMvcConfig webMvcConfig(AuthInterceptor authInterceptor) {
        return new WebMvcConfig(authInterceptor);
    }

    /**
     * 注册 OpenAPI 3.0 配置
     *
     * @return OpenApiConfig 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public OpenApiConfig openApiConfig() {
        return new OpenApiConfig();
    }

    /**
     * 注册 MDC 传递装饰器
     *
     * <p>Spring Boot 的 {@code TaskExecutionAutoConfiguration} 会自动将此 {@link TaskDecorator}
     * 应用到默认的 {@code applicationTaskExecutor}，从而让所有 {@code @Async} 方法（如
     * OperationLogAspect、事件监听器等）都能继承主线程的 MDC 上下文（traceId 等）。</p>
     *
     * <p>修复问题：异步线程丢失 traceId 导致审计日志无法与请求链路关联。</p>
     *
     * @return TaskDecorator 实例，负责将主线程 MDC 复制到异步线程
     */
    @Bean
    @ConditionalOnMissingBean
    public TaskDecorator mdcTaskDecorator() {
        return runnable -> {
            // 捕获主线程 MDC 上下文
            Map<String, String> context = MDC.getCopyOfContextMap();
            return () -> {
                if (context != null) {
                    MDC.setContextMap(context);
                }
                try {
                    runnable.run();
                } finally {
                    MDC.clear();
                }
            };
        };
    }

    /**
     * 注册事务后处理器（P1-10 分布式事务降级方案）
     *
     * <p>提供 {@code executeAfterCommit(Runnable)} 能力，让 Feign 远程写操作
     * 在本地事务提交后执行，避免悬挂事务。详见 {@link TransactionPostProcessor}。</p>
     *
     * @return TransactionPostProcessor 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public TransactionPostProcessor transactionPostProcessor() {
        return new TransactionPostProcessor();
    }

    /**
     * 注册 SameSite Cookie 过滤器(CSRF 防御纵深)
     *
     * <p>仅在生产 profile 启用: dev 环境通常使用 HTTP,Secure 属性会导致 Cookie
     * 不被浏览器保存。顺序在 {@code XssFilter}(HIGHEST_PRECEDENCE + 1)之后。
     *
     * @return SameSiteCookieFilter 实例
     */
    @Bean
    @Profile("prod")
    @ConditionalOnMissingBean
    public SameSiteCookieFilter sameSiteCookieFilter() {
        return new SameSiteCookieFilter();
    }

    /**
     * 注册 Content-Type 严格校验过滤器(CSRF 防御纵深)
     *
     * <p>对所有 profile 启用: 写操作强制 application/json / multipart/form-data,
     * 拒绝 text/plain / application/x-www-form-urlencoded 等简单请求类型。
     * 顺序在 {@code SameSiteCookieFilter}(HIGHEST_PRECEDENCE + 2)之后。
     *
     * @return StrictContentTypeFilter 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public StrictContentTypeFilter strictContentTypeFilter() {
        return new StrictContentTypeFilter();
    }
}
