package com.njydsz.pmis.common.config;

import com.njydsz.pmis.common.chaos.ChaosAutoConfiguration;
import com.njydsz.pmis.common.featureflag.FeatureFlagAutoConfiguration;
import com.njydsz.pmis.common.filter.ContentSecurityPolicyFilter;
import com.njydsz.pmis.common.filter.SameSiteCookieFilter;
import com.njydsz.pmis.common.filter.StrictContentTypeFilter;
import com.njydsz.pmis.common.interceptor.AuthInterceptor;
import com.njydsz.pmis.common.kms.EnvironmentSecretProvider;
import com.njydsz.pmis.common.kms.JasyptSecretProvider;
import com.njydsz.pmis.common.kms.KmsProperties;
import com.njydsz.pmis.common.kms.SecretManager;
import com.njydsz.pmis.common.kms.SecretProvider;
import com.njydsz.pmis.common.service.BloomFilterService;
import com.njydsz.pmis.common.tx.TransactionPostProcessor;
import org.jasypt.encryption.StringEncryptor;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.task.TaskDecorator;
import org.springframework.cache.annotation.EnableCaching;

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
@Import({FeatureFlagAutoConfiguration.class, ChaosAutoConfiguration.class, SentinelAutoConfiguration.class, AsyncAutoConfiguration.class, BloomFilterConfig.class})
@EnableConfigurationProperties(KmsProperties.class)
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

    /**
     * 注册 Content-Security-Policy 安全响应头过滤器(P2-11 安全闭环)
     *
     * <p>为所有 HTTP 响应注入 CSP、X-Content-Type-Options、X-Frame-Options 等安全头。
     * 顺序在 {@code StrictContentTypeFilter}(HIGHEST_PRECEDENCE + 3)之后。
     *
     * @return ContentSecurityPolicyFilter 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public ContentSecurityPolicyFilter contentSecurityPolicyFilter() {
        return new ContentSecurityPolicyFilter();
    }

    /**
     * 注册布隆过滤器服务(P1-10:防止缓存穿透)
     *
     * <p>仅当 classpath 存在 {@link RedissonClient} 时生效,与 {@link BloomFilterConfig}
     * 中定义的 {@code userBloomFilter} / {@code userIdBloomFilter} Bean 配合使用。
     * 业务层注入后,通过逻辑名称(如 {@code "user:username"})操作过滤器。
     *
     * @param userBloomFilter   用户名维度的布隆过滤器(由 BloomFilterConfig 注册)
     * @param userIdBloomFilter 用户 ID 维度的布隆过滤器(由 BloomFilterConfig 注册)
     * @return BloomFilterService 实例
     */
    @Bean
    @ConditionalOnClass(RedissonClient.class)
    @ConditionalOnMissingBean
    public BloomFilterService bloomFilterService(RBloomFilter<String> userBloomFilter,
                                                 RBloomFilter<String> userIdBloomFilter) {
        return new BloomFilterService(userBloomFilter, userIdBloomFilter);
    }

    // ==================== KMS 密钥管理（P2-9） ====================

    /**
     * 注册默认密钥提供者：基于环境变量/Nacos 配置（P2-9）
     *
     * <p>当 {@code pmis.kms.provider=environment}（默认值或缺省）时生效。
     * 优先从环境变量读取（{@code PMIS_SECRETS_DB_PASSWORD} 等），
     * 其次从 Nacos 配置 {@code pmis.kms.secrets.*} 读取。
     *
     * <p>使用 {@code @ConditionalOnMissingBean(SecretProvider.class)} 允许业务模块覆盖。
     *
     * @param kmsProperties KMS 配置属性
     * @param environment   Spring Environment
     * @return EnvironmentSecretProvider 实例
     */
    @Bean
    @ConditionalOnMissingBean(SecretProvider.class)
    @ConditionalOnProperty(prefix = "pmis.kms", name = "provider", havingValue = "environment", matchIfMissing = true)
    public EnvironmentSecretProvider environmentSecretProvider(KmsProperties kmsProperties,
                                                              Environment environment) {
        return new EnvironmentSecretProvider(kmsProperties, environment);
    }

    /**
     * 注册 Jasypt 增强密钥提供者（P2-9）
     *
     * <p>当 {@code pmis.kms.provider=jasypt} 时生效。在环境变量/Nacos 配置基础上，
     * 对 {@code ENC()} 密文自动解密，复用项目现有 Jasypt 配置。
     *
     * <p>使用 {@code @ConditionalOnMissingBean(SecretProvider.class)} 允许业务模块覆盖。
     *
     * @param kmsProperties   KMS 配置属性
     * @param environment     Spring Environment
     * @param stringEncryptor Jasypt 字符串加密器（由 jasypt-spring-boot-starter 自动装配）
     * @return JasyptSecretProvider 实例
     */
    @Bean
    @ConditionalOnMissingBean(SecretProvider.class)
    @ConditionalOnProperty(prefix = "pmis.kms", name = "provider", havingValue = "jasypt")
    public JasyptSecretProvider jasyptSecretProvider(KmsProperties kmsProperties,
                                                     Environment environment,
                                                     StringEncryptor stringEncryptor) {
        return new JasyptSecretProvider(kmsProperties, environment, stringEncryptor);
    }

    /**
     * 注册密钥管理器（P2-9 业务统一入口）
     *
     * <p>业务代码注入 {@link SecretManager} 获取数据库密码、Redis 密码、JWT 密钥等，
     * 内部委托给当前生效的 {@link SecretProvider} 实现。
     *
     * <p>使用 {@code @ConditionalOnMissingBean} 允许业务模块覆盖。
     *
     * @param secretProvider 当前生效的密钥提供者
     * @return SecretManager 实例
     */
    @Bean
    @ConditionalOnMissingBean(SecretManager.class)
    public SecretManager secretManager(SecretProvider secretProvider) {
        return new SecretManager(secretProvider);
    }
}
