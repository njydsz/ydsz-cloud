package com.njydsz.userinfo.domain.enums;


/**
 * 启用/停用状态枚举（通用）。
 *
 * <p>对应 Role / Menu / Department / Company / Post / Language 等实体的 {@code status} 字段（存储值 {@code
 * "ENABLED" / "DISABLED"}）， 实现 {@link BaseStatusEnum} 契约，提供统一的状态流转校验。
 *
 * <p><b>状态流转：</b>{@code ENABLED ⇄ DISABLED}（双向可切换，均非终态）。
 *
 * <p><b>已废弃：</b>用户生命周期管理请使用 {@link UserLifecycleStatusEnum}，本类仅保留作为向后兼容层。
 * 所有 {@code parse()} / {@code canTransitTo()} 方法委托给 {@link UserLifecycleStatusEnum}。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @deprecated 使用 {@link UserLifecycleStatusEnum} 替代，支持完整用户生命周期状态机
 * @see UserLifecycleStatusEnum
 */
@Deprecated
public enum EnableStatusEnum implements BaseStatusEnum<EnableStatusEnum> {

  /** 启用 */
  ENABLED,
  /** 停用 */
  DISABLED;

  /**
   * 解析字符串为枚举值（大小写不敏感，容忍 null 与空串）。
   *
   * <p>兼容两种格式：
   *
   * <ul>
   *   <li>标准格式：{@code "ENABLED"} / {@code "DISABLED"}（所有实体统一使用此格式）
   *   <li>遗留格式：{@code "0"}（禁用）/ {@code "1"}（启用）—— UserAccount 表的历史兼容，新代码应使用枚举字面量
   * </ul>
   *
   * <p><b>已废弃：</b>委托给 {@link UserLifecycleStatusEnum#parse(String)}，支持更多状态格式。
   *
   * @param value 状态字符串（如 "ENABLED"、"0"、"1"）
   * @return 枚举值，无法解析时返回 null
   */
  @Deprecated
  public static EnableStatusEnum parse(String value) {
    UserLifecycleStatusEnum result = UserLifecycleStatusEnum.parse(value);
    if (result == null) {
      return null;
    }
    // 仅映射 ENABLED/DISABLED，其他新状态返回 null（保持旧契约）
    return switch (result) {
      case ENABLED -> ENABLED;
      case DISABLED -> DISABLED;
      default -> null;
    };
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
   * <p>启用与停用可互相切换。委托给 {@link UserLifecycleStatusEnum#canTransitTo(UserLifecycleStatusEnum)}。
   *
   * @param target 目标状态
   * @return true 表示允许流转
   */
  @Override
  public boolean canTransitTo(EnableStatusEnum target) {
    if (target == null) {
      return false;
    }
    UserLifecycleStatusEnum source = mapToLifecycle(this);
    UserLifecycleStatusEnum targetLifecycle = mapToLifecycle(target);
    return source != null && targetLifecycle != null && source.canTransitTo(targetLifecycle);
  }

  /**
   * 将旧枚举映射到新生命周期枚举。
   *
   * @param status 旧枚举值
   * @return 对应的生命周期枚举，映射失败返回 null
   */
  private static UserLifecycleStatusEnum mapToLifecycle(EnableStatusEnum status) {
    return switch (status) {
      case ENABLED -> UserLifecycleStatusEnum.ENABLED;
      case DISABLED -> UserLifecycleStatusEnum.DISABLED;
    };
  }
}
