package com.njydsz.pmis.common.notify.config;

import com.njydsz.pmis.common.notify.channel.NotifyChannelStrategy;
import com.njydsz.pmis.common.notify.core.NotifyService;
import com.njydsz.pmis.common.notify.core.NotifyServiceImpl;
import com.njydsz.pmis.common.notify.core.PersistentNotifyRetryQueue;
import com.njydsz.pmis.common.notify.ratelimit.NotifyRateLimiterManager;
import com.njydsz.pmis.common.notify.template.TemplateEngine;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import com.njydsz.pmis.common.notify.core.AsyncNotifyService;
import com.njydsz.pmis.common.notify.core.NotifyRetryQueue;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import com.njydsz.pmis.common.util.concurrent.ExecutorUtils;

/**
 * 统一消息通知自动配置类
 *
 * <p>各渠道 Sender（Email/SMS/WeCom/DingTalk/Feishu）通过实现 {@link NotifyChannelStrategy} 接口自动注册。
 * 邮件渠道的 {@link JavaMailSender} Bean 由本配置类根据 {@code ydsz.notify.email} 配置自动创建。
 * 若容器中已存在 {@link JavaMailSender} Bean（如由 spring-boot-starter-mail 自动配置），则不重复创建。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@AutoConfiguration
@RequiredArgsConstructor
@EnableConfigurationProperties(NotifyProperties.class)
@ConditionalOnClass(name = "org.apache.hc.client5.http.classic.HttpClient")
@ConditionalOnProperty(prefix = "ydsz.notify", name = "enabled", havingValue = "true", matchIfMissing = true)
public class NotifyConfiguration {

	private final Logger log = LoggerFactory.getLogger(NotifyConfiguration.class);

	/** 通知服务实例，供定时任务消费重试队列时使用 */
	private NotifyService notifyServiceInstance;
	/** 通知重试队列实例，供定时任务消费重试队列时使用 */
	private NotifyRetryQueue retryQueueInstance;

	/**
	 * 创建邮件发送器 {@link JavaMailSender}。
	 *
	 * <p>根据 {@code ydsz.notify.email} 配置构建 SMTP 连接参数，支持 SSL/TLS 加密、STARTTLS 升级、
	 * 自定义 JavaMail 属性等。当邮件渠道未启用或 SMTP 主机未配置时，不创建该 Bean。
	 *
	 * <p>若容器中已存在 {@link JavaMailSender} Bean（如通过 spring.mail.* 标准配置自动装配），
	 * 则不重复创建，使用已有 Bean。
	 *
	 * @param properties 通知配置属性
	 * @return JavaMailSender 实例
	 */
	@Bean
	@ConditionalOnMissingBean(JavaMailSender.class)
	@ConditionalOnClass(JavaMailSender.class)
	@ConditionalOnProperty(prefix = "ydsz.notify.email", name = "enabled", havingValue = "true")
	public JavaMailSender notifyJavaMailSender(NotifyProperties properties) {
		NotifyProperties.EmailConfig email = properties.getEmail();
		JavaMailSenderImpl sender = new JavaMailSenderImpl();
		sender.setHost(email.getSmtpHost());
		sender.setPort(email.getSmtpPort());
		sender.setUsername(email.getFromMail());
		sender.setPassword(email.getPassword());
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

		// 注入额外 JavaMail 属性
		if (email.getProperties() != null) {
			for (Map.Entry<String, String> entry : email.getProperties().entrySet()) {
				props.put(entry.getKey(), entry.getValue());
			}
		}

		log.info("[NotifyConfiguration] JavaMailSender bean registered, host={}, port={}, ssl={}, starttls={}",
				email.getSmtpHost(), email.getSmtpPort(), email.getSsl().isEnabled(), email.isStarttls());
		return sender;
	}

	/**
	 * 创建用于通知渠道 HTTP 调用的 {@link RestTemplate}。
	 *
	 * <p>连接超时 5 秒，读取超时 10 秒。
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

	/**
	 * 创建渠道限流管理器，用于控制各通知渠道的发送频率。
	 *
	 * <p>当 {@code ydsz.notify.rate-limit.enabled=true}（默认）时生效，
	 * 为每个渠道维护独立的滑动窗口限流器，防止单渠道过载。
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

	/**
	 * 创建统一消息通知服务，自动收集所有 {@link NotifyChannelStrategy} 实现并组装。
	 *
	 * <p>若容器中存在 {@link TemplateEngine}，则自动注入到各渠道策略中，启用模板渲染能力。
	 * <p>若容器中存在 {@link NotifyRateLimiterManager}，则注入以启用渠道限流保护。
	 *
	 * @param beanFactory          用于扫描所有 NotifyChannelStrategy 实现的 Bean 工厂
	 * @param templateEngine       可选的模板引擎，用于消息模板渲染（可为 null）
	 * @param rateLimiterManager   可选的限流管理器，用于渠道限流保护（可为 null）
	 * @param executor             可选的并行执行器，用于批量并行发送（可为 null）
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
		// 注入模板引擎到各渠道策略
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

	/**
	 * 创建通知重试队列，用于管理发送失败通知的重试调度。
	 *
	 * <p>当 {@code ydsz.notify.retryQueue.persistent=true} 时，使用 Redis 持久化队列
	 * （支持多实例共享、重启不丢失），Redis 不可用时降级为内存队列。
	 * 当 {@code persistent=false} 时，直接使用内存队列。
	 * 队列容量、批量大小等参数可通过 {@code ydsz.notify.retryQueue} 配置。
	 *
	 * @param properties         通知配置属性
	 * @param redisTemplateProvider Redis 模板提供者（可选）
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
			// 非持久化模式：使用内存队列（通过 PersistentNotifyRetryQueue 降级实现）
			queue = new PersistentNotifyRetryQueue(null,
					retryConfig.getMaxRetries(), retryConfig.getCapacity(), retryConfig.getBatchSize());
			log.info("[NotifyConfiguration] In-memory NotifyRetryQueue bean registered, persistent=false, maxRetries={}, batchSize={}",
					retryConfig.getMaxRetries(), retryConfig.getBatchSize());
		}
		retryQueueInstance = queue;
		return queue;
	}

	/**
	 * 创建异步通知服务，封装 {@link NotifyService} 并集成重试队列支持。
	 *
	 * <p>发送失败的通知将自动进入重试队列，由 {@link NotifyRetryQueue} 管理后续重试。
	 *
	 * @param notifyService 统一通知服务
	 * @param retryQueue    通知重试队列
	 * @param executor      共享虚拟线程池
	 * @return 具备异步发送和重试能力的 AsyncNotifyService 实例
	 */
	@Bean
	@ConditionalOnMissingBean(AsyncNotifyService.class)
	public AsyncNotifyService asyncNotifyService(NotifyService notifyService, NotifyRetryQueue retryQueue,
												 @Qualifier("notifyVirtualThreadExecutor") ExecutorService executor) {
		log.info("[NotifyConfiguration] AsyncNotifyService bean registered");
		return new AsyncNotifyService(notifyService, retryQueue, executor);
	}

	/**
	 * 创建共享的虚拟线程池，供通知模块各组件使用
	 *
	 * <p>使用 Java 21 虚拟线程特性，避免重复创建线程池造成资源浪费。
	 * 各 Sender、AsyncNotifyService、NotifyServiceImpl 等组件统一使用此线程池。
	 *
	 * @return 共享的虚拟线程池 ExecutorService
	 */
	@Bean(destroyMethod = "shutdown")
	@ConditionalOnMissingBean(name = "notifyVirtualThreadExecutor")
	public ExecutorService notifyVirtualThreadExecutor() {
		log.info("[NotifyConfiguration] 创建共享虚拟线程池 notifyVirtualThreadExecutor");
		return ExecutorUtils.newVirtualThreadExecutor("notify-virtual-");
	}

	/**
	 * 定时消费重试队列，每 5 秒执行一次。
	 * <p>使用批量消费模式提升吞吐量，默认每次最多处理 100 条消息。
	 * 采用指数退避策略，跳过未到重试时间的条目。
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
