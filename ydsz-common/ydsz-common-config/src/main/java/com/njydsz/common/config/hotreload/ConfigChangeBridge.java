package com.njydsz.common.config.hotreload;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;

import com.njydsz.common.thread.util.ExecutorUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;

import com.njydsz.common.config.ConfigProperties;
import com.njydsz.common.config.hotreload.ConfigChangeEvent.ChangeType;

/**
 * 配置变更桥接器
 *
 * <p>监听 Spring Cloud 的 {@code RefreshEvent}（配置刷新前）和 {@code EnvironmentChangeEvent}（配置刷新后），自动 diff
 * 属性变更并：
 *
 * <ol>
 *   <li>发布 {@link ConfigChangeEvent} Spring 事件
 *   <li>记录审计日志（含节点 IP、变更数量）
 *   <li>通知所有 {@link ConfigChangeListener} 实现类
 * </ol>
 *
 * <h3>工作原理</h3>
 *
 * <ol>
 *   <li>构造函数中一次性采集当前 Environment 所有可枚举属性值作为<b>稳定视图</b>基线
 *   <li>收到 {@code EnvironmentChangeEvent} → 遍历事件携带的 changedKeys，与稳定视图对比计算 oldValue / newValue
 *   <li>组装 {@link ConfigChangeEvent.ConfigChange} 列表 → 发布事件 + 回调监听器
 *   <li>增量更新稳定视图（仅写入被变更的键，时间复杂度 O(changedKeys)）
 * </ol>
 *
 * <h3>性能优化</h3>
 *
 * <p>相比每次 RefreshEvent 全量扫描 PropertySource 树（O(allProperties)），增量快照将每次刷新的快照开销降为
 * O(changedKeys)，配置项数量 > 500 时优势显著（Nacos 场景常见）。
 *
 * <h3>条件激活</h3>
 *
 * <p>仅在 classpath 存在 Spring Cloud {@code EnvironmentChangeEvent} 时生效， 由 {@code @ConditionalOnClass}
 * 在 AutoConfiguration 层控制。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class ConfigChangeBridge implements ApplicationListener<ApplicationEvent> {

  private static final Logger LOG = LoggerFactory.getLogger(ConfigChangeBridge.class);

  /** Spring Cloud 事件类名（用于运行时匹配，避免编译期硬依赖） */
  private static final String REFRESH_EVENT_CLASS =
  // CHECKSTYLE.OFF: RegexpSinglelineJava — 字符串常量（注解/反射类名），非代码引用
      "org.springframework.cloud.context.refresh.RefreshEvent";
  // CHECKSTYLE.ON: RegexpSinglelineJava

  private static final String ENV_CHANGE_EVENT_CLASS =
  // CHECKSTYLE.OFF: RegexpSinglelineJava — 字符串常量（注解/反射类名），非代码引用
      "org.springframework.cloud.context.environment.EnvironmentChangeEvent";
  // CHECKSTYLE.ON: RegexpSinglelineJava

  private static final String UNKNOWN_HOST = "unknown";

  /** 异步执行器优雅关闭等待超时秒数 */
  private static final long AWAIT_TERMINATION_SECONDS = 5L;

  private final ConfigurableEnvironment environment;
  private final ApplicationEventPublisher publisher;
  private final ConfigProperties.ChangeMonitor changeMonitorProps;
  private final List<ConfigChangeListener> listeners;
  private final String nodeIp;
  private final ThreadPoolExecutor asyncExecutor;
  private final boolean isAsyncDispatch;
  private final ConfigAuditPublisher auditPublisher;

  /**
   * 稳定视图快照（启动时一次性全量采集，后续增量更新）。
   *
   * <p>每次 EnvironmentChangeEvent 处理完成后，根据变更记录增量更新此 Map，避免下次 RefreshEvent 重复全量遍历
   * PropertySource 树。
   */
  private final Map<String, String> stableSnapshot = new ConcurrentHashMap<>();

  /** 快照是否已初始化的标志 */
  private volatile boolean isSnapshotInitialized = false;

  /**
   * 处理配置变更事件（完整参数）。
   *
   * @param environment 环境配置
   * @param publisher 事件发布器
   * @param changeMonitorProps 变更监控配置
   * @param listeners 监听器列表（允许 null 或空列表）
   * @param auditPublisher 配置审计发布器（允许 null）
   */
  public ConfigChangeBridge(
      ConfigurableEnvironment environment,
      ApplicationEventPublisher publisher,
      ConfigProperties.ChangeMonitor changeMonitorProps,
      List<ConfigChangeListener> listeners,
      ConfigAuditPublisher auditPublisher) {
    this.environment = environment;
    this.publisher = publisher;
    this.changeMonitorProps = changeMonitorProps;
    // 强制包装为线程安全集合，避免外部传入不可变列表导致 addListener 抛异常
    this.listeners =
        listeners != null ? new CopyOnWriteArrayList<>(listeners) : new CopyOnWriteArrayList<>();
    this.nodeIp = resolveNodeIp();
    this.isAsyncDispatch = changeMonitorProps.isAsyncDispatch();
    this.asyncExecutor = this.isAsyncDispatch ? createAsyncExecutor(changeMonitorProps) : null;
    this.auditPublisher = auditPublisher;
    // 按 Order 排序监听器
    sortListenersByOrder();
    initializeStableSnapshot();
  }

  /**
   * 初始化稳定视图快照（启动时执行一次）。
   *
   * <p>全量遍历当前 Environment 中所有可枚举属性源的属性值，作为后续 diff 计算的基线。
   * 后续通过 {@link #incrementalUpdateSnapshot} 增量更新，避免重复全量扫描。
   */
  private void initializeStableSnapshot() {
    if (!changeMonitorProps.isSnapshotOldValues()) {
      isSnapshotInitialized = true;
      return;
    }
    stableSnapshot.clear();
    for (PropertySource<?> ps : environment.getPropertySources()) {
      if (ps instanceof EnumerablePropertySource<?> enumerable) {
        for (String key : enumerable.getPropertyNames()) {
          Object value = enumerable.getProperty(key);
          if (value instanceof String strValue) {
            stableSnapshot.put(key, strValue);
          } else if (value != null) {
            stableSnapshot.put(key, value.toString());
          }
        }
      }
    }
    isSnapshotInitialized = true;
    LOG.debug("[ConfigChangeBridge] 稳定视图快照已初始化，共 {} 个属性", stableSnapshot.size());
  }

  /**
   * 创建异步分发线程池
   *
   * <p>使用 CallerRunsPolicy 拒绝策略：队列满载时由调用线程（Spring Cloud 刷新线程）执行，
   * 避免任务丢失代价，但会带来刷新线程短暂阻塞（通常 < 10ms 的单次监听器回调）。
   *
   * <p>通过 {@link ExecutorUtils} 创建（YDIZ-CONC-001 合规），统一线程命名、异常处理、JVM 关闭阻塞防护。
   * 应用关闭时通过 {@link #shutdownAsyncExecutor()} 优雅关闭。
   *
   * @param props 变更监控配置
   * @return 线程池执行器
   */
  private static ThreadPoolExecutor createAsyncExecutor(ConfigProperties.ChangeMonitor props) {
    return ExecutorUtils.builder()
        .corePoolSize(props.getAsyncCorePoolSize())
        .maxPoolSize(props.getAsyncCorePoolSize())
        .keepAliveTime(60L, TimeUnit.SECONDS)
        .queueCapacity(props.getAsyncQueueCapacity())
        .threadNamePrefix("config-change-")
        .daemon(true)
        .rejectedHandler(new ThreadPoolExecutor.CallerRunsPolicy())
        .build();
  }

  /**
   * 解析当前节点的 IP 地址
   *
   * @return 节点 IP，若解析失败返回 "unknown"
   */
  private static String resolveNodeIp() {
    try {
      InetAddress localHost = InetAddress.getLocalHost();
      return localHost.getHostAddress();
    } catch (UnknownHostException e) {
      return UNKNOWN_HOST;
    }
  }

  /**
   * 按 {@link ConfigChangeListener#getOrder()} 升序排列监听器。
   *
   * <p>数值越小越先执行。同序号执行顺序不保证。
   */
  private void sortListenersByOrder() {
    listeners.sort(Comparator.comparingInt(ConfigChangeListener::getOrder));
    LOG.debug("[ConfigChangeBridge] 监听器已按 Order 排序，数量: {}", listeners.size());
  }

  /**
   * 动态添加监听器（添加后重新排序）。
   *
   * @param listener 要添加的监听器
   */
  public void addListener(ConfigChangeListener listener) {
    listeners.add(listener);
    sortListenersByOrder();
  }

  /**
   * 监听 Spring 配置刷新相关事件并路由分发。
   *
   * <p>按事件类名精确匹配（避免引入对 spring-cloud 的编译期强依赖）：
   *
   * <ul>
   *   <li>{@code RefreshEvent} → 采集刷新前的属性快照（供后续 diff 计算旧值）
   *   <li>{@code EnvironmentChangeEvent} → 计算属性变更集合并分发到所有监听器
   * </ul>
   *
   * <p>其余事件一律忽略；事件处理不抛异常，异常在内部捕获并记录 WARN。
   */
  @Override
  public void onApplicationEvent(ApplicationEvent event) {
    String eventClassName = event.getClass().getName();

    if (REFRESH_EVENT_CLASS.equals(eventClassName)) {
      handleRefreshEvent();
    } else if (ENV_CHANGE_EVENT_CLASS.equals(eventClassName)) {
      handleEnvironmentChangeEvent(event);
    }
  }

  /**
   * RefreshEvent 处理：稳定视图已在构造函数中初始化，无需每次刷新前重复全量扫描。
   *
   * <p>此方法保留为空操作的钩子，兼容未来可能的扩展（如采集刷新前的全量扫描对比）。
   */
  private void handleRefreshEvent() {
    // 启动时已通过 initializeStableSnapshot() 全量采集，此处不再重复扫描
  }

  /** EnvironmentChangeEvent 处理：diff 计算变更并分发 */
  private void handleEnvironmentChangeEvent(ApplicationEvent event) {
    Set<String> changedKeys = extractChangedKeys(event);
    if (changedKeys == null || changedKeys.isEmpty()) {
      return;
    }

    List<ConfigChangeEvent.ConfigChange> changes = new ArrayList<>(changedKeys.size());
    for (String key : changedKeys) {
      String oldValue = changeMonitorProps.isSnapshotOldValues() ? stableSnapshot.get(key) : null;
      String newValue = environment.getProperty(key);
      // 跳过未实际变更的属性（值完全相同）
      if (oldValue != null && oldValue.equals(newValue)) {
        continue;
      }
      ChangeType changeType = resolveChangeType(oldValue, newValue);
      changes.add(new ConfigChangeEvent.ConfigChange(key, oldValue, newValue, changeType));
    }

    if (changes.isEmpty()) {
      return;
    }

    LOG.info("[ConfigChangeBridge] 检测到 {} 个属性变更, node={}", changes.size(), nodeIp);
    if (LOG.isDebugEnabled()) {
      for (ConfigChangeEvent.ConfigChange c : changes) {
        LOG.debug("[ConfigChangeBridge] {} | {} -> {}", c.key(), c.oldValue(), c.newValue());
      }
    }

    // 1. 发布 Spring 事件
    ConfigChangeEvent changeEvent = new ConfigChangeEvent(this, changes, "default", "default");
    publisher.publishEvent(changeEvent);

    // 2. 通知监听器（异步 / 同步由 changeMonitor.asyncDispatch 控制）
    dispatchToListeners(changes);

    // 3. 审计发布
    publishAudit(changeEvent, changes.size());

    // 4. 增量更新稳定视图（仅更新已变更的键）
    incrementalUpdateSnapshot(changes);
  }

  /**
   * 增量更新稳定视图快照。
   *
   * <p>根据变更记录更新 stableSnapshot：
   *
   * <ul>
   *   <li>CHANGED / ADDED：将新值写入 stableSnapshot
   *   <li>DELETED：从 stableSnapshot 中移除
   * </ul>
   *
   * <p>时间复杂度 O(changedKeys)，远低于全量扫描的 O(allProperties)。
   *
   * @param changes 已处理的变更列表
   */
  private void incrementalUpdateSnapshot(List<ConfigChangeEvent.ConfigChange> changes) {
    if (!changeMonitorProps.isSnapshotOldValues() || changes.isEmpty()) {
      return;
    }
    for (ConfigChangeEvent.ConfigChange c : changes) {
      if (c.newValue() != null) {
        stableSnapshot.put(c.key(), c.newValue());
      } else {
        stableSnapshot.remove(c.key());
      }
    }
    LOG.debug("[ConfigChangeBridge] 稳定视图增量更新完成，变更 {} 个键", changes.size());
  }

  /**
   * 分发配置变更到所有监听器
   *
   * <p>根据 {@link ConfigProperties.ChangeMonitor#isAsyncDispatch()} 决定同步或异步回调：
   *
   * <ul>
   *   <li>异步模式：通过线程池派发，不阻塞 Spring Cloud 刷新线程
   *   <li>同步模式：直接在事件处理线程中回调
   * </ul>
   *
   * <p>单次监听器回调异常不影响其他监听器执行。
   *
   * @param changes 变更列表
   */
  private void dispatchToListeners(List<ConfigChangeEvent.ConfigChange> changes) {
    if (isAsyncDispatch) {
      for (ConfigChangeListener listener : listeners) {
        for (ConfigChangeEvent.ConfigChange c : changes) {
          asyncExecutor.submit(() -> invokeListener(listener, c));
        }
      }
    } else {
      for (ConfigChangeListener listener : listeners) {
        for (ConfigChangeEvent.ConfigChange c : changes) {
          invokeListener(listener, c);
        }
      }
    }
  }

  /**
   * 执行单次监听器回调（含异常隔离）
   *
   * @param listener 监听器实例
   * @param change 变更记录
   */
  static void invokeListener(ConfigChangeListener listener, ConfigChangeEvent.ConfigChange change) {
    try {
      listener.onChange(change.key(), change.oldValue(), change.newValue());
    } catch (Exception e) {
      LOG.warn(
          "[ConfigChangeBridge] 监听器 {} 回调异常: {}",
          listener.getClass().getSimpleName(),
          e.getMessage(),
          e);
    }
  }

  // ==================== 生命周期管理 ====================

  /**
   * 优雅关闭异步分发线程池。
   *
   * <p>由 Spring 容器在 Bean 销毁时调用（{@link PreDestroy}）。停机流程：
   *
   * <ol>
   *   <li>调用 {@code shutdown()} 拒绝新任务</li>
   *   <li>等待最多 {@code awaitTerminationSeconds} 秒让进行中任务完成</li>
   *   <li>超时后强制 {@code shutdownNow()}</li>
   * </ol>
   */
  @PreDestroy
  public void shutdownAsyncExecutor() {
    if (asyncExecutor == null) {
      return;
    }
    asyncExecutor.shutdown();
    try {
      if (!asyncExecutor.awaitTermination(AWAIT_TERMINATION_SECONDS, TimeUnit.SECONDS)) {
        LOG.warn(
            "[ConfigChangeBridge] 异步执行器 {}s 内未完全终止，强制执行 shutdownNow",
            AWAIT_TERMINATION_SECONDS);
        asyncExecutor.shutdownNow();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      asyncExecutor.shutdownNow();
    }
    LOG.info("[ConfigChangeBridge] 异步执行器已关闭");
  }

  // ==================== 包可见测试方法 ====================

  /**
   * 当前注册的监听器数量（仅供单元测试使用）。
   *
   * @return 监听器数量
   */
  int getListenerCount() {
    return listeners.size();
  }

  /**
   * 是否处于异步分发模式（仅供单元测试使用）。
   *
   * @return true 表示异步分发
   */
  boolean isAsyncMode() {
    return isAsyncDispatch;
  }

  /**
   * 从 EnvironmentChangeEvent 中提取变更的属性键集合
   *
   * <p>通过反射调用 {@code getKeys()} 方法。由于 {@link #onApplicationEvent} 已通过类名 确认事件类型，且 Bean 创建时
   * {@code @ConditionalOnClass} 已保证 Spring Cloud 在 classpath 中， 因此反射调用是安全的。
   */
  private Set<String> extractChangedKeys(ApplicationEvent event) {
    try {
      // 已知 event 是 EnvironmentChangeEvent，直接调用 getKeys()
      Method getKeys = event.getClass().getMethod("getKeys");
      // Method.invoke 返回 Object，编译期无法验证返回类型为 Set<String>；由 onApplicationEvent 类名检查 + @ConditionalOnClass 保证安全性
      Set<String> keys = (Set<String>) getKeys.invoke(event);
      return keys != null ? keys : Set.of();
    } catch (Exception e) {
      LOG.warn("[ConfigChangeBridge] 提取变更键失败: {}", e.getMessage());
      return Set.of();
    }
  }

  /**
   * 发布配置变更审计记录。
   *
   * <p>仅在审计启用且 auditPublisher 非空时执行。审计失败不影响主流程。
   *
   * @param event 配置变更事件
   * @param changeCount 变更数量
   */
  private void publishAudit(ConfigChangeEvent event, int changeCount) {
    if (!changeMonitorProps.isAuditEnabled() || auditPublisher == null) {
      return;
    }
    try {
      auditPublisher.publish(event, nodeIp, changeCount);
    } catch (Exception e) {
      LOG.warn("[ConfigChangeBridge] 审计发布失败(不影响主流程): {}", e.getMessage());
    }
  }

  /**
   * 根据刷新前后的值推断变更类型
   *
   * <p>推断规则：
   *
   * <ul>
   *   <li>oldValue == null 且 newValue != null → {@link ChangeType#ADDED}
   *   <li>oldValue != null 且 newValue == null → {@link ChangeType#DELETED}
   *   <li>其余（值不同或均未变更但被事件携带）→ {@link ChangeType#CHANGED}
   * </ul>
   *
   * @param oldValue 刷新前的值
   * @param newValue 刷新后的值
   * @return 变更类型
   */
  private static ChangeType resolveChangeType(String oldValue, String newValue) {
    if (oldValue == null && newValue != null) {
      return ChangeType.ADDED;
    }
    if (oldValue != null && newValue == null) {
      return ChangeType.DELETED;
    }
    return ChangeType.CHANGED;
  }

}
