package com.njydsz.common.config.hotreload;

/**
 * 配置变更监听器
 *
 * <p>实现此接口可接收配置中心（Nacos / Apollo 等）下发的属性变更通知。 由 {@link ConfigChangeBridge} 在 Spring Cloud {@code
 * EnvironmentChangeEvent} 触发时分发到所有注册的监听器实例。
 *
 * <h3>使用示例</h3>
 *
 * <pre>{@code
 * @Component
 * public class MyConfigListener implements ConfigChangeListener {
 *     &#64;Override
 *     public void onChange(String key, String oldValue, String newValue) {
 *         LOG.info("配置变更: {} | {} -> {}", key, oldValue, newValue);
 *         // 响应配置变更，刷新本地缓存等
 *     }
 * }
 * }</pre>
 *
 * <h3>注册方式</h3>
 *
 * <ul>
 *   <li>Spring Bean：标注 {@code @Component} 即自动被 {@link ConfigChangeBridge} 发现
 *   <li>手动注册：调用 {@code ConfigChangeBridge.addListener()}
 * </ul>
 *
 * <h3>配置过滤</h3>
 *
 * <p>如需仅接收特定前缀的变更，在实现类内部自行过滤即可：
 *
 * <pre>{@code
 * @Component
 * public class FeatureConfigListener implements ConfigChangeListener {
 *     &#64;Override
 *     public void onChange(String key, String oldValue, String newValue) {
 *         if (!key.startsWith("feature.")) {
 *             return;
 *         }
 *         // 仅处理 feature. 前缀的配置
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@FunctionalInterface
public interface ConfigChangeListener {

  /**
   * 单个属性变更回调
   *
   * @param key 变更的属性键（如 {@code "spring.datasource.password"}）
   * @param oldValue 变更前的值；如果未启用快照或首次加载，可能为 {@code null}
   * @param newValue 变更后的值；属性被删除时为 {@code null}
   */
  void onChange(String key, String oldValue, String newValue);
}
