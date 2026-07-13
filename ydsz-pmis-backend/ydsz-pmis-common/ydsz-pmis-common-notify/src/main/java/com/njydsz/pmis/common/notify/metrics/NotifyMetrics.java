package com.njydsz.pmis.common.notify.metrics;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * 通知模块指标埋点（P1-4：Micrometer 指标监控）
 *
 * <p>为邮件及其他通知渠道提供统一的指标埋点能力，暴露以下指标：
 * <ul>
 *   <li>{@code notify_email_sent_total{channel,result}} — 发送总数（含成功/失败标签）</li>
 *   <li>{@code notify_email_duration_seconds{channel}} — 发送耗时分布</li>
 *   <li>{@code notify_email_failed_total{channel,reason}} — 失败总数（含失败原因标签）</li>
 *   <li>{@code notify_channel_sent_total{channel,result}} — 各渠道发送总数</li>
 * </ul>
 *
 * <p>当 micrometer-core 依赖不存在时，自动降级为 no-op（不影响功能）。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
public class NotifyMetrics {

	private static final Logger log = LoggerFactory.getLogger(NotifyMetrics.class);

	private static final String METRIC_EMAIL_SENT = "notify_email_sent_total";
	private static final String METRIC_EMAIL_DURATION = "notify_email_duration_seconds";
	private static final String METRIC_EMAIL_FAILED = "notify_email_failed_total";
	private static final String METRIC_CHANNEL_SENT = "notify_channel_sent_total";

	private static final boolean MICROMETER_AVAILABLE;

	static {
		boolean available;
		try {
			Class.forName("io.micrometer.core.instrument.MeterRegistry");
			available = true;
		} catch (ClassNotFoundException e) {
			available = false;
		}
		MICROMETER_AVAILABLE = available;
	}

	private final MeterRegistry meterRegistry;
	private final ConcurrentMap<String, Counter> counterCache = new ConcurrentHashMap<>();
	private final ConcurrentMap<String, Timer> timerCache = new ConcurrentHashMap<>();

	/**
	 * 构造通知指标收集器
	 *
	 * @param meterRegistry Micrometer MeterRegistry（可为 null，降级为 SimpleMeterRegistry）
	 */
	public NotifyMetrics(MeterRegistry meterRegistry) {
		if (!MICROMETER_AVAILABLE) {
			this.meterRegistry = null;
			log.info("[NotifyMetrics] micrometer-core 依赖不存在，指标收集降级为 no-op");
		} else {
			this.meterRegistry = meterRegistry != null ? meterRegistry : new SimpleMeterRegistry();
			log.info("[NotifyMetrics] NotifyMetrics 初始化完成, registry={}",
					this.meterRegistry.getClass().getSimpleName());
		}
	}

	/**
	 * 记录邮件发送结果
	 *
	 * @param channel  渠道名称
	 * @param success  是否成功
	 * @param duration 发送耗时
	 */
	public void recordEmailSend(String channel, boolean success, Duration duration) {
		if (meterRegistry == null) {
			return;
		}
		String result = success ? "success" : "failure";
		Counter counter = counterCache.computeIfAbsent(
				METRIC_EMAIL_SENT + "_" + channel + "_" + result,
				k -> Counter.builder(METRIC_EMAIL_SENT)
						.tag("channel", channel)
						.tag("result", result)
						.register(meterRegistry));
		counter.increment();

		Timer timer = timerCache.computeIfAbsent(
				METRIC_EMAIL_DURATION + "_" + channel,
				k -> Timer.builder(METRIC_EMAIL_DURATION)
						.tag("channel", channel)
						.register(meterRegistry));
		timer.record(duration);
	}

	/**
	 * 记录邮件发送失败
	 *
	 * @param channel 渠道名称
	 * @param reason  失败原因
	 */
	public void recordEmailFailure(String channel, String reason) {
		if (meterRegistry == null) {
			return;
		}
		Counter counter = counterCache.computeIfAbsent(
				METRIC_EMAIL_FAILED + "_" + channel + "_" + reason,
				k -> Counter.builder(METRIC_EMAIL_FAILED)
						.tag("channel", channel)
						.tag("reason", reason != null ? reason : "unknown")
						.register(meterRegistry));
		counter.increment();
	}

	/**
	 * 记录各渠道发送结果
	 *
	 * @param channel 渠道名称
	 * @param success 是否成功
	 */
	public void recordChannelSend(String channel, boolean success) {
		if (meterRegistry == null) {
			return;
		}
		String result = success ? "success" : "failure";
		Counter counter = counterCache.computeIfAbsent(
				METRIC_CHANNEL_SENT + "_" + channel + "_" + result,
				k -> Counter.builder(METRIC_CHANNEL_SENT)
						.tag("channel", channel)
						.tag("result", result)
						.register(meterRegistry));
		counter.increment();
	}

	/**
	 * 判断 Micrometer 是否可用
	 *
	 * @return true 表示指标收集功能正常
	 */
	public static boolean isMicrometerAvailable() {
		return MICROMETER_AVAILABLE;
	}
}
