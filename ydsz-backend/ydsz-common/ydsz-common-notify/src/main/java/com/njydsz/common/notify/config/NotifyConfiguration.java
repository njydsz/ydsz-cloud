package com.njydsz.common.notify.config;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;

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
import com.njydsz.common.notify.helper.NotifyHelper;
import com.njydsz.common.notify.dedup.NotifyDedupService;
import com.njydsz.common.notify.fallback.NotifyFallbackManager;
import com.njydsz.common.notify.health.NotifyHealthIndicator;
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
import com.njydsz.common.util.concurrent.ExecutorUtils;

import io.micrometer.core.instrument.MeterRegistry;

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

	@Bean
	@ConditionalOnMissingBean(NotifyPasswordResolver.class)
	@ConditionalOnProperty(prefix = "ydsz.notify.email", name = "enabled", havingValue = "true")
	public NotifyPasswordResolver notifyPasswordResolver(NotifyProperties properties) {
		log.info("[NotifyConfiguration] NotifyPasswordResolver bean registered");
		return new NotifyPasswordResolver(properties);
	}

	// ==================== JavaMailSender ====================

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

	@Bean
	@ConditionalOnMissingBean(EmailSmtpHealthChecker.class)
	@ConditionalOnClass(JavaMailSender.class)
	@ConditionalOnProperty(prefix = "ydsz.notify.email", name = "enabled", havingValue = "true")
	public EmailSmtpHealthChecker emailSmtpHealthChecker(NotifyProperties properties) {
		log.info("[NotifyConfiguration] EmailSmtpHealthChecker bean registered");
		return new EmailSmtpHealthChecker(properties);
	}

	// ==================== 指标埋点 ====================

	@Bean
	@ConditionalOnMissingBean(NotifyMetrics.class)
	public NotifyMetrics notifyMetrics(ObjectProvider<MeterRegistry> meterRegistryProvider) {
		MeterRegistry registry = meterRegistryProvider.getIfAvailable();
		log.info("[NotifyConfiguration] NotifyMetrics bean registered, meterRegistry={}",
				registry != null ? registry.getClass().getSimpleName() : "null");
		return new NotifyMetrics(registry);
	}

	// ==================== 邮件追踪 ====================

	@Bean
	@ConditionalOnMissingBean(EmailTrackingService.class)
	@ConditionalOnProperty(prefix = "ydsz.notify.email", name = "enabled", havingValue = "true")
	public EmailTrackingService emailTrackingService(NotifyProperties properties,
													 ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
		StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
		log.info("[NotifyConfiguration] EmailTrackingService bean registered, redis={}",
				redisTemplate != null);
		return new EmailTrackingService(properties, redisTemplate);
	}

	// ==================== 渠道降级 ====================

	@Bean
	@ConditionalOnMissingBean(NotifyFallbackManager.class)
	public NotifyFallbackManager notifyFallbackManager(NotifyProperties properties,
													   List<NotifyChannelStrategy> strategies) {
		log.info("[NotifyConfiguration] NotifyFallbackManager bean registered, strategies={}",
				strategies != null ? strategies.size() : 0);
		return new NotifyFallbackManager(properties, strategies != null ? strategies : List.of());
	}

	// ==================== HTML 模板注册 ====================

	@Bean
	@ConditionalOnMissingBean(HtmlTemplateRegistry.class)
	public HtmlTemplateRegistry htmlTemplateRegistry() {
		log.info("[NotifyConfiguration] HtmlTemplateRegistry bean registered");
		return new HtmlTemplateRegistry();
	}

	// ==================== DKIM 签名 ====================

	@Bean
	@ConditionalOnMissingBean(DkimSigner.class)
	@ConditionalOnProperty(prefix = "ydsz.notify.email", name = "enabled", havingValue = "true")
	public DkimSigner dkimSigner(NotifyProperties properties) {
		log.info("[NotifyConfiguration] DkimSigner bean registered");
		return new DkimSigner(properties);
	}

	// ==================== 通知偏好 ====================

	@Bean
	@ConditionalOnMissingBean(NotifyPreferenceManager.class)
	public NotifyPreferenceManager notifyPreferenceManager(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
		StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
		log.info("[NotifyConfiguration] NotifyPreferenceManager bean registered, redis={}",
				redisTemplate != null);
		return new NotifyPreferenceManager(redisTemplate);
	}

	// ==================== 去重 ====================

	@Bean
	@ConditionalOnMissingBean(NotifyDedupService.class)
	public NotifyDedupService notifyDedupService(NotifyProperties properties,
												 ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
		StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
		log.info("[NotifyConfiguration] NotifyDedupService bean registered, redis={}",
				redisTemplate != null);
		return new NotifyDedupService(properties, redisTemplate);
	}

	// ==================== 国际化 ====================

	@Bean
	@ConditionalOnMissingBean(NotifyI18nService.class)
	public NotifyI18nService notifyI18nService() {
		log.info("[NotifyConfiguration] NotifyI18nService bean registered");
		return new NotifyI18nService();
	}

	@Bean
	@ConditionalOnMissingBean(NotifyI18nResolver.class)
	public NotifyI18nResolver notifyI18nResolver(NotifyPreferenceManager preferenceManager,
												  NotifyI18nService i18nService) {
		log.info("[NotifyConfiguration] NotifyI18nResolver bean registered");
		return new NotifyI18nResolver(preferenceManager, i18nService);
	}

	// ==================== 熔断器 ====================

	@Bean
	@ConditionalOnMissingBean(NotifyCircuitBreakerRegistry.class)
	public NotifyCircuitBreakerRegistry notifyCircuitBreakerRegistry() {
		log.info("[NotifyConfiguration] NotifyCircuitBreakerRegistry bean registered");
		return new NotifyCircuitBreakerRegistry();
	}

	// ==================== 死信队列 ====================

	@Bean
	@ConditionalOnMissingBean(DeadLetterHandler.class)
	public DeadLetterHandler notifyDeadLetterHandler() {
		log.info("[NotifyConfiguration] InMemoryDeadLetterHandler bean registered");
		return new InMemoryDeadLetterHandler();
	}

	// ==================== 审计日志 ====================

	@Bean
	@ConditionalOnMissingBean(NotifyAuditService.class)
	public NotifyAuditService notifyAuditService() {
		log.info("[NotifyConfiguration] NotifyAuditService bean registered");
		return new NotifyAuditService();
	}

	// ==================== 消息聚合器 ====================

	@Bean
	@ConditionalOnMissingBean(NotificationAggregator.class)
	public TimeWindowAggregator notifyAggregator() {
		log.info("[NotifyConfiguration] TimeWindowAggregator bean registered");
		return new TimeWindowAggregator(30, 100);
	}

	// ==================== 模板变量校验器 ====================

	@Bean
	@ConditionalOnMissingBean(TemplateVariableValidator.class)
	public TemplateVariableValidator templateVariableValidator() {
		log.info("[NotifyConfiguration] TemplateVariableValidator bean registered");
		return new TemplateVariableValidator();
	}

	// ==================== AliyunSmsProvider ====================

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

	@Bean
	@ConditionalOnMissingBean
	public RestTemplate notifyRestTemplate() {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(5));
		factory.setReadTimeout(Duration.ofSeconds(10));
		return new RestTemplate(factory);
	}

	// ==================== 限流器 ====================

	@Bean
	@ConditionalOnMissingBean(NotifyRateLimiterManager.class)
	public NotifyRateLimiterManager notifyRateLimiterManager(NotifyProperties properties) {
		NotifyProperties.RateLimit rateLimitConfig = properties.getRateLimit();
		log.info("[NotifyConfiguration] NotifyRateLimiterManager bean registered, enabled={}, defaultMaxRequests={}, defaultWindowSeconds={}",
				rateLimitConfig.isEnabled(), rateLimitConfig.getDefaultMaxRequests(), rateLimitConfig.getDefaultWindowSeconds());
		return new NotifyRateLimiterManager(rateLimitConfig);
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

	@Bean
	@ConditionalOnMissingBean(AsyncNotifyService.class)
	public AsyncNotifyService asyncNotifyService(NotifyService notifyService, NotifyRetryQueue retryQueue,
												 @Qualifier("notifyVirtualThreadExecutor") ExecutorService executor) {
		log.info("[NotifyConfiguration] AsyncNotifyService bean registered");
		return new AsyncNotifyService(notifyService, retryQueue, executor);
	}

	// ==================== 事务安全通知发布器 ====================

	@Bean
	@ConditionalOnMissingBean(TransactionalNotifyPublisher.class)
	public TransactionalNotifyPublisher transactionalNotifyPublisher(
			AsyncNotifyService asyncNotifyService,
			ApplicationEventPublisher eventPublisher) {
		log.info("[NotifyConfiguration] TransactionalNotifyPublisher bean registered");
		return new TransactionalNotifyPublisher(eventPublisher, asyncNotifyService);
	}

	// ==================== 虚拟线程池 =====================

	@Bean(destroyMethod = "shutdown")
	@ConditionalOnMissingBean(name = "notifyVirtualThreadExecutor")
	public ExecutorService notifyVirtualThreadExecutor() {
		log.info("[NotifyConfiguration] 创建共享虚拟线程池 notifyVirtualThreadExecutor");
		return ExecutorUtils.newVirtualThreadExecutor("notify-virtual-");
	}

	// ==================== 定时任务 ====================

	/**
	 * 定时消费重试队列，每 5 秒执行一次。
	 */
	@Scheduled(fixedDelay = 5000)
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
	 * 定时刷新聚合消息缓冲区，每 30 秒执行一次。
	 */
	@Scheduled(fixedDelay = 30000)
	public void flushAggregatedMessages() {
		if (notifyServiceInstance != null) {
			notifyServiceInstance.flushAggregatedMessages();
		}
	}

	// ==================== 通知辅助类 ====================

	@Bean
	@ConditionalOnMissingBean(NotifyHelper.class)
	public NotifyHelper notifyHelper(NotifyService notifyService) {
		log.info("[NotifyConfiguration] NotifyHelper bean registered");
		return new NotifyHelper(notifyService);
	}
}
