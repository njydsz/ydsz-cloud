package com.njydsz.agent.domain.enums;

import com.njydsz.common.domain.enums.BaseStatusEnum;

/**
 * Agent 定义状态枚举。
 *
 * <p>对应 {@code ydsz_agent_definition.status} 字段（ENABLED / DISABLED）， 实现 {@link BaseStatusEnum}
 * 契约，提供启用/停用状态流转校验。
 *
 * <p><b>状态流转：</b>{@code ENABLED ⇄ DISABLED}（双向可切换）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum AgentStatusEnum implements BaseStatusEnum<AgentStatusEnum> {

  /** 启用（可被调度） */
  ENABLED,
  /** 停用（下线，不参与调度） */
  DISABLED;

  /**
   * 解析字符串为枚举值（大小写不敏感，容忍 null）。
   *
   * @param value 状态字符串（如 "ENABLED"）
   * @return 枚举值，无法解析时返回 null
   */
  public static AgentStatusEnum parse(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return AgentStatusEnum.valueOf(value.toUpperCase());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  /**
   * 是否启用。
   *
   * @return 当前为 ENABLED 返回 true
   */
  public boolean isEnabled() {
    return this == ENABLED;
  }

  /**
   * 校验状态流转是否合法。
   *
   * <p>启用与停用可互相切换。
   *
   * @param target 目标状态
   * @return true 表示允许流转
   */
  @Override
  public boolean canTransitTo(AgentStatusEnum target) {
    return this == target
        || this == ENABLED && target == DISABLED
        || this == DISABLED && target == ENABLED;
  }
}
