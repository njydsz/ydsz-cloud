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
 * <h3>changeType 语义</h3>
 *
 * <ul>
 *   <li>{@link ChangeType#ADDED} — 属性在本次刷新中新增（刷新前不存在）
 *   <li>{@link ChangeType#CHANGED} — 属性值在本次刷新中发生变化（刷新前后均存在且值不同）
 *   <li>{@link ChangeType#DELETED} — 属性在本次刷新中被删除（刷新后不存在）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class ConfigChangeEvent extends ApplicationEvent {

  private final List<ConfigChange> changes;

  /**
   * 配置变更事件。
   *
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
   * @param changeType 变更类型（新增 / 修改 / 删除）
   */
  public record ConfigChange(String key, String oldValue, String newValue, ChangeType changeType) {}

  /**
   * 配置属性变更类型枚举
   *
   * <p>对标 Apollo {@code ConfigChangeEvent.changeType} 与 Nacos {@code ConfigChangeEvent.eventType}，
   * 明确区分属性的新增、修改与删除，避免监听器通过 {@code oldValue/newValue == null} 推断导致的语义歧义。
   *
   * @since 26.09.20
   */
  public enum ChangeType {

    /** 属性新增（刷新前不存在，刷新后存在） */
    ADDED,

    /** 属性值变更（刷新前后均存在且值不同） */
    CHANGED,

    /** 属性删除（刷新前存在，刷新后不存在） */
    DELETED
  }
}
