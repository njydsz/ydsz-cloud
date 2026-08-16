package com.njydsz.common.config.hotreload;

import java.util.Collections;
import java.util.List;

import org.springframework.context.ApplicationEvent;

/**
 * 配置变更事件
 *
 * <p>当配置中心（Nacos / Apollo / Spring Cloud Config）下发配置刷新时， 由 {@link ConfigChangeBridge} 检测属性变更并发布此事件。
 * 业务模块可通过 {@code @EventListener} 监听此事件实现自定义刷新逻辑。
 *
 * <h3>事件分发时序</h3>
 *
 * <ol>
 *   <li>配置中心推送变更 → Spring Cloud 发布 {@code RefreshEvent}
 *   <li>{@link ConfigChangeBridge} 收到 {@code RefreshEvent}，快照当前 Environment 中所有属性
 *   <li>Spring Cloud 发布 {@code EnvironmentChangeEvent}，完成属性源更新
 *   <li>{@link ConfigChangeBridge} 收到 {@code EnvironmentChangeEvent}，对比快照计算 diff
 *   <li>发布 {@link ConfigChangeEvent}（本事件），通知所有 {@link ConfigChangeListener}
 * </ol>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class ConfigChangeEvent extends ApplicationEvent {

  private final List<ConfigChange> changes;

  /**
   * @param source 事件源（通常是 {@link ConfigChangeBridge} 实例）
   * @param changes 变更的属性列表
   */
  public ConfigChangeEvent(Object source, List<ConfigChange> changes) {
    super(source);
    this.changes = changes;
  }

  /**
   * 获取本次配置刷新的所有属性变更
   *
   * @return 不可变的变更列表，如无变更则为空列表
   */
  public List<ConfigChange> getChanges() {
    return Collections.unmodifiableList(changes);
  }

  /**
   * 单个属性的变更记录
   *
   * @param key 属性键
   * @param oldValue 变更前的值（可能为 {@code null}）
   * @param newValue 变更后的值（属性被删除时为 {@code null}）
   */
  public record ConfigChange(String key, String oldValue, String newValue) {}
}
