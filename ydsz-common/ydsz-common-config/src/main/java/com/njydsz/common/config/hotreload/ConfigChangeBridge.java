package com.njydsz.common.config.hotreload;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;

import com.njydsz.common.config.ConfigProperties;

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
 *   <li>收到 {@code RefreshEvent} → 快照当前 Environment 所有可枚举属性值
 *   <li>收到 {@code EnvironmentChangeEvent} → 遍历事件携带的 changedKeys， 与快照对比计算 oldValue / newValue
 *   <li>组装 {@link ConfigChangeEvent.ConfigChange} 列表 → 发布事件 + 回调监听器
 * </ol>
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

  private final ConfigurableEnvironment environment;
  private final ApplicationEventPublisher publisher;
  private final ConfigProperties.ChangeMonitor changeMonitorProps;
  private final List<ConfigChangeListener> listeners;
  private final String nodeIp;

  /** 刷新前的属性值快照（仅在 snapshotOldValues=true 时维护） */
  private final Map<String, String> snapshot = new ConcurrentHashMap<>();

  /**
   * 处理配置变更事件。
   *
   * @param environment 环境配置
   * @param publisher 事件发布器
   * @param changeMonitorProps 变更监控配置
   * @param listeners 监听器列表（允许 null 或空列表）
   */
  public ConfigChangeBridge(
      ConfigurableEnvironment environment,
      ApplicationEventPublisher publisher,
      ConfigProperties.ChangeMonitor changeMonitorProps,
      List<ConfigChangeListener> listeners) {
    this.environment = environment;
    this.publisher = publisher;
    this.changeMonitorProps = changeMonitorProps;
    // 强制包装为线程安全集合，避免外部传入不可变列表导致 addListener 抛异常
    this.listeners =
        listeners != null ? new CopyOnWriteArrayList<>(listeners) : new CopyOnWriteArrayList<>();
    this.nodeIp = resolveNodeIp();
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
   * 动态添加监听器
   *
   * @param listener 要添加的监听器
   */
  public void addListener(ConfigChangeListener listener) {
    listeners.add(listener);
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

  /** RefreshEvent 处理：快照当前属性值 */
  private void handleRefreshEvent() {
    if (!changeMonitorProps.isSnapshotOldValues()) {
      return;
    }
    snapshot.clear();
    takeSnapshot();
    LOG.debug("[ConfigChangeBridge] 快照已采集，共 {} 个属性", snapshot.size());
  }

  /** EnvironmentChangeEvent 处理：diff 计算变更并分发 */
  private void handleEnvironmentChangeEvent(ApplicationEvent event) {
    Set<String> changedKeys = extractChangedKeys(event);
    if (changedKeys == null || changedKeys.isEmpty()) {
      return;
    }

    List<ConfigChangeEvent.ConfigChange> changes = new ArrayList<>(changedKeys.size());
    for (String key : changedKeys) {
      String oldValue = changeMonitorProps.isSnapshotOldValues() ? snapshot.get(key) : null;
      String newValue = environment.getProperty(key);
      // 跳过未实际变更的属性（值完全相同）
      if (oldValue != null && oldValue.equals(newValue)) {
        continue;
      }
      changes.add(new ConfigChangeEvent.ConfigChange(key, oldValue, newValue));
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
    ConfigChangeEvent changeEvent = new ConfigChangeEvent(this, changes);
    publisher.publishEvent(changeEvent);

    // 2. 通知监听器
    for (ConfigChangeListener listener : listeners) {
      for (ConfigChangeEvent.ConfigChange c : changes) {
        try {
          listener.onChange(c.key(), c.oldValue(), c.newValue());
        } catch (Exception e) {
          LOG.warn(
              "[ConfigChangeBridge] 监听器 {} 回调异常: {}",
              listener.getClass().getSimpleName(),
              e.getMessage(),
              e);
        }
      }
    }

    // 清理快照
    snapshot.clear();
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
      @SuppressWarnings("unchecked") // 反射调用 Method.invoke 返回 Object，无法在编译期验证泛型类型
      Set<String> keys = (Set<String>) getKeys.invoke(event);
      return keys != null ? keys : Set.of();
    } catch (Exception e) {
      LOG.warn("[ConfigChangeBridge] 提取变更键失败: {}", e.getMessage());
      return Set.of();
    }
  }

  /**
   * 快照当前 Environment 中所有可枚举属性源的属性值
   *
   * <p>仅当需要计算 oldValues 时才调用此方法。遍历整个 PropertySource 树 开销较大，因此默认通过 {@code snapshot-old-values}
   * 配置控制是否启用。
   */
  private void takeSnapshot() {
    for (PropertySource<?> ps : environment.getPropertySources()) {
      if (ps instanceof EnumerablePropertySource<?> enumerable) {
        for (String key : enumerable.getPropertyNames()) {
          Object value = enumerable.getProperty(key);
          if (value instanceof String strValue) {
            snapshot.put(key, strValue);
          } else if (value != null) {
            snapshot.put(key, value.toString());
          }
        }
      }
    }
  }
}
