package com.njydsz.common.config.hotreload;

import java.util.List;

/**
 * 配置变更监听器
 *
 * <p>实现此接口可接收配置中心（Nacos / Apollo 等）下发的属性变更通知。
 * 由 {@link ConfigChangeBridge} 在 Spring Cloud {@code EnvironmentChangeEvent}
 * 触发时分发到所有注册的监听器实例。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Component
 * public class MyConfigListener implements ConfigChangeListener {
 *     @Override
 *     public void onChange(String key, String oldValue, String newValue) {
 *         log.info("配置变更: {} | {} -> {}", key, oldValue, newValue);
 *         if ("my.feature.enabled".equals(key)) {
 *             // 响应配置变更，刷新本地缓存等
 *         }
 *     }
 * }
 * }</pre>
 *
 * <h3>批量变更处理</h3>
 * <p>当一次配置刷新涉及多个属性时，可覆写 {@link #onBatchChange(List)} 进行批量处理，
 * 避免逐个回调的额外开销。默认实现逐个调用 {@link #onChange}。
 *
 * <h3>注册方式</h3>
 * <ul>
 *   <li>Spring Bean：标注 {@code @Component} 即自动被 {@link ConfigChangeBridge} 发现</li>
 *   <li>手动注册：调用 {@code ConfigChangeBridge.addListener()}</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@FunctionalInterface
public interface ConfigChangeListener {

    /**
     * 单个属性变更回调
     *
     * @param key       变更的属性键（如 {@code "spring.datasource.password"}）
     * @param oldValue  变更前的值；如果未启用快照或首次加载，可能为 {@code null}
     * @param newValue  变更后的值；属性被删除时为 {@code null}
     */
    void onChange(String key, String oldValue, String newValue);

    /**
     * 批量属性变更回调
     *
     * <p>当一次配置刷新涉及多个属性时调用此方法。默认实现逐个调用 {@link #onChange}，
     * 如需批量处理（如整体重新初始化），请覆写此方法。
     *
     * @param changes 变更列表，包含所有变更的属性
     */
    default void onBatchChange(List<ConfigChangeEvent.ConfigChange> changes) {
        for (ConfigChangeEvent.ConfigChange c : changes) {
            onChange(c.key(), c.oldValue(), c.newValue());
        }
    }
}
