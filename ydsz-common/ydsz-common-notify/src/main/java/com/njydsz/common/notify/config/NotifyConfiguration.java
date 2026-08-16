package com.njydsz.common.notify.config;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import com.njydsz.common.notify.aggregate.NotificationAggregator;
import com.njydsz.common.notify.aggregate.TimeWindowAggregator;
import com.njydsz.common.notify.audit.NotifyAuditService;
import com.njydsz.common.notify.channel.NotifyChannelStrategy;
import com.njydsz.common.notify.core.AsyncNotifyService;
import com.njydsz.common.notify.core.DeadLetterHandler;
import com.njydsz.common.notify.core.InMemoryDeadLetterHandler;
import com.njydsz.common.notify.core.NotifyCircuitBreakerRegistry;
import com.njydsz.common.notify.core.NotifyRetryQueue;
import com.njydsz.common.notify.core.NotifyService;
import com.njydsz.common.notify.core.NotifyServiceImpl;
import com.njydsz.common.notify.core.PersistentNotifyRetryQueue;
import com.njydsz.common.notify.core.TransactionalNotifyPublisher;
import com.njydsz.common.notify.dedup.NotifyDedupService;
import com.njydsz.common.notify.fallback.NotifyFallbackManager;
import com.njydsz.common.notify.health.NotifyHealthIndicator;
import com.njydsz.common.notify.helper.NotifyHelper;
import com.njydsz.common.notify.i18n.NotifyI18nResolver;
import com.njydsz.common.notify.i18n.NotifyI18nService;
import com.njydsz.common.notify.metrics.NotifyMetrics;
import com.njydsz.common.notify.preference.NotifyPreferenceManager;
import com.njydsz.common.notify.provider.AliyunSmsProvider;
import com.njydsz.common.notify.provider.SmsProvider;
import com.njydsz.common.notify.ratelimit.NotifyRateLimiterManager;
import com.njydsz.common.notify.security.DkimSigner;
import com.njydsz.common.notify.security.EmailSmtpHealthChecker;
import com.njydsz.common.notify.security.NotifyPasswordResolver;
import com.njydsz.common.notify.template.HtmlTemplateRegistry;
import com.njydsz.common.notify.template.TemplateEngine;
import com.njydsz.common.notify.template.TemplateVariableValidator;
import com.njydsz.common.notify.tracking.EmailTrackingService;
import com.njydsz.common.redis.service.RedisRateLimiter;
import com.njydsz.common.redis.service.ops.RedisCollectionOps;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.common.util.concurrent.ExecutorUtils;

/**
 * 统一消息通知自动配置类
 *
 * <p>各渠道 Sender（Email/SMS/WeCom/DingTalk/Feishu）通过实现 {@link NotifyChannelStrategy} 接口自动注册。
 * 邮件渠道的 {@link JavaMailSender} Bean 由本配置类根据 {@code ydsz.notify.email} 配置自动创建。
 *
 * <p>核心能力：密码加密、SMTP 健康探活、XSS 防护、指标埋点、邮件追踪、渠道降级、
 * HTML 模板注册、DKIM 签名、多提供商抽象、通知偏好、去重、国际化、消息聚合、审计日志、
 * 熔断器、死信队列、事务安全发布。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(NotifyProperties.class)
@EnableScheduling
@ConditionalOnClass(name = "org.apache.hc.client5.http.classic.HttpClient")
@ConditionalOnProperty(prefix = "ydsz.notify", name = "enabled", havingValue = "true", matchIfMissing = true)
public class NotifyConfiguration {

    private final Logger log = LoggerFactory.getLogger(NotifyConfiguration.class);

    /** 通知服务实例，供定时任务消费重试队列和聚合消息时使用 */
    private NotifyServiceImpl notifyServiceInstance;
    /** 通知重试队列实例，供定时任务消费重试队列时使用 */
    private NotifyRetryQueue retryQueueInstance;

    // ==================== 密码加密 ====================

    /**
     * 注册 SMTP 密码解析器 Bean。
     *
     * <p>负责 Jasypt 加密 SMTP 密码的运行时解密，避免明文配置泄露。
     * 仅在邮件渠道启用时创建；若容器中已有 {@link NotifyPasswordResolver} 则不再注册，便于外部自定义实现覆盖默认行为。
     */
    @Bean
    @ConditionalOnMissingBean(NotifyPasswordResolver.class)
    @ConditionalOnProperty(prefix = "ydsz.notify.email", name = "enabled", havingValue = "true")
    public NotifyPasswordResolver notifyPasswordResolver(NotifyProperties properties) {
        log.info("[NotifyConfiguration] NotifyPasswordResolver bean registered");
        return new NotifyPasswordResolver(properties);
    }

    // ==================== JavaMailSender ====================

    /**
     * 构建 Spring {@link JavaMailSender} Bean（邮件发送客户端）。
     *
     * <p>依据 {@code ydsz.notify.email} 配置装配 SMTP 主机/端口/协议（SSL/STARTTLS）与超时参数；
     * 若密码经 Jasypt 加密则通过 {@link NotifyPasswordResolver} 解密后再注入。
     * 仅在邮件渠道启用且无自定义 {@link JavaMailSender} 时创建，避免与业务侧已有邮件客户端冲突。
     */
    @Bean
    @ConditionalOnMissingBean(JavaMailSender.class)
    @ConditionalOnClass(JavaMailSender.class)
    @ConditionalOnProperty(prefix = "ydsz.notify.email", name = "enabled", havingValue = "true")
    public JavaMailSender notifyJavaMailSender(NotifyProperties properties,
                                               ObjectProvider<NotifyPasswordResolver> passwordResolver) {
        NotifyProperties.EmailConfig email = properties.getEmail();
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(email.getSmtpHost());
        sender.setPort(email.getSmtpPort());
        sender.setUsername(email.getFromMail());

        String password = email.getPassword();
        NotifyPasswordResolver resolver = passwordResolver.getIfAvailable();
        if (resolver != null && NotifyPasswordResolver.isEncrypted(password)) {
            password = resolver.resolvePassword(password);
            log.info("[NotifyConfiguration] SMTP 密码已通过 Jasypt 解密");
        }
        sender.setPassword(password);
        sender.setDefaultEncoding(email.getEncoding());
        sender.setProtocol(email.getSsl().isEnabled() ? "smtps" : "smtp");

        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", String.valueOf(email.isAuth()));
        props.put("mail.smtp.connectiontimeout", String.valueOf(email.getConnectionTimeout()));
        props.put("mail.smtp.timeout", String.valueOf(email.getTimeout()));
        props.put("mail.smtp.writetimeout", String.valueOf(email.getWriteTimeout()));
        props.put("mail.smtp.debug", String.valueOf(email.isDebug()));

        if (email.getSsl().isEnabled()) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.ssl.protocols", email.getSsl().getProtocols());
            props.put("mail.smtp.ssl.checkserveridentity",
                    String.valueOf(email.getSsl().isCheckServerIdentity()));
            if (StringUtils.hasText(email.getSsl().getTrustStorePath())) {
                props.put("mail.smtp.ssl.trust", email.getSsl().getTrustStorePath());
            }
        }

        if (email.isStarttls()) {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        }

        if (email.getProperties() != null) {
            for (Map.Entry<String, String> entry : email.getProperties().entrySet()) {
                props.put(entry.getKey(), entry.getValue());
            }
        }

        log.info("[NotifyConfiguration] JavaMailSender bean registered, host={}, port={}, ssl={}, starttls={}",
                email.getSmtpHost(), email.getSmtpPort(), email.getSsl().isEnabled(), email.isStarttls());
        return sender;
    }

    // ==================== SMTP 健康探活 ====================

    /**
     * 注册 SMTP 健康探活 Bean。
     *
     * <p>定期连通 SMTP 服务器以判定邮件渠道可用性，为 {@link NotifyHealthIndicator} 提供底层探针。
     * 仅邮件渠道启用时创建，且无自定义实现时注册默认实现。
     */
    @Bean
    @ConditionalOnMissingBean(EmailSmtpHealthChecker.class)
    @ConditionalOnClass(JavaMailSender.class)
    @ConditionalOnProperty(prefix = "ydsz.notify.email", name = "enabled", havingValue = "true")
    public EmailSmtpHealthChecker emailSmtpHealthChecker(NotifyProperties properties) {
        log.info("[NotifyConfiguration] EmailSmtpHealthChecker bean registered");
        return new EmailSmtpHealthChecker(properties);
    }

    // ==================== 指标埋点 ====================

    /**
     * 注册通知指标采集 Bean。
     *
     * <p>聚合发送量、成功率、延迟、渠道分布等 Metrics，接入 Micrometer {@link MeterRegistry}（可选，缺失时为无操作实现）。
     * 为容量评估与告警提供数据底座；无自定义 Bean 时注册，避免重复埋点。
     */
    @Bean
    @ConditionalOnMissingBean(NotifyMetrics.class)
    public NotifyMetrics notifyMetrics(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        log.info("[NotifyConfiguration] NotifyMetrics bean registered, meterRegistry={}",
                registry != null ? registry.getClass().getSimpleName() : "null");
        return new NotifyMetrics(registry);
    }

    // ==================== 邮件追踪 ====================

    /**
     * 注册邮件打开/点击追踪 Bean。
     *
     * <p>通过埋点像素与 Redis 记录邮件送达后的打开与点击行为，用于效果分析。
     * Redis 为可选依赖（缺失时降级为不追踪）；仅邮件渠道启用时创建。
     */
    @Bean
    @ConditionalOnMissingBean(EmailTrackingService.class)
    @ConditionalOnProperty(prefix = "ydsz.notify.email", name = "enabled", havingValue = "true")
    public EmailTrackingService emailTrackingService(NotifyProperties properties,
                                                     ObjectProvider<RedisStringOps> redisStringOpsProvider,
                                                     ObjectProvider<RedisCollectionOps> redisCollectionOpsProvider) {
        RedisStringOps redisStringOps = redisStringOpsProvider.getIfAvailable();
        RedisCollectionOps redisCollectionOps = redisCollectionOpsProvider.getIfAvailable();
        log.info("[NotifyConfiguration] EmailTrackingService bean registered, redisStringOps={}, redisCollectionOps={}",
                redisStringOps != null, redisCollectionOps != null);
        return new EmailTrackingService(properties, redisStringOps, redisCollectionOps);
    }

    // ==================== 渠道降级 ====================

    /**
     * 注册渠道降级管理器 Bean。
     *
     * <p>在首选渠道（如短信）不可用或触发熔断时，按策略将通知降级到备用渠道（如邮件/站内信）。
     * 收集全部 {@link NotifyChannelStrategy} 作为降级目标；无自定义 Bean 时注册默认实现。
     */
    @Bean
    @ConditionalOnMissingBean(NotifyFallbackManager.class)
    public NotifyFallbackManager notifyFallbackManager(NotifyProperties properties,
                                                       List<NotifyChannelStrategy> strategies) {
        log.info("[NotifyConfiguration] NotifyFallbackManager bean registered, strategies={}",
                strategies != null ? strategies.size() : 0);
        return new NotifyFallbackManager(properties, strategies != null ? strategies : List.of());
    }

    // ==================== HTML 模板注册 ====================

    /**
     * 注册 HTML 邮件模板注册表 Bean。
     *
     * <p>集中管理可用的 HTML 邮件模板，供模板引擎按名称检索渲染。
     * 无自定义 Bean 时注册空的默认注册表，模板可由业务侧动态注册。
     */
    @Bean
    @ConditionalOnMissingBean(HtmlTemplateRegistry.class)
    public HtmlTemplateRegistry htmlTemplateRegistry() {
        log.info("[NotifyConfiguration] HtmlTemplateRegistry bean registered");
        return new HtmlTemplateRegistry();
    }

    // ==================== DKIM 签名 ====================

    /**
     * 注册 DKIM 邮件签名 Bean。
     *
     * <p>对出站邮件施加 DKIM 签名以提升投递可信度、降低被判定为垃圾邮件的概率。
     * 仅邮件渠道启用时创建，且无自定义实现时注册默认实现。
     */
    @Bean
    @ConditionalOnMissingBean(DkimSigner.class)
    @ConditionalOnProperty(prefix = "ydsz.notify.email", name = "enabled", havingValue = "true")
    public DkimSigner dkimSigner(NotifyProperties properties) {
        log.info("[NotifyConfiguration] DkimSigner bean registered");
        return new DkimSigner(properties);
    }

    // ==================== 通知偏好 ====================

    /**
     * 注册用户通知偏好管理器 Bean。
     *
     * <p>维护按用户/租户维度的通知渠道与免打扰偏好，决定某条通知是否、以何种渠道触达。
     * Redis 为可选依赖（缺失时按内存或默认策略降级）；无自定义 Bean 时注册默认实现。
     */
    @Bean
    @ConditionalOnMissingBean(NotifyPreferenceManager.class)
    public NotifyPreferenceManager notifyPreferenceManager(ObjectProvider<RedisStringOps> redisStringOpsProvider) {
        RedisStringOps redisStringOps = redisStringOpsProvider.getIfAvailable();
        log.info("[NotifyConfiguration] NotifyPreferenceManager bean registered, redis={}",
                redisStringOps != null);
        return new NotifyPreferenceManager(redisStringOps);
    }

    // ==================== 去重 ====================

    /**
     * 注册通知去重服务 Bean。
     *
     * <p>基于内容指纹+时间窗拦截重复通知，避免同一事件在重试/多渠道下对用户造成骚扰。
     * Redis 为可选依赖（缺失时降级为进程内去重）；无自定义 Bean 时注册默认实现。
     */
    @Bean
    @ConditionalOnMissingBean(NotifyDedupService.class)
    public NotifyDedupService notifyDedupService(NotifyProperties properties,
                                                 ObjectProvider<RedisStringOps> redisStringOpsProvider) {
        RedisStringOps redisStringOps = redisStringOpsProvider.getIfAvailable();
        log.info("[NotifyConfiguration] NotifyDedupService bean registered, redis={}",
                redisStringOps != null);
        return new NotifyDedupService(properties, redisStringOps);
    }

    // ==================== 国际化 ====================

    /**
     * 注册通知国际化（i18n）服务 Bean。
     *
     * <p>提供多语言模板与文案的解析能力，支撑按接收方语言偏好渲染通知内容。
     * 无自定义 Bean 时注册默认实现。
     */
    @Bean
    @ConditionalOnMissingBean(NotifyI18nService.class)
    public NotifyI18nService notifyI18nService() {
        log.info("[NotifyConfiguration] NotifyI18nService bean registered");
        return new NotifyI18nService();
    }

    /**
     * 注册国际化解析器 Bean。
     *
     * <p>结合用户偏好管理器与 i18n 服务，在渲染时确定最终语言与文案来源。
     * 依赖 {@link NotifyPreferenceManager} 与 {@link NotifyI18nService}，无自定义 Bean 时注册默认实现。
     */
    @Bean
    @ConditionalOnMissingBean(NotifyI18nResolver.class)
    public NotifyI18nResolver notifyI18nResolver(NotifyPreferenceManager preferenceManager,
                                                  NotifyI18nService i18nService) {
        log.info("[NotifyConfiguration] NotifyI18nResolver bean registered");
        return new NotifyI18nResolver(preferenceManager, i18nService);
    }

    // ==================== 熔断器 ====================

    /**
     * 注册通知熔断器注册表 Bean。
     *
     * <p>为每个渠道/Provider 维护独立的熔断状态，在下游持续异常时切断请求、防止线程池耗尽与级联故障。
     * 无自定义 Bean 时注册默认实现，供 {@link NotifyServiceImpl} 在发送前做熔断判定。
     */
    @Bean
    @ConditionalOnMissingBean(NotifyCircuitBreakerRegistry.class)
    public NotifyCircuitBreakerRegistry notifyCircuitBreakerRegistry() {
        log.info("[NotifyConfiguration] NotifyCircuitBreakerRegistry bean registered");
        return new NotifyCircuitBreakerRegistry();
    }

    // ==================== 死信队列 ====================

    /**
     * 注册死信处理器 Bean。
     *
     * <p>承接多次重试仍失败的通知，避免其无限占用重试队列；默认使用内存实现，进程重启后丢失。
     * 无自定义 Bean 时注册，业务可替换为持久化实现以保证据。
     */
    @Bean
    @ConditionalOnMissingBean(DeadLetterHandler.class)
    public DeadLetterHandler notifyDeadLetterHandler() {
        log.info("[NotifyConfiguration] InMemoryDeadLetterHandler bean registered");
        return new InMemoryDeadLetterHandler();
    }

    // ==================== 审计日志 ====================

    /**
     * 注册通知审计服务 Bean。
     *
     * <p>记录通知的发送/投递/失败等关键事件，满足合规审计与问题追溯诉求。
     * 无自定义 Bean 时注册默认实现。
     */
    @Bean
    @ConditionalOnMissingBean(NotifyAuditService.class)
    public NotifyAuditService notifyAuditService() {
        log.info("[NotifyConfiguration] NotifyAuditService bean registered");
        return new NotifyAuditService();
    }

    // ==================== 消息聚合器 ====================

    /**
     * 注册时间窗消息聚合器 Bean。
     *
     * <p>将短时间窗内、同一接收方的多条通知合并为一条批量消息，降低打扰与发送成本。
     * 默认窗口 30 秒、单批上限 100 条，可由业务替换以调整聚合策略。
     */
    @Bean
    @ConditionalOnMissingBean(NotificationAggregator.class)
    public TimeWindowAggregator notifyAggregator() {
        log.info("[NotifyConfiguration] TimeWindowAggregator bean registered");
        return new TimeWindowAggregator(30, 100);
    }

    // ==================== 模板变量校验器 ====================

    /**
     * 注册模板变量校验器 Bean。
     *
     * <p>在渲染前校验模板引用变量是否齐全、类型是否合法，避免渲染期因缺参导致通知发送失败或内容错乱。
     * 无自定义 Bean 时注册默认实现。
     */
    @Bean
    @ConditionalOnMissingBean(TemplateVariableValidator.class)
    public TemplateVariableValidator templateVariableValidator() {
        log.info("[NotifyConfiguration] TemplateVariableValidator bean registered");
        return new TemplateVariableValidator();
    }

    // ==================== AliyunSmsProvider ====================

    /**
     * 注册阿里云短信 Provider Bean。
     *
     * <p>在 {@code ydsz.notify.sms.provider=aliyun} 时生效，封装阿里云短信网关的鉴权与发送。
     * 注册于 {@link SmsProvider} 抽象之上，使通知服务无需感知具体厂商；
     * 若已有 {@link SmsProvider} 实现则不再注册，支持腾讯云/华为云等平滑切换。
     */
    @Bean
    @ConditionalOnMissingBean(SmsProvider.class)
    @ConditionalOnProperty(prefix = "ydsz.notify.sms", name = "provider", havingValue = "aliyun")
    public AliyunSmsProvider aliyunSmsProvider(RestTemplate restTemplate, NotifyProperties properties) {
        NotifyProperties.SmsConfig sms = properties.getSms();
        log.info("[NotifyConfiguration] AliyunSmsProvider bean registered, endpoint={}", sms.getEndpoint());
        return new AliyunSmsProvider(restTemplate, sms.getEndpoint(),
                sms.getAccessKeyId(), sms.getAccessKeySecret());
    }

    // ==================== 健康检查 ====================

    /**
     * 注册通知健康检查指示器 Bean。
     *
     * <p>聚合各渠道策略、重试队列积压、熔断状态，对外暴露通知子系统整体健康度（供 /health 探活）。
     * 依赖 Spring HealthIndicator 类存在时启用；无自定义 Bean 时注册默认实现。
     */
    @Bean
    @ConditionalOnMissingBean(NotifyHealthIndicator.class)
    @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
    public NotifyHealthIndicator notifyHealthIndicator(NotifyProperties notifyProperties,
            ObjectProvider<List<NotifyChannelStrategy>> strategiesProvider,
            ObjectProvider<NotifyRetryQueue> retryQueueProvider,
            ObjectProvider<NotifyCircuitBreakerRegistry> circuitBreakerProvider) {
        log.info("[NotifyConfiguration] NotifyHealthIndicator bean registered");
        return new NotifyHealthIndicator(notifyProperties, strategiesProvider,
                retryQueueProvider, circuitBreakerProvider);
    }

    // ==================== RestTemplate =====================

    /**
     * 注册通知专用 {@link RestTemplate} Bean。
     *
     * <p>供短信/第三方网关等 HTTP 调用复用，设置 5s 连接、10s 读取超时以约束外部依赖最大阻塞时间。
     * 使用 {@code @ConditionalOnMissingBean} 允许业务侧提供带拦截器/连接池的定制实例。
     */
    @Bean
    @ConditionalOnMissingBean
    public RestTemplate notifyRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return new RestTemplate(factory);
    }

    // ==================== 限流器 ====================

    /**
     * 注册通知限流管理器 Bean。
     *
     * <p>按渠道/租户维度对发送速率进行滑动窗口限流，保护下游网关不被突发流量击穿。
     * 依据 {@code ydsz.notify.ratelimit} 配置初始化；无自定义 Bean 时注册默认实现。
     *
     * <p>P0-1 架构优化：委托 {@link RedisRateLimiter} 实现分布式限流，
     * RedisRateLimiter 不可用时降级为不限制。
     */
    @Bean
    @ConditionalOnMissingBean(NotifyRateLimiterManager.class)
    public NotifyRateLimiterManager notifyRateLimiterManager(NotifyProperties properties,
                                                             ObjectProvider<RedisRateLimiter> redisRateLimiterProvider) {
        NotifyProperties.RateLimit rateLimitConfig = properties.getRateLimit();
        RedisRateLimiter redisRateLimiter = redisRateLimiterProvider.getIfAvailable();
        log.info("[NotifyConfiguration] NotifyRateLimiterManager bean registered, redisRateLimiter={}, enabled={}, defaultMaxRequests={}, defaultWindowSeconds={}",
                redisRateLimiter != null, rateLimitConfig.isEnabled(), rateLimitConfig.getDefaultMaxRequests(), rateLimitConfig.getDefaultWindowSeconds());
        return new NotifyRateLimiterManager(rateLimitConfig, redisRateLimiter);
    }

    // ==================== NotifyService ====================

    /**
     * 创建统一消息通知服务，自动收集所有 {@link NotifyChannelStrategy} 实现并组装。
     *
     * <p>集成限流、熔断、降级、去重、指标、审计、偏好、聚合等全部横切关注点。
     */
    @Bean
    @ConditionalOnMissingBean(NotifyService.class)
    public NotifyService notifyService(ListableBeanFactory beanFactory,
            ObjectProvider<TemplateEngine> templateEngineProvider,
            ObjectProvider<NotifyRateLimiterManager> rateLimiterManagerProvider,
            @Qualifier("notifyVirtualThreadExecutor") ObjectProvider<ExecutorService> executorProvider,
            ObjectProvider<NotifyCircuitBreakerRegistry> circuitBreakerRegistryProvider,
            ObjectProvider<NotifyFallbackManager> fallbackManagerProvider,
            ObjectProvider<NotifyAuditService> auditServiceProvider,
            ObjectProvider<NotifyMetrics> metricsProvider,
            ObjectProvider<NotifyPreferenceManager> preferenceManagerProvider,
            ObjectProvider<NotifyDedupService> dedupServiceProvider,
            ObjectProvider<NotificationAggregator> aggregatorProvider) {
        List<NotifyChannelStrategy> strategies = beanFactory.getBeansOfType(NotifyChannelStrategy.class)
                .values()
                .stream()
                .toList();

        TemplateEngine templateEngine = templateEngineProvider.getIfAvailable();
        if (templateEngine != null) {
            for (NotifyChannelStrategy strategy : strategies) {
                strategy.setTemplateEngine(templateEngine);
            }
            log.info("[NotifyConfiguration] TemplateEngine 已注入到 {} 个渠道策略", strategies.size());
        }

        NotifyServiceImpl service = new NotifyServiceImpl(
                strategies,
                rateLimiterManagerProvider.getIfAvailable(),
                executorProvider.getIfAvailable(),
                circuitBreakerRegistryProvider.getIfAvailable(),
                fallbackManagerProvider.getIfAvailable(),
                auditServiceProvider.getIfAvailable(),
                metricsProvider.getIfAvailable(),
                preferenceManagerProvider.getIfAvailable(),
                dedupServiceProvider.getIfAvailable(),
                aggregatorProvider.getIfAvailable()
        );
        notifyServiceInstance = service;
        return service;
    }

    // ==================== 重试队列 ====================

    /**
     * 注册通知重试队列 Bean。
     *
     * <p>承接发送失败的异步重试；当配置 {@code persistent=true} 时落地 Redis 以保证进程重启后仍可恢复，
     * 否则退化为内存队列（重启即丢）。达到最大重试次数后转交死信处理器。缓存实例引用供定时任务消费。
     */
    @Bean
    @ConditionalOnMissingBean(NotifyRetryQueue.class)
    public NotifyRetryQueue notifyRetryQueue(NotifyProperties properties,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            ObjectProvider<DeadLetterHandler> deadLetterHandlerProvider) {
        NotifyProperties.RetryQueueConfig retryConfig = properties.getRetryQueue();
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        DeadLetterHandler dlqHandler = deadLetterHandlerProvider.getIfAvailable();

        NotifyRetryQueue queue;
        if (retryConfig.isPersistent()) {
            queue = new PersistentNotifyRetryQueue(redisTemplate,
                    retryConfig.getMaxRetries(), retryConfig.getCapacity(),
                    retryConfig.getBatchSize(), retryConfig.getRedisKeyPrefix(), dlqHandler);
            log.info("[NotifyConfiguration] PersistentNotifyRetryQueue bean registered, persistent=true, maxRetries={}, batchSize={}, redisKeyPrefix={}, dlq={}",
                    retryConfig.getMaxRetries(), retryConfig.getBatchSize(), retryConfig.getRedisKeyPrefix(), dlqHandler != null);
        } else {
            queue = new PersistentNotifyRetryQueue(null,
                    retryConfig.getMaxRetries(), retryConfig.getCapacity(), retryConfig.getBatchSize(),
                    null, dlqHandler);
            log.info("[NotifyConfiguration] In-memory NotifyRetryQueue bean registered, persistent=false, maxRetries={}, batchSize={}, dlq={}",
                    retryConfig.getMaxRetries(), retryConfig.getBatchSize(), dlqHandler != null);
        }
        retryQueueInstance = queue;
        return queue;
    }

    // ==================== 异步通知服务 ====================

    /**
     * 注册异步通知服务 Bean。
     *
     * <p>将通知发送异步化，借助虚拟线程池解耦主流程与下游调用，提升吞吐；
     * 失败时回落到重试队列做补偿。无自定义 Bean 时注册默认实现。
     */
    @Bean
    @ConditionalOnMissingBean(AsyncNotifyService.class)
    public AsyncNotifyService asyncNotifyService(NotifyService notifyService, NotifyRetryQueue retryQueue,
                                                 @Qualifier("notifyVirtualThreadExecutor") ExecutorService executor) {
        log.info("[NotifyConfiguration] AsyncNotifyService bean registered");
        return new AsyncNotifyService(notifyService, retryQueue, executor);
    }

    // ==================== 事务安全通知发布器 ====================

    /**
     * 注册事务安全通知发布器 Bean。
     *
     * <p>保证通知仅在所在数据库事务成功提交后才会真正发出，避免事务回滚却已外发通知的一致性问题。
     * 借助 {@link ApplicationEventPublisher} 在事务提交后触发。无自定义 Bean 时注册默认实现。
     */
    @Bean
    @ConditionalOnMissingBean(TransactionalNotifyPublisher.class)
    public TransactionalNotifyPublisher transactionalNotifyPublisher(
            AsyncNotifyService asyncNotifyService,
            ApplicationEventPublisher eventPublisher) {
        log.info("[NotifyConfiguration] TransactionalNotifyPublisher bean registered");
        return new TransactionalNotifyPublisher(eventPublisher, asyncNotifyService);
    }

    // ==================== 虚拟线程池 =====================

    /**
     * 注册通知共享虚拟线程池 Bean。
     *
     * <p>为异步发送、聚合刷新等提供高并发、低开销的虚拟线程执行器；{@code destroyMethod="shutdown"} 确保容器关闭时优雅回收。
     * 以命名 bean 避免与业务线程池冲突；无同名 Bean 时注册默认实现。
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "notifyVirtualThreadExecutor")
    public ExecutorService notifyVirtualThreadExecutor() {
        log.info("[NotifyConfiguration] 创建共享虚拟线程池 notifyVirtualThreadExecutor");
        return ExecutorUtils.newVirtualThreadExecutor("notify-virtual-");
    }

    // ==================== 定时任务 ====================

    /**
     * 定时消费重试队列。
     *
     * <p>调度周期由 {@code ydsz.notify.scheduler.retry-queue-fixed-delay-ms} 控制，默认 5000ms。
     */
    @Scheduled(fixedDelayString = "${ydsz.notify.scheduler.retry-queue-fixed-delay-ms:5000}")
    public void processRetryQueue() {
        if (notifyServiceInstance != null && retryQueueInstance != null && retryQueueInstance.getQueueSize() > 0) {
            log.debug("[NotifyRetryQueue] 开始消费重试队列, queueSize={}", retryQueueInstance.getQueueSize());
            int processed = retryQueueInstance.retryBatch(notifyServiceInstance);
            if (processed > 0) {
                log.info("[NotifyRetryQueue] 批量重试完成, queueSize={}, 本次处理={}",
                        retryQueueInstance.getQueueSize(), processed);
            }
        }
    }

    /**
     * 定时刷新聚合消息缓冲区。
     *
     * <p>调度周期由 {@code ydsz.notify.scheduler.aggregate-flush-fixed-delay-ms} 控制，默认 30000ms。
     */
    @Scheduled(fixedDelayString = "${ydsz.notify.scheduler.aggregate-flush-fixed-delay-ms:30000}")
    public void flushAggregatedMessages() {
        if (notifyServiceInstance != null) {
            notifyServiceInstance.flushAggregatedMessages();
        }
    }

    // ==================== 通知辅助类 ====================

    /**
     * 注册通知辅助工具类 Bean。
     *
     * <p>封装面向业务代码的便捷发送 API，屏蔽底层渠道选择/降级/异步等复杂度。
     * 依赖统一通知服务；无自定义 Bean 时注册默认实现。
     */
    @Bean
    @ConditionalOnMissingBean(NotifyHelper.class)
    public NotifyHelper notifyHelper(NotifyService notifyService) {
        log.info("[NotifyConfiguration] NotifyHelper bean registered");
        return new NotifyHelper(notifyService);
    }
}
