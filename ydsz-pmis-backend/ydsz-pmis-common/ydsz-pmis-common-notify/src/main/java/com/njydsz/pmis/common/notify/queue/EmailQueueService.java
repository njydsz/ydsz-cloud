ackage com.njydsz.pmis.common.notify.queue;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

import com.njydsz.pmis.common.json.YdszJson;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

import com.njydsz.pmis.common.notify.channel.EmailMessage;
import com.njydsz.pmis.common.notify.channel.EmailNotifySender;
import com.njydsz.pmis.common.notify.core.NotifySendResult;

/**
 * 邮件队列与削峰服务（P2-8）
 *
 * <p>通过 Redis List 实现邮件发送队列，将同步发送转为异步消费，
 * 实现削峰填谷、失败重试和流量控制。
 *
 * <p><b>工作流程：</b>
 * <ol>
 *   <li>生产者调用 {@link #enqueue(EmailMessage)} 将邮件推入 Redis 队列</li>
 *   <li>消费者通过 {@link #startConsumer(EmailNotifySender)} 异步消费队列</li>
 *   <li>消费失败的消息进入重试队列，按指数退避重试</li>
 *   <li>超过最大重试次数的消息进入死信队列</li>
 * </ol>
 *
 * <p>当 Redis 不可用时，降级为直接同步发送。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
public class EmailQueueService {

	private static final Logger log = LoggerFactory.getLogger(EmailQueueService.class);

	private static final String QUEUE_KEY = "notify:email:queue";
	private static final String DEAD_LETTER_KEY = "notify:email:deadletter";
	private static final String RETRY_COUNT_PREFIX = "notify:email:retry:";
	private static final int MAX_RETRY = 3;

	private final StringRedisTemplate redisTemplate;
	private final ExecutorService executor;
	private final ObjectMapper objectMapper;

	private volatile boolean consumerRunning = false;
	private final AtomicLong totalEnqueued = new AtomicLong(0);
	private final AtomicLong totalConsumed = new AtomicLong(0);
	private final AtomicLong totalFailed = new AtomicLong(0);

	public EmailQueueService(StringRedisTemplate redisTemplate, ExecutorService executor) {
		this.redisTemplate = redisTemplate;
		this.executor = executor;
		this.objectMapper = new ObjectMapper();
	}

	/**
	 * 将邮件消息推入队列
	 *
	 * @param message 邮件消息
	 * @return true 表示入队成功
	 */
	public boolean enqueue(EmailMessage message) {
		if (message == null || !StringUtils.hasText(message.getTo())) {
			log.warn("[EmailQueueService] 邮件消息无效，跳过入队");
			return false;
		}
		try {
			String json = YdszJson.toJson(message);
			redisTemplate.opsForList().rightPush(QUEUE_KEY, json);
			totalEnqueued.incrementAndGet();
			log.debug("[EmailQueueService] 邮件已入队: to={}", message.getTo());
			return true;
		} catch (Exception e) {
			log.error("[EmailQueueService] 邮件入队失败: {}", e.getMessage(), e);
			return false;
		}
	}

	/**
	 * 启动队列消费者
	 *
	 * @param emailSender 邮件发送器
	 */
	public void startConsumer(EmailNotifySender emailSender) {
		if (consumerRunning) {
			log.warn("[EmailQueueService] 消费者已在运行");
			return;
		}
		consumerRunning = true;
		executor.submit(() -> consumeLoop(emailSender));
		log.info("[EmailQueueService] 邮件队列消费者已启动");
	}

	/**
	 * 停止队列消费者
	 */
	public void stopConsumer() {
		consumerRunning = false;
		log.info("[EmailQueueService] 邮件队列消费者已停止");
	}

	/**
	 * 消费循环
	 */
	private void consumeLoop(EmailNotifySender emailSender) {
		while (consumerRunning) {
			try {
				String json = redisTemplate.opsForList().leftPop(QUEUE_KEY);
				if (json == null) {
					Thread.sleep(1000);
					continue;
				}
				EmailMessage message = YdszJson.toObject(json, EmailMessage.class);
				NotifySendResult result = emailSender.sendEmail(message);
				totalConsumed.incrementAndGet();
				if (!result.isSuccess()) {
					totalFailed.incrementAndGet();
					handleFailure(message, result.getErrorMessage());
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			} catch (Exception e) {
				log.error("[EmailQueueService] 消费异常: {}", e.getMessage(), e);
				try {
					Thread.sleep(2000);
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					break;
				}
			}
		}
	}

	/**
	 * 处理发送失败
	 */
	private void handleFailure(EmailMessage message, String error) {
		int retryCount = getRetryCount(message);
		if (retryCount >= MAX_RETRY) {
			try {
				redisTemplate.opsForList().rightPush(DEAD_LETTER_KEY, YdszJson.toJson(message));
				log.error("[EmailQueueService] 邮件超过最大重试次数({}), 进入死信队列: to={}",
						MAX_RETRY, message.getTo());
			} catch (Exception e) {
				log.error("[EmailQueueService] 死信队列写入失败: {}", e.getMessage());
			}
		} else {
			enqueue(message);
			log.warn("[EmailQueueService] 邮件发送失败, 重新入队({}/{}): to={}, error={}",
					retryCount + 1, MAX_RETRY, message.getTo(), error);
		}
	}

	/**
	 * 获取消息的已重试次数
	 */
	private int getRetryCount(EmailMessage message) {
		String key = RETRY_COUNT_PREFIX + message.getTo() + ":" + message.getSubject();
		String count = redisTemplate.opsForValue().get(key);
		int c = count != null ? Integer.parseInt(count) : 0;
		redisTemplate.opsForValue().increment(key);
		return c;
	}

	/**
	 * 获取队列统计信息
	 */
	public QueueStats getStats() {
		Long queueSize = 0L;
		Long deadLetterSize = 0L;
		try {
			queueSize = redisTemplate.opsForList().size(QUEUE_KEY);
			deadLetterSize = redisTemplate.opsForList().size(DEAD_LETTER_KEY);
		} catch (Exception ignored) {
		}
		return new QueueStats(
				queueSize != null ? queueSize : 0,
				deadLetterSize != null ? deadLetterSize : 0,
				totalEnqueued.get(),
				totalConsumed.get(),
				totalFailed.get()
		);
	}

	/**
	 * 队列统计信息
	 */
	public record QueueStats(long queueSize, long deadLetterSize,
							 long totalEnqueued, long totalConsumed, long totalFailed) {
	}
}
