package com.njydsz.pmis.common.exception.metrics;

import com.njydsz.pmis.common.exception.custom.AbstractYdszException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * 异常指标统计器
 *
 * <p>集成 Micrometer，按异常类型、级别、类别等维度统计异常指标，
 * 便于监控大盘和告警规则配置。
 *
 * <p><b>指标命名规范：</b>
 * <ul>
 *   <li>{@code exception.count} - 异常计数（按类型/级别/类别等打 tag）</li>
 *   <li>{@code exception.handler.duration} - 异常处理耗时（Timer）</li>
 * </ul>
 *
 * <p><b>Tag 维度：</b>
 * <ul>
 *   <li>{@code type} - 异常简单类名（如 BusinessException）</li>
 *   <li>{@code level} - 异常级别（INFO/WARN/ERROR/FATAL）</li>
 *   <li>{@code category} - 异常类别（BUSINESS/SYSTEM/EXTERNAL 等）</li>
 *   <li>{@code code} - 异常编码</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 3.0.0
 */
public class ExceptionMetrics {

	private static final Logger log = LoggerFactory.getLogger(ExceptionMetrics.class);

	/**
	 * 异常计数指标名称
	 */
	public static final String METRIC_EXCEPTION_COUNT = "exception.count";

	/**
	 * 异常处理耗时指标名称
	 */
	public static final String METRIC_HANDLER_DURATION = "exception.handler.duration";

	/**
	 * Tag 名称 - 异常类型
	 */
	public static final String TAG_TYPE = "type";

	/**
	 * Tag 名称 - 异常级别
	 */
	public static final String TAG_LEVEL = "level";

	/**
	 * Tag 名称 - 异常类别
	 */
	public static final String TAG_CATEGORY = "category";

	/**
	 * Tag 名称 - 异常编码
	 */
	public static final String TAG_CODE = "code";

	private final MeterRegistry meterRegistry;

	private volatile boolean enabled = true;

	public ExceptionMetrics(MeterRegistry meterRegistry) {
		this.meterRegistry = meterRegistry;
	}

	/**
	 * 是否启用指标统计
	 *
	 * @return 启用返回 true
	 */
	public boolean isEnabled() {
		return enabled;
	}

	/**
	 * 设置是否启用指标统计
	 *
	 * @param enabled 是否启用
	 */
	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	/**
	 * 记录异常发生
	 *
	 * @param throwable 异常对象
	 */
	public void recordException(Throwable throwable) {
		if (!enabled || meterRegistry == null) {
			return;
		}
		try {
			String exceptionType = throwable.getClass().getSimpleName();
			String level = "UNKNOWN";
			String category = "UNKNOWN";
			String code = "N/A";

			if (throwable instanceof AbstractYdszException) {
				AbstractYdszException ex = (AbstractYdszException) throwable;
				if (ex.getLevel() != null) {
					level = ex.getLevel().name();
				}
				if (ex.getCategory() != null) {
					category = ex.getCategory().name();
				}
				if (ex.getCode() != null) {
					code = ex.getCode();
				}
			}

			Counter.builder(METRIC_EXCEPTION_COUNT)
					.tag(TAG_TYPE, exceptionType)
					.tag(TAG_LEVEL, level)
					.tag(TAG_CATEGORY, category)
					.tag(TAG_CODE, code)
					.register(meterRegistry)
					.increment();
		} catch (Exception e) {
			log.warn("记录异常指标失败: {}", e.getMessage());
		}
	}

	/**
	 * 记录异常处理耗时
	 *
	 * @param durationMs 耗时（毫秒）
	 * @param throwable  异常对象
	 */
	public void recordHandlerDuration(long durationMs, Throwable throwable) {
		if (!enabled || meterRegistry == null) {
			return;
		}
		try {
			String exceptionType = throwable.getClass().getSimpleName();
			Timer.builder(METRIC_HANDLER_DURATION)
					.tag(TAG_TYPE, exceptionType)
					.register(meterRegistry)
					.record(durationMs, TimeUnit.MILLISECONDS);
		} catch (Exception e) {
			log.warn("记录异常处理耗时指标失败: {}", e.getMessage());
		}
	}

	/**
	 * 记录异常发生（带额外标签）
	 *
	 * @param throwable  异常对象
	 * @param extraTags  额外标签（key, value 交替出现）
	 */
	public void recordExceptionWithTags(Throwable throwable, String... extraTags) {
		if (!enabled || meterRegistry == null) {
			return;
		}
		try {
			String exceptionType = throwable.getClass().getSimpleName();
			String level = "UNKNOWN";
			String category = "UNKNOWN";
			String code = "N/A";

			if (throwable instanceof AbstractYdszException) {
				AbstractYdszException ex = (AbstractYdszException) throwable;
				if (ex.getLevel() != null) {
					level = ex.getLevel().name();
				}
				if (ex.getCategory() != null) {
					category = ex.getCategory().name();
				}
				if (ex.getCode() != null) {
					code = ex.getCode();
				}
			}

			Counter.Builder builder = Counter.builder(METRIC_EXCEPTION_COUNT)
					.tag(TAG_TYPE, exceptionType)
					.tag(TAG_LEVEL, level)
					.tag(TAG_CATEGORY, category)
					.tag(TAG_CODE, code);

			// 添加额外标签
			for (int i = 0; i + 1 < extraTags.length; i += 2) {
				builder.tag(extraTags[i], extraTags[i + 1]);
			}

			builder.register(meterRegistry).increment();
		} catch (Exception e) {
			log.warn("记录异常指标失败: {}", e.getMessage());
		}
	}
}
