package com.njydsz.common.sentry.adapter;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.sentry.SentryService;
import com.njydsz.common.sentry.metrics.MicrometerMetricsCollector;
import com.njydsz.common.sentry.spi.MetricsCollector;

/**
 * Sentry 指标适配器（兼容 AbstractModuleMetrics 风格的迁移桥梁）。
 *
 * <p>为已从 {@code AbstractModuleMetrics} 迁移但仍依赖前缀拼接语义的模块提供过渡方案。 本类将传统 Counter/Timer/Gauge 调用桥接到
 * {@link MetricsCollector} 统一入口， 底层由 {@code MicrometerMetricsCollector} 执行实际的 Micrometer 注册。
 *
 * <p><b>26.10.01 变更</b>：移除 {@link MeterRegistry} 构造参数，改为内部通过 {@link SentryService} 获取 {@link
 * MetricsCollector}，业务模块不再直接依赖 Micrometer API。 符合《云顶编码规范》第 27.2.1 节「禁止直接操作 MeterRegistry」的强制要求。
 *
 * <p><b>26.10.01 变更</b>：新增 {@link #modulePrefix()} 抽象方法和 {@link MetricRegion} 枚举， 统一指标命名规范为 {@code
 * ydzs_{module}_{metric}_total} 格式，消除现有 MetricsConstants（30 个常量仅 4 处使用）的碎片化问题。 符合 P0 规则 YDIZ-SENTRY-001。
 *
 * <h3>迁移路径</h3>
 *
 * <ol>
 *   <li><b>当前阶段</b>：继承本类替换 {@code AbstractModuleMetrics}，获得统一入口能力
 *   <li><b>最终阶段</b>：直接调用 {@code SentryService} 的 count/time/gauge 方法
 * </ol>
 *
 * <h3>使用示例（26.10.01 新规范）</h3>
 *
 * <pre>{@code
 * @Component("flowMetrics")
 * public class FlowMetrics extends SentryMetricsAdapter {
 *     &#64;Override
 *     protected MetricRegion moduleRegion() { return MetricRegion.WORKFLOW; }
 *
 *     public void incInstanceCreated(String flowCode) {
 *         incrementCounter("instance_created_total", "flow_code", safe(flowCode));
 *     }
 * }
 * }</pre>
 *
 * <p>最终注册到 Prometheus 的指标名为：{@code ydzs_workflow_instance_created_total}
 *
 * @author ydsz-team
 * @since 26.10.01
 * @see com.njydsz.common.sentry.SentryService
 * @see MetricsCollector
 */
public abstract class SentryMetricsAdapter {

  private static final Logger LOG = LoggerFactory.getLogger(SentryMetricsAdapter.class);

  /** 模块指标前缀（如 "ydsz_flow_" / "ydsz_msg_"），通过 {@link #modulePrefix()} 动态生成 */
  protected final String prefix;

  /** Counter 实例缓存，避免重复构建 Builder */
  private final Map<String, Counter> counterCache = new ConcurrentHashMap<>();

  /** Timer 实例缓存，避免重复构建 Builder */
  private final Map<String, Timer> timerCache = new ConcurrentHashMap<>();

  /** Gauge 引用缓存，用于动态更新 Gauge 值 */
  private final Map<String, AtomicReference<Double>> gaugeRefCache = new ConcurrentHashMap<>();

  /** SentryService 提供者（由 SentryAutoConfiguration 注册） */
  private static volatile Supplier<SentryService> sentryServiceProvider;

  /**
   * 模块分区枚举。
   *
   * <p>定义业务模块的 Prometheus 指标命名空间，统一为 {@code ydzs_{region}_} 前缀。 消除现有 MetricsConstants（30 个常量仅 4
   * 处使用）的碎片化问题。
   *
   * @since 26.10.01
   */
  public enum MetricRegion {
    /** agent 智能引擎 */
    AGENT("ydsz_agent_"),
    /** cronjob 任务引擎 */
    CRONJOB("ydsz_cronjob_"),
    /** gateway 网关 */
    GATEWAY("ydsz_gateway_"),
    /** literule 规则引擎 */
    LITERULE("ydsz_literule_"),
    /** message 消息引擎 */
    MESSAGE("ydsz_msg_"),
    /** nextwiki 文件引擎 */
    NEXTWIKI("ydsz_nextwiki_"),
    /** system 系统引擎 */
    SYSTEM("ydsz_system_"),
    /** userinfo 身份引擎 */
    USERINFO("ydsz_userinfo_"),
    /** workflow 流程引擎 */
    WORKFLOW("ydsz_flow_"),
    /** generator 代码生成器 */
    GENERATOR("ydsz_generator_"),
    /** common 通用模块（兜底） */
    COMMON("ydsz_common_"),
    /** auth 认证模块 */
    AUTH("ydsz_auth_"),
    /** cache 缓存模块 */
    CACHE("ydsz_cache_"),
    /** redis 缓存模块 */
    REDIS("ydsz_redis_"),
    /** safe 安全模块 */
    SAFE("ydsz_safe_"),
    /** web 通用模块 */
    WEB("ydsz_web_"),
    /** excel 通用模块 */
    EXCEL("ydsz_excel_"),
    /** thread 线程池模块 */
    THREAD("ydsz_thread_"),
    /** feign 远程调用模块 */
    FEIGN("ydsz_feign_"),
    /** jdbc 数据库模块 */
    JDBC("ydsz_jdbc_"),
    /** lock 分布式锁模块 */
    LOCK("ydsz_lock_"),
    /** notify 通知模块 */
    NOTIFY("ydsz_notify_"),
    /** file 文件存储模块 */
    FILE("ydsz_file_"),
    /** tenant 多租户模块 */
    TENANT("ydsz_tenant_"),
    /** socket WebSocket 模块 */
    SOCKET("ydsz_socket_"),
    /** netty 网络模块 */
    NETTY("ydsz_netty_"),
    /** docs 文档模块 */
    DOCS("ydsz_docs_"),
    /** exception 异常模块 */
    EXCEPTION("ydsz_exception_"),
    /** queue 消息队列模块 */
    QUEUE("ydsz_queue_"),
    /** event 事件模块 */
    EVENT("ydsz_event_"),
    /** audit 审计模块 */
    AUDIT("ydsz_audit_"),
    /** search 搜索模块 */
    SEARCH("ydsz_search_"),
    /** config 配置模块 */
    CONFIG("ydsz_config_"),
    /** app 移动端模块 */
    APP("ydsz_app_"),
    /** sentry 监控模块自身 */
    SENTRY("ydsz_sentry_"),
    /** message-template 消息模板渲染 */
    MESSAGE_TEMPLATE("ydsz_message_template_"),
    ;

    private final String prefix;

    MetricRegion(String prefix) {
      this.prefix = prefix;
    }

    public String prefix() {
      return prefix;
    }
  }

  /**
   * 注册 SentryService 的 Supplier。
   *
   * <p>由 {@code SentryAutoConfiguration} 在容器初始化时调用，传入 ObjectProvider 风格的 Supplier。
   *
   * @param supplier SentryService 提供者，非空
   */
  public static void setSentryServiceProvider(Supplier<SentryService> supplier) {
    sentryServiceProvider = supplier;
  }

  /**
   * 生成带标签的缓存 key。
   *
   * @param name 指标名称
   * @param tags 标签键值对
   * @return 缓存 key 字符串
   */
  private static String cacheKey(String name, String... tags) {
    if (tags == null || tags.length == 0) {
      return name;
    }
    StringBuilder sb = new StringBuilder(name);
    for (int i = 0; i < tags.length; i++) {
      sb.append(':').append(tags[i]);
    }
    return sb.toString();
  }

  /**
   * 构造 Sentry 指标适配器。
   *
   * <p>通过 {@link #moduleRegion()} 获取模块前缀，自动拼接到所有指标名称前。 指标名格式为 {@code ydzs_{module}_{metric}_total}。
   */
  protected SentryMetricsAdapter() {
    this.prefix = moduleRegion().prefix;
  }

  /**
   * 获取模块分区，子类必须实现以确定指标前缀。
   *
   * <p>实现示例：
   *
   * <pre>{@code
   * &#64;Override
   * protected MetricRegion moduleRegion() { return MetricRegion.WORKFLOW; }
   * }</pre>
   *
   * @return 模块分区，不可为 null
   */
  protected abstract MetricRegion moduleRegion();

  /**
   * 便捷方法：通过 moduleRegion() 生成最终前缀（用于 getInstance 场景等）。
   *
   * @param region 模块分区
   * @return 前缀字符串
   */
  public static String getPrefixForRegion(MetricRegion region) {
    return region.prefix;
  }

  /**
   * 获取 MetricsCollector 实例。
   *
   * <p>通过 SentryService 获取当前注册的 MetricsCollector，支持 Micrometer 实现和内存降级实现。
   *
   * @return MetricsCollector 实例，可能为 null（Sentry 模块未装配时）
   */
  protected MetricsCollector getMetricsCollector() {
    SentryService service = getSentryService();
    if (service == null) {
      return null;
    }
    return service.getMetricsCollector();
  }

  /**
   * 获取 SentryService 实例。
   *
   * @return SentryService 实例，可能为 null
   */
  private static SentryService getSentryService() {
    Supplier<SentryService> supplier = sentryServiceProvider;
    if (supplier == null) {
      return null;
    }
    return supplier.get();
  }

  /**
   * 注册或获取 Counter 指标。
   *
   * @param name 指标名称（不含前缀，如 "instance_created_total"）
   * @param tags 标签键值对（如 "flow_code", "project_initiation"）
   * @return Counter 实例
   */
  protected Counter counter(String name, String... tags) {
    String key = cacheKey(prefix + name, tags);
    return counterCache.computeIfAbsent(
        key,
        k -> {
          MetricsCollector collector = getMetricsCollector();
          if (collector instanceof MicrometerMetricsCollector micrometer) {
            // 使用 Micrometer 注册
            return micrometer.getMeterRegistry() != null
                ? Counter.builder(prefix + name)
                    .tags(Tags.of(tags))
                    .register(micrometer.getMeterRegistry())
                : null;
          }
          return null;
        });
  }

  /**
   * 便捷方法：注册/获取 Counter 并立即递增 1。
   *
   * @param name 指标名称（不含前缀）
   * @param tags 标签键值对
   */
  public void incrementCounter(String name, String... tags) {
    MetricsCollector collector = getMetricsCollector();
    if (collector != null) {
      collector.incrementCounter(prefix + name, null, toMap(tags), 1.0);
    }
  }

  /**
   * 便捷方法：注册/获取 Counter 并立即递增指定值。
   *
   * @param name 指标名称（不含前缀）
   * @param amount 递增量
   * @param tags 标签键值对
   */
  public void incrementCounter(String name, double amount, String... tags) {
    MetricsCollector collector = getMetricsCollector();
    if (collector != null) {
      collector.incrementCounter(prefix + name, null, toMap(tags), amount);
    }
  }

  /**
   * 注册或获取 Timer 指标。
   *
   * @param name 指标名称（不含前缀，如 "instance_duration_ms"）
   * @param tags 标签键值对
   * @return Timer 实例
   */
  protected Timer timer(String name, String... tags) {
    String key = cacheKey(prefix + name, tags);
    return timerCache.computeIfAbsent(
        key,
        k -> {
          MetricsCollector collector = getMetricsCollector();
          if (collector instanceof MicrometerMetricsCollector micrometer) {
            return micrometer.getMeterRegistry() != null
                ? Timer.builder(prefix + name)
                    .tags(Tags.of(tags))
                    .register(micrometer.getMeterRegistry())
                : null;
          }
          return null;
        });
  }

  /**
   * 记录耗时到 Timer 指标。
   *
   * @param name 指标名称
   * @param durationMs 耗时（毫秒）
   * @param tags 标签键值对
   */
  protected void recordTimer(String name, long durationMs, String... tags) {
    MetricsCollector collector = getMetricsCollector();
    if (collector != null) {
      collector.recordTimer(prefix + name, null, toMap(tags), Duration.ofMillis(durationMs));
    }
  }

  /**
   * 记录耗时到 Timer 指标（Supplier 模式）。
   *
   * @param name 指标名称
   * @param supplier 业务逻辑供应器
   * @param tags 标签键值对
   * @param <T> 返回类型
   * @return supplier 的返回值
   */
  protected <T> T recordTimer(String name, Supplier<T> supplier, String... tags) {
    MetricsCollector collector = getMetricsCollector();
    if (collector != null) {
      long start = System.currentTimeMillis();
      try {
        return supplier.get();
      } finally {
        long durationMs = System.currentTimeMillis() - start;
        collector.recordTimer(prefix + name, null, toMap(tags), Duration.ofMillis(durationMs));
      }
    }
    return supplier.get();
  }

  /**
   * 注册 Gauge 指标（通过固定数值设置）。
   *
   * @param name 指标名称
   * @param value 当前值
   * @param tags 标签键值对
   */
  protected void gauge(String name, double value, String... tags) {
    MetricsCollector collector = getMetricsCollector();
    if (collector != null) {
      collector.setGauge(prefix + name, null, toMap(tags), value);
    }
  }

  /**
   * 注册 Gauge 指标（通过 Supplier 提供数值）。
   *
   * @param name 指标名称
   * @param supplier 数值供应器
   * @param tags 标签键值对
   */
  protected void gauge(String name, Supplier<Number> supplier, String... tags) {
    MetricsCollector collector = getMetricsCollector();
    if (collector != null) {
      // 使用 AtomicReference 包装 Supplier 值，支持动态更新
      String key = cacheKey(prefix + name, tags);
      AtomicReference<Double> ref =
          gaugeRefCache.computeIfAbsent(
              key,
              k -> {
                AtomicReference<Double> newRef = new AtomicReference<>(0.0);
                // 注册 Gauge 回调
                registerGaugeCallback(prefix + name, newRef, tags);
                return newRef;
              });
      Number val = supplier.get();
      ref.set(val != null ? val.doubleValue() : 0.0);
    }
  }

  /**
   * 注册 Gauge 指标（通过固定数值引用提供，适用于 AtomicLong/AtomicReference 场景）。
   *
   * @param name 指标名称
   * @param valueReference 数值引用对象
   * @param valueExtractor 从引用对象提取 double 值的函数
   * @param tags 标签键值对
   * @param <N> 数值引用类型
   */
  protected <N> void gaugeRef(
      String name, N valueReference, ToDoubleFunction<N> valueExtractor, String... tags) {
    String fullName = prefix + name;
    MetricsCollector collector = getMetricsCollector();
    if (collector instanceof MicrometerMetricsCollector micrometer
        && micrometer.getMeterRegistry() != null) {
      micrometer.getMeterRegistry().gauge(fullName, Tags.of(tags), valueReference, valueExtractor);
    }
  }

  /** 注册 Gauge 回调。 */
  private void registerGaugeCallback(String name, AtomicReference<Double> ref, String... tags) {
    MetricsCollector collector = getMetricsCollector();
    if (collector instanceof MicrometerMetricsCollector micrometer
        && micrometer.getMeterRegistry() != null) {
      micrometer.getMeterRegistry().gauge(name, Tags.of(tags), ref, AtomicReference::get);
    }
  }

  /**
   * Null 安全的字符串处理：将 null/空字符串替换为 "unknown"。
   *
   * @param value 原始值（可为 null）
   * @return 非 null 字符串
   */
  protected static String safe(String value) {
    return (value == null || value.isEmpty()) ? "unknown" : value;
  }

  /**
   * 将标签键值对数组转换为 Map。
   *
   * @param tags 标签键值对（k1, v1, k2, v2...）
   * @return 标签 Map
   */
  protected static Map<String, String> toMap(String... tags) {
    if (tags == null || tags.length == 0) {
      return Collections.emptyMap();
    }
    Map<String, String> map = new HashMap<>(16);
    for (int i = 0; i < tags.length - 1; i += 2) {
      map.put(tags[i], tags[i + 1]);
    }
    return map;
  }

  /**
   * 获取 Micrometer MeterRegistry（仅在 MicrometerMetricsCollector 可用时）。
   *
   * <p>仅供内部 Gauge 注册使用，不暴露给子类。
   *
   * @return MeterRegistry 或 null
   */
  private MeterRegistry getMicrometerRegistry() {
    MetricsCollector collector = getMetricsCollector();
    if (collector instanceof MicrometerMetricsCollector micrometer) {
      return micrometer.getMeterRegistry();
    }
    return null;
  }
}
