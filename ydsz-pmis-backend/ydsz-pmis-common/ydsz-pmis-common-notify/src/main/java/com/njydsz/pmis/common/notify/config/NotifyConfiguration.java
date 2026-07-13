package com.njydsz.pmis.common.notify.config;

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
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import com.njydsz.pmis.common.notify.channel.NotifyChannelStrategy;
import com.njydsz.pmis.common.notify.core.AsyncNotifyService;
import com.njydsz.pmis.common.notify.core.NotifyRetryQueue;
import com.njydsz.pmis.common.notify.core.NotifyService;
import com.njydsz.pmis.common.notify.core.NotifyServiceImpl;
import com.njydsz.pmis.common.notify.core.PersistentNotifyRetryQueue;
import com.njydsz.pmis.common.notify.dedup.NotifyDedupService;
import com.njydsz.pmis.common.notify.fallback.NotifyFallbackManager;
import com.njydsz.pmis.common.notify.i18n.NotifyI18nService;
import com.njydsz.pmis.common.notify.metrics.NotifyMetrics;
import com.njydsz.pmis.common.notify.preference.NotifyPreferenceManager;
import com.njydsz.pmis.common.notify.queue.EmailQueueService;
import com.njydsz.pmis.common.notify.ratelimit.NotifyRateLimiterManager;
import com.njydsz.pmis.common.notify.security.DkimSigner;
import com.njydsz.pmis.common.notify.security.EmailSmtpHealthChecker;
import com.njydsz.pmis.common.notify.security.NotifyPasswordResolver;
import com.njydsz.pmis.common.notify.template.HtmlTemplateRegistry;
import com.njydsz.pmis.common.notify.template.TemplateEngine;
import com.njydsz.pmis.common.notify.tracking.EmailTrackingService;
import com.njydsz.pmis.common.util.concurrent.ExecutorUtils;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * 统一消息通知自动配置类
 *
 * <p>各渠道 Sender（Email/SMS/WeCom/DingTalk/Feishu）通过实现 {@link NotifyChannelStrategy} 接口自动注册。
 * 邮件渠道的 {@link JavaMailSender} Bean 由本配置类根据 {@code ydsz.notify.email} 配置自动创建，
 * 支持密码加密解密（P0-1）、SMTP 健康探活（P0-2）、内容 XSS 防护（P0-3）、指标埋点（P1-4）、
 * 追踪像素（P1-5）、渠道降级（P1-6）、HTML 模板注册（P2-7）、邮件队列（P2-8）、
 * DKIM 签名（P2-9）、多提供商抽象（P2-10）、通知偏好（P3-12）、去重（P3-13）、国际化（P3-14）。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(NotifyProperties.class)
@ConditionalOnClass(name = "org.apache.hc.client5.http.classic.HttpClient")
@ConditionalOnProperty(prefix = "ydsz.notify", name = "enabled", havingValue = "true", matchIfMissing = true)
public class NotifyConfiguration {

	private final Logger log = LoggerFactory.getLogger(NotifyConfiguration.class);

	/** 通知服务实例，供定时任务消费重试队列时使用 */
	private NotifyService notifyServiceInstance;
	/** 通知重试队列实例，供定时任务消费重试队列时使用 */
	private NotifyRetryQueue retryQueueInstance;

	// ==================== P0-1 密码加密 ====================

	/**
	 * 创建通知模块密码解析器（P0-1）
	 *
	 * <p>当 EmailConfig.security.passwordEncrypted=true 时，通过 Jasypt 解密 SMTP 密码。
	 *
	 * @param properties 通知配置属性
	 * @return NotifyPasswordResolver 实例
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
	 * 创建邮件发送器 {@link JavaMailSender}。
	 *
	 * <p>根据 {@code ydsz.notify.email} 配置构建 SMTP 连接参数，支持 SSL/TLS 加密、STARTTLS 升级、
	 * 自定义 JavaMail 属性等。当邮件渠道未启用或 SMTP 主机未配置时，不创建该 Bean。
	 * 若容器中已存在 {@link JavaMailSender} Bean，则不重复创建。
	 *
	 * <p>P0-1：当 security.passwordEncrypted=true 时，通过 {@link NotifyPasswordResolver} 自动解密密码。
	 *
	 * @param properties       通知配置属性
	 * @param passwordResolver 密码解析器（可选，用于加密密码解密）
	 * @return JavaMailSender 实例
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

		// P0-1：密码解密
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

	// ==================== P0-2 SMTP 健康探活 ====================

	/**
	 * 创建 SMTP 健康检查器（P0-2）
	 *
	 * @param properties 通知配置属性
	 * @return EmailSmtpHealthChecker 实例
	 */
	@Bean
	@ConditionalOnMissingBean(EmailSmtpHealthChecker.class)
	@ConditionalOnClass(JavaMailSender.class)
	@ConditionalOnProperty(prefix = "ydsz.notify.email", name = "enabled", havingValue = "true")
	public EmailSmtpHealthChecker emailSmtpHealthChecker(NotifyProperties properties) {
		log.info("[NotifyConfiguration] EmailSmtpHealthChecker bean registered");
		return new EmailSmtpHealthChecker(properties);
	}

	// ==================== P0-3 XSS 防护 ====================
	// EmailContentSanitizer 为静态工具类，无需注册 Bean

	// ==================== P1-4 指标埋点 ====================

	/**
	 * 创建通知指标收集器（P1-4）
	 *
	 * @param meterRegistryProvider Micrometer MeterRegistry 提供者（可选）
	 * @return NotifyMetrics 实例
	 */
	@Bean
	@ConditionalOnMissingBean(NotifyMetrics.class)
	public NotifyMetrics notifyMetrics(ObjectProvider<MeterRegistry> meterRegistryProvider) {
		MeterRegistry registry = meterRegistryProvider.getIfAvailable();
		log.info("[NotifyConfiguration] NotifyMetrics bean registered, meterRegistry={}",
				registry != null ? registry.getClass().getSimpleName() : "null");
		return new NotifyMetrics(registry);
	}

	// ==================== P1-5 邮件追踪 ====================

	/**
	 * 创建邮件追踪服务（P1-5）
	 *
	 * @param properties          通知配置属性
	 * @param redisTemplateProvider Redis 模板提供者（可选）
	 * @return EmailTrackingService 实例
	 */
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

	// ==================== P1-6 渠道降级 ====================

	/**
	 * 创建渠道降级管理器（P1-6）
	 *
	 * @param properties 通知配置属性
	 * @param strategies 所有渠道策略列表
	 * @return NotifyFallbackManager 实例
	 */
	@Bean
	@ConditionalOnMissingBean(NotifyFallbackManager.class)
	public NotifyFallbackManager notifyFallbackManager(NotifyProperties properties,
													   List<NotifyChannelStrategy> strategies) {
		log.info("[NotifyConfiguration] NotifyFallbackManager bean registered, strategies={}",
				strategies != null ? strategies.size() : 0);
		return new NotifyFallbackManager(properties, strategies != null ? strategies : List.of());
	}

	// ==================== P2-7 HTML 模板注册 ====================

	/**
	 * 创建 HTML 邮件模板注册中心（P2-7）
	 *
	 * @return HtmlTemplateRegistry 实例
	 */
	@Bean
	@ConditionalOnMissingBean(HtmlTemplateRegistry.class)
	public HtmlTemplateRegistry htmlTemplateRegistry() {
		log.info("[NotifyConfiguration] HtmlTemplateRegistry bean registered");
		return new HtmlTemplateRegistry();
	}

	// ==================== P2-8 邮件队列 ====================

	/**
	 * 创建邮件队列服务（P2-8）
	 *
	 * @param redisTemplateProvider Redis 模板提供者（可选）
	 * @param executor             虚拟线程池
	 * @return EmailQueueService 实例
	 */
	@Bean
	@ConditionalOnMissingBean(EmailQueueService.class)
	@ConditionalOnClass(name = "com.fasterxml.jackson.databind.ObjectMapper")
	public EmailQueueService emailQueueService(ObjectProvider<StringRedisTemplate> redisTemplateProvider,
											   @Qualifier("notifyVirtualThreadExecutor") ExecutorService executor) {
		StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
		log.info("[NotifyConfiguration] EmailQueueService bean registered, redis={}",
				redisTemplate != null);
		return new EmailQueueService(redisTemplate, executor);
	}

	// ==================== P2-9 DKIM 签名 ====================

	/**
	 * 创建 DKIM 签名器（P2-9）
	 *
	 * @param properties 通知配置属性
	 * @return DkimSigner 实例
	 */
	@Bean
	@ConditionalOnMissingBean(DkimSigner.class)
	@ConditionalOnProperty(prefix = "ydsz.notify.email", name = "enabled", havingValue = "true")
	public DkimSigner dkimSigner(NotifyProperties properties) {
		log.info("[NotifyConfiguration] DkimSigner bean registered");
		return new DkimSigner(properties);
	}

	// ==================== P3-12 通知偏好 ====================

	/**
	 * 创建通知偏好管理器（P3-12）
	 *
	 * @param redisTemplateProvider Redis 模板提供者（可选）
	 * @return NotifyPreferenceManager 实例
	 */
	@Bean
	@ConditionalOnMissingBean(NotifyPreferenceManager.class)
	public NotifyPreferenceManager notifyPreferenceManager(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
		StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
		log.info("[NotifyConfiguration] NotifyPreferenceManager bean registered, redis={}",
				redisTemplate != null);
		return new NotifyPreferenceManager(redisTemplate);
	}

	// ==================== P3-13 邮件去重 ====================

	/**
	 * 创建邮件去重服务（P3-13）
	 *
	 * @param properties          通知配置属性
	 * @param redisTemplateProvider Redis 模板提供者（可选）
	 * @return NotifyDedupService 实例
	 */
	@Bean
	@ConditionalOnMissingBean(NotifyDedupService.class)
	public NotifyDedupService notifyDedupService(NotifyProperties properties,
												 ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
		StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
		log.info("[NotifyConfiguration] NotifyDedupService bean registered, redis={}",
				redisTemplate != null);
		return new NotifyDedupService(properties, redisTemplate);
	}

	// ==================== P3-14 国际化 ====================

	/**
	 * 创建通知国际化服务（P3-14）
	 *
	 * @return NotifyI18nService 实例
	 */
	@Bean
	@ConditionalOnMissingBean(NotifyI18nService.class)
	public NotifyI18nService notifyI18nService() {
		log.info("[NotifyConfiguration] NotifyI18nService bean registered");
		return new NotifyI18nService();
	}

	// ==================== RestTemplate ====================

	/**
	 * 创建用于通知渠道 HTTP 调用的 {@link RestTemplate}。
	 *
	 * @return 配置了超时参数的 RestTemplate 实例
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
	 * 创建渠道限流管理器，用于控制各通知渠道的发送频率。
	 *
	 * @param properties 通知配置属性
	 * @return NotifyRateLimiterManager 实例
	 */
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
	 * @param beanFactory              用于扫描所有 NotifyChannelStrategy 实现的 Bean 工厂
	 * @param templateEngineProvider   可选的模板引擎
	 * @param rateLimiterManagerProvider 可选的限流管理器
	 * @param executorProvider         可选的并行执行器
	 * @return 组装完成的 NotifyService 实例
	 */
	@Bean
	@ConditionalOnMissingBean(NotifyService.class)
	public NotifyService notifyService(ListableBeanFactory beanFactory,
			ObjectProvider<TemplateEngine> templateEngineProvider,
			ObjectProvider<NotifyRateLimiterManager> rateLimiterManagerProvider,
			@Qualifier("notifyVirtualThreadExecutor") ObjectProvider<ExecutorService> executorProvider) {
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

		NotifyRateLimiterManager rateLimiterManager = rateLimiterManagerProvider.getIfAvailable();
		ExecutorService parallelExecutor = executorProvider.getIfAvailable();

		log.info("[NotifyConfiguration] NotifyService bean registered, strategies={}, rateLimitEnabled={}, parallelEnabled={}",
				strategies.size(), rateLimiterManager != null, parallelExecutor != null);
		NotifyService service = new NotifyServiceImpl(strategies, rateLimiterManager, parallelExecutor);
		notifyServiceInstance = service;
		return service;
	}

	// ==================== 重试队列 ====================

	/**
	 * 创建通知重试队列，用于管理发送失败通知的重试调度。
	 *
	 * @param properties             通知配置属性
	 * @param redisTemplateProvider  Redis 模板提供者（可选）
	 * @return NotifyRetryQueue 实例
	 */
	@Bean
	@ConditionalOnMissingBean(NotifyRetryQueue.class)
	public NotifyRetryQueue notifyRetryQueue(NotifyProperties properties,
			ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
		NotifyProperties.RetryQueueConfig retryConfig = properties.getRetryQueue();
		StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();

		NotifyRetryQueue queue;
		if (retryConfig.isPersistent()) {
			queue = new PersistentNotifyRetryQueue(redisTemplate,
					retryConfig.getMaxRetries(), retryConfig.getCapacity(),
					retryConfig.getBatchSize(), retryConfig.getRedisKeyPrefix());
			log.info("[NotifyConfiguration] PersistentNotifyRetryQueue bean registered, persistent=true, maxRetries={}, batchSize={}, redisKeyPrefix={}",
					retryConfig.getMaxRetries(), retryConfig.getBatchSize(), retryConfig.getRedisKeyPrefix());
		} else {
			queue = new PersistentNotifyRetryQueue(null,
					retryConfig.getMaxRetries(), retryConfig.getCapacity(), retryConfig.getBatchSize());
			log.info("[NotifyConfiguration] In-memory NotifyRetryQueue bean registered, persistent=false, maxRetries={}, batchSize={}",
					retryConfig.getMaxRetries(), retryConfig.getBatchSize());
		}
		retryQueueInstance = queue;
		return queue;
	}

	// ==================== 异步通知服务 ====================

	/**
	 * 创建异步通知服务，封装 {@link NotifyService} 并集成重试队列支持。
	 *
	 * @param notifyService 统一通知服务
	 * @param retryQueue    通知重试队列
	 * @param executor      共享虚拟线程池
	 * @return AsyncNotifyService 实例
	 */
	@Bean
	@ConditionalOnMissingBean(AsyncNotifyService.class)
	public AsyncNotifyService asyncNotifyService(NotifyService notifyService, NotifyRetryQueue retryQueue,
												 @Qualifier("notifyVirtualThreadExecutor") ExecutorService executor) {
		log.info("[NotifyConfiguration] AsyncNotifyService bean registered");
		return new AsyncNotifyService(notifyService, retryQueue, executor);
	}

	// ==================== 虚拟线程池 ====================

	/**
	 * 创建共享的虚拟线程池，供通知模块各组件使用
	 *
	 * @return 共享的虚拟线程池 ExecutorService
	 */
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
}
