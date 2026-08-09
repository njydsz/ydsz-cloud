package com.njydsz.common.config.hotreload;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
 * <p>监听 Spring Cloud 的 {@code RefreshEvent}（配置刷新前）和
 * {@code EnvironmentChangeEvent}（配置刷新后），自动 diff 属性变更并：
 * <ol>
 *   <li>发布 {@link ConfigChangeEvent} Spring 事件</li>
 *   <li>通知所有 {@link ConfigChangeListener} 实现类</li>
 * </ol>
 *
 * <h3>工作原理</h3>
 * <ol>
 *   <li>收到 {@code RefreshEvent} → 快照当前 Environment 所有可枚举属性值</li>
 *   <li>收到 {@code EnvironmentChangeEvent} → 遍历事件携带的 changedKeys，
 *       与快照对比计算 oldValue / newValue</li>
 *   <li>组装 {@link ConfigChangeEvent.ConfigChange} 列表 → 发布事件 + 回调监听器</li>
 * </ol>
 *
 * <h3>条件激活</h3>
 * <p>仅在 classpath 存在 Spring Cloud {@code EnvironmentChangeEvent} 时生效，
 * 由 {@code @ConditionalOnClass} 在 AutoConfiguration 层控制。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class ConfigChangeBridge
        implements ApplicationListener<ApplicationEvent> {

    private static final Logger log = LoggerFactory.getLogger(ConfigChangeBridge.class);

    /** Spring Cloud 事件类名（用于运行时匹配，避免编译期硬依赖） */
    private static final String REFRESH_EVENT_CLASS =
            "org.springframework.cloud.context.refresh.RefreshEvent";
    private static final String ENV_CHANGE_EVENT_CLASS =
            "org.springframework.cloud.context.environment.EnvironmentChangeEvent";

    private final ConfigurableEnvironment environment;
    private final ApplicationEventPublisher publisher;
    private final ConfigProperties.ChangeMonitor changeMonitorProps;
    private final List<ConfigChangeListener> listeners;

    /** 刷新前的属性值快照（仅在 snapshotOldValues=true 时维护） */
    private final Map<String, String> snapshot = new ConcurrentHashMap<>();

    public ConfigChangeBridge(ConfigurableEnvironment environment,
                              ApplicationEventPublisher publisher,
                              ConfigProperties.ChangeMonitor changeMonitorProps,
                              List<ConfigChangeListener> listeners) {
        this.environment = environment;
        this.publisher = publisher;
        this.changeMonitorProps = changeMonitorProps;
        this.listeners = listeners != null ? listeners : List.of();
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
     * <ul>
     *   <li>{@code RefreshEvent} → 采集刷新前的属性快照（供后续 diff 计算旧值）</li>
     *   <li>{@code EnvironmentChangeEvent} → 计算属性变更集合并分发到所有监听器</li>
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
     * RefreshEvent 处理：快照当前属性值
     */
    private void handleRefreshEvent() {
        if (!changeMonitorProps.isSnapshotOldValues()) {
            return;
        }
        snapshot.clear();
        takeSnapshot();
        log.debug("[ConfigChangeBridge] 快照已采集，共 {} 个属性", snapshot.size());
    }

    /**
     * EnvironmentChangeEvent 处理：diff 计算变更并分发
     */
    private void handleEnvironmentChangeEvent(ApplicationEvent event) {
        Set<String> changedKeys = extractChangedKeys(event);
        if (changedKeys == null || changedKeys.isEmpty()) {
            return;
        }

        List<ConfigChangeEvent.ConfigChange> changes = new ArrayList<>(changedKeys.size());
        for (String key : changedKeys) {
            String oldValue = changeMonitorProps.isSnapshotOldValues()
                    ? snapshot.get(key)
                    : null;
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

        log.info("[ConfigChangeBridge] 检测到 {} 个属性变更", changes.size());
        if (log.isDebugEnabled()) {
            for (ConfigChangeEvent.ConfigChange c : changes) {
                log.debug("[ConfigChangeBridge] {} | {} -> {}", c.key(), c.oldValue(), c.newValue());
            }
        }

        // 1. 发布 Spring 事件
        ConfigChangeEvent changeEvent = new ConfigChangeEvent(this, changes);
        publisher.publishEvent(changeEvent);

        // 2. 通知监听器
        for (ConfigChangeListener listener : listeners) {
            try {
                listener.onBatchChange(changes);
            } catch (Exception e) {
                log.warn("[ConfigChangeBridge] 监听器 {} 回调异常: {}",
                        listener.getClass().getSimpleName(), e.getMessage(), e);
            }
        }

        // 清理快照
        snapshot.clear();
    }

    /**
     * 从 EnvironmentChangeEvent 中提取变更的属性键集合
     *
     * <p>使用反射调用 {@code getKeys()} 方法，避免编译期硬依赖 Spring Cloud。
     */
    private Set<String> extractChangedKeys(ApplicationEvent event) {
        try {
            var method = event.getClass().getMethod("getKeys");
            Object keys = method.invoke(event);
            if (keys instanceof Set<?> set) {
                return castToStringSet(set);
            }
        } catch (NoSuchMethodException e) {
            log.debug("[ConfigChangeBridge] 事件 {} 无 getKeys() 方法", event.getClass().getSimpleName());
        } catch (Exception e) {
            log.warn("[ConfigChangeBridge] 提取变更键失败: {}", e.getMessage());
        }
        return Set.of();
    }

    /**
     * 安全类型转换：Set&lt;?&gt; → Set&lt;String&gt;
     */
    private Set<String> castToStringSet(Set<?> set) {
        Set<String> result = new LinkedHashSet<>();
        for (Object item : set) {
            result.add(String.valueOf(item));
        }
        return result;
    }

    /**
     * 快照当前 Environment 中所有可枚举属性源的属性值
     */
    private void takeSnapshot() {
        for (PropertySource<?> ps : environment.getPropertySources()) {
            if (ps instanceof EnumerablePropertySource<?> enumerable) {
                for (String key : enumerable.getPropertyNames()) {
                    Object value = enumerable.getProperty(key);
                    if (value instanceof String strValue) {
                        snapshot.put(key, strValue);
                    } else if (value != null) {
                        snapshot.put(key, String.valueOf(value));
                    }
                }
            }
        }
    }
}
