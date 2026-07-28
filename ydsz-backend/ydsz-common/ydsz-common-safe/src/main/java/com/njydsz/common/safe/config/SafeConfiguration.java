package com.njydsz.common.safe.config;

import java.util.List;

import com.njydsz.common.safe.sensitive.SensitiveDataProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.scheduling.annotation.EnableScheduling;

import org.springframework.data.redis.core.StringRedisTemplate;

import com.njydsz.common.redis.service.RedisService;
import com.njydsz.common.safe.advice.XssRequestBodyAdvice;
import com.njydsz.common.safe.alert.SafeAlertProperties;
import com.njydsz.common.safe.alert.SecurityEventAggregator;
import com.njydsz.common.safe.alert.SecurityEventListener;
import com.njydsz.common.safe.alert.SecurityEventPublisher;
import com.njydsz.common.safe.audit.SecurityAuditLogger;
import com.njydsz.common.safe.metrics.SafeMetrics;
import com.njydsz.common.safe.config.condition.XssConverterModeCondition;

import io.micrometer.core.instrument.MeterRegistry;
import com.njydsz.common.safe.config.condition.XssFilterModeCondition;
import com.njydsz.common.safe.converter.XssJsonMessageConverter;
import com.njydsz.common.safe.core.JsonBodyXssCleaner;
import com.njydsz.common.safe.csrf.CsrfTokenGenerator;
import com.njydsz.common.safe.csrf.CsrfTokenRepository;
import com.njydsz.common.safe.csrf.impl.DefaultCsrfTokenGenerator;
import com.njydsz.common.safe.csrf.impl.InMemoryCsrfTokenRepository;
import com.njydsz.common.safe.csrf.impl.RedisCsrfTokenRepository;
import com.njydsz.common.safe.crypto.NonceCache;
import com.njydsz.common.safe.filter.ApiSignatureFilter;
import com.njydsz.common.safe.filter.CsrfFilter;
import com.njydsz.common.safe.filter.IpAccessFilter;
import com.njydsz.common.safe.filter.SecurityHeaderFilter;
import com.njydsz.common.safe.filter.SqlInjectionFilter;
import com.njydsz.common.safe.filter.XssFilter;
import com.njydsz.common.safe.ip.IpAccessService;
import com.njydsz.common.safe.sensitive.SensitiveDataAdvice;

/**
 * 安全模块自动配置
 * <p>
 * 集中注册以下安全防护能力：
 * <ul>
 *   <li>XSS 过滤器：基于 OWASP 库的全局 HTTP 请求参数与 JSON 请求体清洗</li>
 *   <li>安全响应头：防止 XSS、点击劫持、MIME 嗅探等 Web 安全威胁</li>
 *   <li>CSRF 防护：基于 Token 机制，Redis 存储支持分布式</li>
 *   <li>SQL 注入防护：基于正则的请求参数拦截</li>
 *   <li>限流防护：基于 Redis 令牌桶的全局限流</li>
 *   <li>敏感数据脱敏：基于 Jackson 序列化器的字段级脱敏</li>
 *   <li>验证码：图形/算术验证码生成与验证</li>
 * </ul>
 *
 * <p><b>过滤器执行顺序：</b>SecurityHeaderFilter → XssFilter → SqlInjectionFilter
 * → CsrfFilter → RateLimitFilter。其中 RateLimitFilter 优先级最高，限流失败直接
 * 返回 429 而不再进入后续过滤器。</p>
 *
 * <p><b>注意：</b>防重复提交/幂等性功能由本模块的 Redis 限流能力提供。</p>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass(FilterRegistrationBean.class)
@EnableScheduling
@EnableConfigurationProperties({
        SafeXssProperties.class,
        SecurityHeaderProperties.class,
        CsrfProperties.class,
        SensitiveDataProperties.class,
        SafeAlertProperties.class,
        ApiSignatureProperties.class,
        IpAccessProperties.class,
        AutoBlockProperties.class,
        SqlInjectionProperties.class

/**
 * SafeConfiguration 自动配置类，注册模块 Bean 并管理装配条件。
 *
 * <p>所属包：{@code com.njydsz.common.safe.config}
 *
 * @author ydsz-team
 * @since 1.0.0
 */
})
public class SafeConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SafeConfiguration.class);

    /**
     * 安全模块启动摘要日志
     *
     * <p>启动时输出安全能力清单。各能力是否实际注册由对应的
     * {@code @ConditionalOnProperty} 决定，此处仅列出模块支持的全部能力。
     */
    @PostConstruct
    public void logStartupSummary() {
        log.info("==================== [Safe Module] Security Capabilities Summary ====================");
        log.info("  XSS Filter:         OWASP Sanitizer + configurable policies (STRICT/STANDARD/RELAXED)");
        log.info("  SQL Injection:      Regex-based detection + runtime hot-reload");
        log.info("  CSRF:               Synchronizer Token / Double Submit Cookie (dual mode)");
        log.info("  Security Headers:   CSP / HSTS / X-Frame-Options / X-Content-Type-Options");
        log.info("  Rate Limit:         Redis sliding window + @RateLimit AOP + local fallback");
        log.info("  IP Access Control:  CIDR blacklist/whitelist + auto-block");
        log.info("  API Signature:       timestamp + nonce + HMAC-SHA256");
        log.info("  Auto Block:         Sliding window event aggregation + auto IP ban");
        log.info("  Sensitive Data:     18 types + Record support + role-based control");
        log.info("  Captcha:            Image + Arithmetic + Slider");
        log.info("  Crypto:             AES-256-GCM + Nonce cache");
        log.info("  Metrics:            Micrometer Counter/Timer (optional)");
        log.info("  Audit Logger:       Structured JSON + traceId");
        log.info("====================================================================================");
    }

    /**
     * 注册安全响应头过滤器
     *
     * <p>为 HTTP 响应添加安全相关的头部，包括：
     * <ul>
     *   <li>X-Frame-Options：防止点击劫持</li>
     *   <li>X-Content-Type-Options：防止 MIME 嗅探</li>
     *   <li>X-XSS-Protection：XSS 过滤器</li>
     *   <li>Strict-Transport-Security：强制 HTTPS</li>
     *   <li>Content-Security-Policy：内容安全策略</li>
     * </ul>
     *
     * @param properties 安全响应头配置属性
     * @return 安全响应头过滤器注册 bean
     */
    @Bean
    @ConditionalOnMissingBean(name = "securityHeaderFilter")
    @ConditionalOnProperty(prefix = "ydsz.safe.security-headers", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<SecurityHeaderFilter> securityHeaderFilterRegistration(SecurityHeaderProperties properties) {
        FilterRegistrationBean<SecurityHeaderFilter> registrationBean = new FilterRegistrationBean<>(new SecurityHeaderFilter(properties));
        registrationBean.setName("securityHeaderFilter");
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(properties.getOrder());
        return registrationBean;
    }

    /**
     * 注册安全事件发布器
     *
     * @return 安全事件发布器实例
     */
    @Bean
    @ConditionalOnMissingBean(SecurityEventPublisher.class)
    public SecurityEventPublisher securityEventPublisher() {
        return new SecurityEventPublisher();
    }

    /**
     * 注册安全指标采集器
     *
     * <p>采集安全相关 Micrometer 指标（XSS/SQL注入/CSRF/限流/IP封禁 Counter + Filter Timer）。
     * Micrometer 为可选依赖，不可用时降级为内存计数。
     *
     * @param meterRegistry Micrometer MeterRegistry（可选）
     * @return 安全指标采集器实例
     */
    @Bean
    @ConditionalOnMissingBean(SafeMetrics.class)
    public SafeMetrics safeMetrics(ObjectProvider<MeterRegistry> meterRegistry) {
        log.info("注册安全指标采集器");
        return new SafeMetrics(meterRegistry.getIfAvailable());
    }

    /**
     * 注册安全审计日志记录器
     *
     * <p>将安全事件以结构化 JSON 格式输出到独立的审计日志，
     * 支持 traceId 关联，可与 Loki/Sentry 集成。
     *
     * @return 安全审计日志记录器实例
     */
    @Bean
    @ConditionalOnMissingBean(SecurityAuditLogger.class)
    public SecurityAuditLogger securityAuditLogger() {
        log.info("注册安全审计日志记录器");
        return new SecurityAuditLogger();
    }

    /**
     * 注册安全事件监听器
     *
     * <p>串联安全事件处理链：监听 {@link SecurityEvent} 事件，
     * 分发给 {@link SafeMetrics}（指标采集）和 {@link SecurityAuditLogger}（审计日志）。
     *
     * @param safeMetrics   安全指标采集器（可选）
     * @param auditLogger   安全审计日志记录器（可选）
     * @return 安全事件监听器实例
     */
    @Bean
    @ConditionalOnMissingBean(SecurityEventListener.class)
    public SecurityEventListener securityEventListener(
            ObjectProvider<SafeMetrics> safeMetrics,
            ObjectProvider<SecurityAuditLogger> auditLogger) {
        log.info("注册安全事件监听器（串联指标采集 + 审计日志）");
        return new SecurityEventListener(safeMetrics.getIfAvailable(), auditLogger.getIfAvailable());
    }

    /**
     * 注册安全事件自动响应聚合器
     *
     * <p>监听安全事件，基于滑动窗口统计同一 IP 的安全事件频率。
     * 当同一 IP 在窗口内触发超过阈值数量的安全事件时，自动触发 IP 封禁。
     * IpAccessService 为可选依赖，未启用 IP 访问控制时仅记录日志。
     *
     * @param properties       自动封禁配置属性
     * @param ipAccessService IP 访问控制服务（可选）
     * @return 安全事件聚合器实例
     */
    @Bean
    @ConditionalOnMissingBean(SecurityEventAggregator.class)
    @ConditionalOnProperty(prefix = "ydsz.safe.auto-block", name = "enabled", havingValue = "true", matchIfMissing = true)
    public SecurityEventAggregator securityEventAggregator(
            AutoBlockProperties properties,
            ObjectProvider<IpAccessService> ipAccessService) {
        log.info("注册安全事件自动响应聚合器: threshold={}, window={}s",
                properties.getThreshold(), properties.getWindowSeconds());
        return new SecurityEventAggregator(
                ipAccessService.getIfAvailable(),
                properties.isEnabled(),
                properties.getThreshold(),
                properties.getWindowSeconds()
        );
    }

    /**
     * 注册 XSS 过滤器
     *
     * <p>默认排除路径：/error、/favicon.ico、/actuator/**
     * 仅在 mode=filter 时注册（与 RequestBodyAdvice 和 Converter 互斥）。
     *
     * @param xssProperties   XSS 配置属性
     * @param eventPublisher  安全事件发布器
     * @param alertProperties 安全告警配置属性
     * @return XSS 过滤器注册 bean
     */
    @Bean
    @ConditionalOnMissingBean(name = "xssFilterRegistration")
    @Conditional(XssFilterModeCondition.class)
    public FilterRegistrationBean<XssFilter> xssFilterRegistration(SafeXssProperties xssProperties,
                                                                    SecurityEventPublisher eventPublisher,
                                                                    SafeAlertProperties alertProperties) {
        FilterRegistrationBean<XssFilter> registrationBean = new FilterRegistrationBean<>(
                new XssFilter(xssProperties.getExcludes(), eventPublisher, alertProperties));
        registrationBean.setName("xssFilter");
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(xssProperties.getOrder());
        return registrationBean;
    }

    /**
     * 注册 JSON Body XSS 清理器
     *
     * <p>用于递归清理 JSON 对象中所有字符串值的 XSS 内容。
     *
     * @return JSON Body XSS 清理器实例
     */
    @Bean
    @ConditionalOnMissingBean(JsonBodyXssCleaner.class)
    public JsonBodyXssCleaner jsonBodyXssCleaner() {
        return new JsonBodyXssCleaner();
    }

    /**
     * 注册 XSS 请求体拦截器
     *
     * <p>在 JSON 反序列化前，对请求体中的字符串值进行 XSS 清理。
     * 仅在 Filter 模式下生效，避免与 Converter 模式双重清洗。
     *
     * @param xssCleaner    JSON Body XSS 清理器
     * @param xssProperties XSS 配置属性
     * @return XSS 请求体拦截器实例
     */
    @Bean
    @ConditionalOnMissingBean(XssRequestBodyAdvice.class)
    public XssRequestBodyAdvice xssRequestBodyAdvice(JsonBodyXssCleaner xssCleaner,
            SafeXssProperties xssProperties) {
        return new XssRequestBodyAdvice(xssCleaner, xssProperties);
    }

    /**
     * 注册 XSS JSON 消息转换器
     *
     * <p>在 JSON 反序列化阶段对字符串值进行 XSS 过滤。
     * 仅在 mode=converter 时注册（与 Filter 和 Advice 模式互斥）。
     *
     * <p>过滤器通过 {@link HttpMessageConverters} 注册到 Spring MVC 的消息转换器链中，
     * 替换默认的 JsonHttpMessageConverter，在反序列化前完成 XSS 清洗。
     *
     * @param properties XSS 配置属性
     * @return XSS JSON 消息转换器 Bean
     */
    @Bean
    @ConditionalOnMissingBean(XssJsonMessageConverter.class)
    @Conditional(XssConverterModeCondition.class)
    public XssJsonMessageConverter xssJsonMessageConverter(SafeXssProperties properties) {
        log.info("注册 XSS JSON 消息转换器，模式: {}", properties.getMode());
        return new XssJsonMessageConverter();
    }

    // XssJsonMessageConverter 已注册为 Bean，Spring Boot 4.1 自动检测 HttpMessageConverter Bean 并注册到转换器链，
    // 无需再通过 HttpMessageConverters（已弃用）包装注册。

    /**
     * 注册 CSRF 令牌生成器
     */
    @Bean
    @ConditionalOnMissingBean(CsrfTokenGenerator.class)
    public CsrfTokenGenerator csrfTokenGenerator(CsrfTokenRepository tokenRepository) {
        return new DefaultCsrfTokenGenerator(tokenRepository);
    }

    /**
     * 注册 CSRF 令牌存储库（Redis 分布式环境）
     *
     * <p>当 RedisService 可用时，自动使用 Redis 存储以支持分布式部署。
     */
    @Bean
    @Primary
    @ConditionalOnBean(RedisService.class)
    public CsrfTokenRepository redisCsrfTokenRepository(CsrfProperties properties, RedisService redisService) {
        return new RedisCsrfTokenRepository(properties.getExpirationSeconds(), redisService);
    }

    /**
     * 注册 CSRF 令牌存储库（单机内存环境）
     *
     * <p>仅当 RedisService 不可用时使用内存存储。适用于单机部署场景。
     *
     * @param properties CSRF 配置属性
     * @return CSRF 令牌存储库实例
     */
    @Bean
    @ConditionalOnMissingBean({RedisService.class, CsrfTokenRepository.class})
    public CsrfTokenRepository inMemoryCsrfTokenRepository(CsrfProperties properties) {
        return new InMemoryCsrfTokenRepository(properties.getExpirationSeconds());
    }

    /**
     * 注册 CSRF 防护过滤器
     *
     * <p>防止跨站请求伪造（CSRF）攻击：
     * <ul>
     *   <li>GET 请求：自动生成并返回 CSRF 令牌</li>
     *   <li>其他请求：验证 CSRF 令牌有效性</li>
     * </ul>
     *
     * @param properties      CSRF 配置属性
     * @param tokenRepository CSRF 令牌存储库
     * @param tokenGenerator  CSRF 令牌生成器
     * @return CSRF 防护过滤器注册 bean
     */
    @Bean
    @ConditionalOnMissingBean(name = "csrfFilterRegistration")
    @ConditionalOnProperty(prefix = "ydsz.safe.csrf", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<CsrfFilter> csrfFilterRegistration(CsrfProperties properties, CsrfTokenRepository tokenRepository, CsrfTokenGenerator tokenGenerator) {
        FilterRegistrationBean<CsrfFilter> registrationBean = new FilterRegistrationBean<>(new CsrfFilter(properties, tokenRepository));
        registrationBean.setName("csrfFilter");
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(properties.getOrder());
        return registrationBean;
    }

    /**
     * 注册敏感数据脱敏 AOP 拦截器
     *
     * <p>对 Controller 返回值进行敏感数据脱敏处理。
     * 仅在 {@code ydsz.safe.sensitive.enabled=true} 时生效。
     *
     * @param configuration 敏感数据脱敏配置
     * @return 敏感数据脱敏 AOP 拦截器实例
     */
    @Bean
    @ConditionalOnMissingBean(SensitiveDataAdvice.class)
    public SensitiveDataAdvice sensitiveDataAdvice(SensitiveDataProperties configuration) {
        log.info("注册敏感数据脱敏 AOP 拦截器，启用状态: {}", configuration.isEnabled());
        return new SensitiveDataAdvice(configuration);
    }

    /**
     * P2-3: 限流相关 Bean（限流过滤器、方法级限流 AOP、多维度限流器）已迁移至
     * {@code com.njydsz.common.safe.ratelimit.config.RateLimitAutoConfiguration}，
     * 由其统一管理。本配置类不再持有旧版限流 Bean，避免与新版自动配置产生 Bean 冲突。
     *
     * <p>启用方式：通过 {@code ydsz.ratelimit.enabled=true}（默认 true）开启
     * 新版限流自动配置；旧版配置 {@code ydsz.safe.ratelimit.enabled} 已被废弃。
     */

    /**
     * 注册 IP 访问控制服务
     *
     * <p>提供 IP 黑白名单管理能力，支持 CIDR 网段匹配、Redis 持久化存储和本地缓存。
     * 仅在 {@code ydsz.safe.ip-access.enabled=true} 且 Redis 可用时注册。
     *
     * @param properties   IP 访问控制配置
     * @param redisService Redis 服务
     * @return IP 访问控制服务实例
     */
    @Bean
    @ConditionalOnMissingBean(IpAccessService.class)
    @ConditionalOnBean(RedisService.class)
    @ConditionalOnProperty(prefix = "ydsz.safe.ip-access", name = "enabled", havingValue = "true")
    public IpAccessService ipAccessService(IpAccessProperties properties, RedisService redisService) {
        log.info("注册 IP 访问控制服务: mode={}", properties.getMode());
        return new IpAccessService(properties, redisService);
    }

    /**
     * 注册 IP 访问控制过滤器
     *
     * <p>在请求进入安全过滤器链之前执行 IP 黑白名单检查，命中黑名单的 IP 返回 403。
     * 过滤器优先级最高（HIGHEST_PRECEDENCE），确保恶意 IP 在进入其他过滤器之前被拦截。
     *
     * @param ipAccessService IP 访问控制服务
     * @param eventPublisher   安全事件发布器
     * @param properties       IP 访问控制配置
     * @return IP 访问控制过滤器注册 bean
     */
    @Bean
    @ConditionalOnMissingBean(name = "ipAccessFilterRegistration")
    @ConditionalOnBean(IpAccessService.class)
    public FilterRegistrationBean<IpAccessFilter> ipAccessFilterRegistration(
            IpAccessService ipAccessService,
            SecurityEventPublisher eventPublisher,
            IpAccessProperties properties) {
        FilterRegistrationBean<IpAccessFilter> registrationBean = new FilterRegistrationBean<>(
                new IpAccessFilter(ipAccessService, eventPublisher, properties.getExcludes()));
        registrationBean.setName("ipAccessFilter");
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registrationBean;
    }

    /**
     * 注册防重放 Nonce 缓存
     *
     * <p>用于 API 签名验证的 nonce 防重放存储，基于 ydsz-common-cache 实现 TTL 自动过期。
     * 定时清理任务每 60 秒执行一次（需宿主应用开启 {@code @EnableScheduling}）。
     *
     * @return Nonce 缓存实例
     */
    @Bean
    @ConditionalOnMissingBean(NonceCache.class)
    public NonceCache nonceCache() {
        log.info("注册防重放 Nonce 缓存");
        return new NonceCache();
    }

    /**
     * 注册 API 签名验证过滤器
     *
     * <p>基于 {@code timestamp + nonce + signature} 三要素实现 API 请求防篡改和防重放。
     * 使用 HMAC-SHA256 算法计算签名，确保请求在传输过程中未被篡改。
     * 仅在 {@code ydsz.safe.api-signature.enabled=true} 时注册。
     *
     * @param properties     签名配置属性
     * @param nonceCache     防重放 Nonce 缓存
     * @param eventPublisher 安全事件发布器
     * @return API 签名验证过滤器注册 bean
     */
    @Bean
    @ConditionalOnMissingBean(name = "apiSignatureFilterRegistration")
    @ConditionalOnProperty(prefix = "ydsz.safe.api-signature", name = "enabled", havingValue = "true")
    public FilterRegistrationBean<ApiSignatureFilter> apiSignatureFilterRegistration(
            ApiSignatureProperties properties,
            NonceCache nonceCache,
            SecurityEventPublisher eventPublisher) {
        FilterRegistrationBean<ApiSignatureFilter> registrationBean = new FilterRegistrationBean<>(
                new ApiSignatureFilter(properties, nonceCache, eventPublisher));
        registrationBean.setName("apiSignatureFilter");
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE + 4);
        return registrationBean;
    }

    /**
     * 注册 SQL 注入防护过滤器
     *
     * <p>检测并拦截 HTTP 请求中的 SQL 注入攻击，保护应用安全。
     * 仅在 {@code ydsz.safe.sql-injection.enabled=true} 时注册。
     *
     * <p>支持白名单配置：
     * <ul>
     *   <li>{@code ydsz.safe.sql-injection.whitelist-paths} - 白名单路径（Ant 风格，逗号分隔）</li>
     *   <li>{@code ydsz.safe.sql-injection.whitelist-params} - 白名单参数名（逗号分隔）</li>
     * </ul>
     *
     * @param eventPublisher  安全事件发布器
     * @param whitelistPaths  白名单路径
     * @param whitelistParams 白名单参数名
     * @return SQL 注入过滤器注册 bean
     */
    @Bean
    @ConditionalOnMissingBean(name = "sqlInjectionFilterRegistration")
    @ConditionalOnProperty(prefix = "ydsz.safe.sql-injection", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<SqlInjectionFilter> sqlInjectionFilterRegistration(
            SecurityEventPublisher eventPublisher,
            @Value("${ydsz.safe.sql-injection.whitelist-paths:}") List<String> whitelistPaths,
            @Value("${ydsz.safe.sql-injection.whitelist-params:}") List<String> whitelistParams) {
        FilterRegistrationBean<SqlInjectionFilter> registrationBean = new FilterRegistrationBean<>(
                new SqlInjectionFilter(true, eventPublisher, whitelistPaths, whitelistParams));
        registrationBean.setName("sqlInjectionFilter");
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE + 3);
        return registrationBean;
    }
}
