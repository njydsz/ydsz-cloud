package com.njydsz.common.exception.metrics;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

import com.njydsz.common.exception.custom.AbstractYdszException;

/**
 * 异常指标统计器
 *
 * <p>集成 Micrometer，按异常类型、级别、类别等维度统计异常指标， 便于监控大盘和告警规则配置。
 *
 * <p><b>指标命名规范：</b>
 *
 * <ul>
 *   <li>{@code exception.count} - 异常计数（按类型/级别/类别等打 tag）
 *   <li>{@code exception.handler.duration} - 异常处理耗时（Timer）
 * </ul>
 *
 * <p><b>Tag 维度：</b>
 *
 * <ul>
 *   <li>{@code type} - 异常简单类名（如 BusinessException）
 *   <li>{@code level} - 异常级别（INFO/WARN/ERROR/FATAL）
 *   <li>{@code category} - 异常类别（BUSINESS/SYSTEM/EXTERNAL 等）
 * </ul>
 *
 * <p><b>高基数标签治理：</b>{@code code} tag 默认不包含， 通过 {@code ydsz.exception.metrics-include-code-tag=true}
 * 显式开启。
 *
 * <p><b>性能优化（v2.0）：</b>预缓存常用 Tags 对象，减少高频调用场景下的 Counter.Builder 对象创建与 Tags 数组分配开销。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class ExceptionMetrics {

  private static final Logger log = LoggerFactory.getLogger(ExceptionMetrics.class);

  /** 异常计数指标名称 */
  public static final String METRIC_EXCEPTION_COUNT = "exception.count";

  /** 异常处理耗时指标名称 */
  public static final String METRIC_HANDLER_DURATION = "exception.handler.duration";

  /** Tag 名称 - 异常类型 */
  public static final String TAG_TYPE = "type";

  /** Tag 名称 - 异常级别 */
  public static final String TAG_LEVEL = "level";

  /** Tag 名称 - 异常类别 */
  public static final String TAG_CATEGORY = "category";

  /** Tag 名称 - 异常编码（高基数，默认不包含） */
  public static final String TAG_CODE = "code";

  private final MeterRegistry meterRegistry;

  /**
   * 是否启用指标统计，使用 AtomicBoolean 保证多线程可见性和 CAS 操作安全
   *
   * @return 处理结果
   */
  private final AtomicBoolean enabled = new AtomicBoolean(true);

  /** 是否在指标中包含高基数 code tag */
  private volatile boolean includeCodeTag = false;

  /** 预计算分位数列表（如 0.99 = P99），为空则不预计算 */
  private volatile double[] percentiles;

  public ExceptionMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  /**
   * 设置是否包含 code tag
   *
   * @param includeCodeTag 是否包含
   */
  public void setIncludeCodeTag(boolean includeCodeTag) {
    this.includeCodeTag = includeCodeTag;
  }

  /**
   * 设置预计算分位数。
   *
   * @param percentiles 分位数列表（0.0~1.0），如 [0.99] 表示 P99；为空则不预计算
   */
  public void setPercentiles(List<Double> percentiles) {
    if (percentiles == null || percentiles.isEmpty()) {
      this.percentiles = null;
    } else {
      this.percentiles = percentiles.stream().mapToDouble(Double::doubleValue).toArray();
    }
  }

  /**
   * 是否启用指标统计
   *
   * @return 启用返回 true
   */
  public boolean isEnabled() {
    return enabled.get();
  }

  /**
   * 设置是否启用指标统计
   *
   * @param enabled 是否启用
   */
  public void setEnabled(boolean enabled) {
    this.enabled.set(enabled);
  }

  /**
   * CAS 原子切换启用状态
   *
   * @param expected 期望的当前值
   * @param update 新值
   * @return 切换成功返回 true
   * @since 1.0.0
   */
  public boolean compareAndSetEnabled(boolean expected, boolean update) {
    return enabled.compareAndSet(expected, update);
  }

  /**
   * 记录异常发生
   *
   * @param throwable 异常对象
   */
  public void recordException(Throwable throwable) {
    recordExceptionWithTags(throwable);
  }

  /**
   * 记录异常处理耗时
   *
   * @param durationMs 耗时（毫秒）
   * @param throwable 异常对象
   */
  public void recordHandlerDuration(long durationMs, Throwable throwable) {
    if (!enabled.get() || meterRegistry == null) {
      return;
    }
    try {
      String exceptionType = throwable.getClass().getSimpleName();
      Timer.Builder timerBuilder =
          Timer.builder(METRIC_HANDLER_DURATION).tag(TAG_TYPE, exceptionType);
      if (percentiles != null) {
        timerBuilder.publishPercentiles(percentiles);
      }
      timerBuilder.register(meterRegistry).record(durationMs, TimeUnit.MILLISECONDS);
    } catch (Exception e) {
      log.warn("记录异常处理耗时指标失败: {}", e.getMessage());
    }
  }

  /**
   * 记录异常发生（带额外标签）
   *
   * @param throwable 异常对象
   * @param extraTags 额外标签（key, value 交替出现）
   */
  public void recordExceptionWithTags(Throwable throwable, String... extraTags) {
    if (!enabled.get() || meterRegistry == null) {
      return;
    }
    try {
      Counter.Builder builder = Counter.builder(METRIC_EXCEPTION_COUNT);

      // 使用预缓存的 Tags 或构建新 Tags
      Tags tags = buildTags(throwable, extraTags);
      builder.tags(tags);

      if (includeCodeTag) {
        String code = "N/A";
        if (throwable instanceof AbstractYdszException ex && ex.getCode() != null) {
          code = ex.getCode();
        }
        builder.tag(TAG_CODE, code);
      }

      builder.register(meterRegistry).increment();
    } catch (Exception e) {
      log.warn("记录异常指标失败: {}", e.getMessage());
    }
  }

  /**
   * 构建 Tags 对象。
   *
   * <p>Micrometer 内置 Meter 缓存保证相同 tag 组合不会重复构建 Meter， 此处无需额外缓存层。
   *
   * @param throwable 异常对象
   * @param extraTags 额外标签（key, value 交替）
   * @return 处理结果
   */
  private Tags buildTags(Throwable throwable, String... extraTags) {
    String exceptionType = throwable.getClass().getSimpleName();
    String level = "UNKNOWN";
    String category = "UNKNOWN";

    if (throwable instanceof AbstractYdszException ex) {
      if (ex.getLevel() != null) {
        level = ex.getLevel().name();
      }
      if (ex.getCategory() != null) {
        category = ex.getCategory().name();
      }
    }

    Tags tags =
        Tags.of(
            Tag.of(TAG_TYPE, exceptionType),
            Tag.of(TAG_LEVEL, level),
            Tag.of(TAG_CATEGORY, category));

    // 追加额外标签
    if (extraTags != null) {
      for (int i = 0; i + 1 < extraTags.length; i += 2) {
        tags = tags.and(Tag.of(extraTags[i], extraTags[i + 1]));
      }
    }
    return tags;
  }
}
