package com.njydsz.userinfo.domain.enums;

import java.util.Arrays;
import java.util.List;

import com.njydsz.common.domain.enums.BaseStatusEnum;

/**
 * 用户生命周期状态枚举。
 *
 * <p>定义用户从注册到离职的完整生命周期状态机，替代旧版 {@link EnableStatusEnum} 的简单启用/停用二元模型。
 *
 * <p><b>状态流转规则：</b>
 *
 * <pre>
 * PENDING   → ENABLED   （激活：邮箱/手机验证通过）
 * ENABLED   ⇄ SUSPENDED （暂停/恢复：临时停用，可恢复）
 * ENABLED   → DISABLED  （禁用：长期停用）
 * ENABLED   → RESIGNED  （离职：终态）
 * SUSPENDED → ENABLED   （恢复：从暂停状态恢复）
 * SUSPENDED → DISABLED  （禁用：从暂停状态直接禁用）
 * SUSPENDED → RESIGNED  （离职：从暂停状态离职）
 * DISABLED  → ENABLED   （重新启用：从禁用状态恢复）
 * RESIGNED  → (无)       （终态，不可再流转）
 * </pre>
 *
 * <p><b>终态：</b>{@link #RESIGNED} 为唯一终态，不可再流转到任何其他状态。
 *
 * <p><b>登录权限：</b>仅 {@link #ENABLED} 状态允许登录。
 *
 * <p><b>存储格式：</b>DB 列使用整数（0=禁用, 1=启用，历史遗留），新状态使用枚举名字符串存储。 通过 {@link IntegerStringTypeHandler} 自动转换。
 *
 * <p><b>向后兼容：</b>{@link EnableStatusEnum} 保留为兼容层，新代码应使用本类。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see EnableStatusEnum
 * @see BaseStatusEnum
 */
public enum UserLifecycleStatusEnum implements BaseStatusEnum<UserLifecycleStatusEnum> {

  /**
   * 待激活（已注册但未验证邮箱/手机）。
   *
   * <p>可流转到：ENABLED。
   */
  PENDING,

  /**
   * 启用（正常使用）。
   *
   * <p>可流转到：SUSPENDED、DISABLED、RESIGNED。
   */
  ENABLED,

  /**
   * 暂停（临时停用，可恢复）。
   *
   * <p>可流转到：ENABLED、DISABLED、RESIGNED。
   */
  SUSPENDED,

  /**
   * 禁用（长期停用）。
   *
   * <p>可流转到：ENABLED。
   */
  DISABLED,

  /**
   * 已离职（终态，不可再激活）。
   *
   * <p>不可流转到任何状态。
   */
  RESIGNED;

  /**
   * 校验状态流转是否合法。
   *
   * <p>流转规则：
   *
   * <ul>
   *   <li>自身到自身：允许（幂等）
   *   <li>终态（RESIGNED）到任何状态：禁止
   *   <li>PENDING → ENABLED：允许
   *   <li>ENABLED ⇄ SUSPENDED：允许
   *   <li>ENABLED → DISABLED：允许
   *   <li>ENABLED → RESIGNED：允许
   *   <li>SUSPENDED → ENABLED：允许
   *   <li>SUSPENDED → DISABLED：允许
   *   <li>SUSPENDED → RESIGNED：允许
   *   <li>DISABLED → ENABLED：允许
   * </ul>
   *
   * @param target 目标状态
   * @return true 表示允许流转
   */
  @Override
  public boolean canTransitTo(UserLifecycleStatusEnum target) {
    if (target == null) {
      return false;
    }
    if (this == target) {
      return true;
    }
    if (this.isTerminal()) {
      return false;
    }
    return switch (this) {
      case PENDING -> target == ENABLED;
      case ENABLED -> target == SUSPENDED || target == DISABLED || target == RESIGNED;
      case SUSPENDED -> target == ENABLED || target == DISABLED || target == RESIGNED;
      case DISABLED -> target == ENABLED;
      case RESIGNED -> false;
    };
  }

  /**
   * 是否为终态。
   *
   * <p>RESIGNED 为终态，不可再流转到其他状态。
   *
   * @return true 表示当前状态为终态
   */
  @Override
  public boolean isTerminal() {
    return this == RESIGNED;
  }

  /**
   * 是否允许登录。
   *
   * <p>仅 ENABLED 状态允许登录。
   *
   * @return true 表示允许登录
   */
  public boolean canLogin() {
    return this == ENABLED;
  }

  /**
   * 获取所有状态枚举值。
   *
   * @return 所有状态枚举值列表
   */
  @Override
  public List<UserLifecycleStatusEnum> allStates() {
    return Arrays.asList(values());
  }

  /**
   * 解析字符串为枚举值（大小写不敏感，容忍 null 与空串）。
   *
   * <p>兼容三种格式：
   *
   * <ul>
   *   <li>遗留整数格式：{@code "0"}（禁用）/ {@code "1"}（启用）—— UserAccountDO 表的历史兼容
   *   <li>旧枚举字面量：{@code "ENABLED"} / {@code "DISABLED"}
   *   <li>新枚举字面量：{@code "PENDING"} / {@code "SUSPENDED"} / {@code "RESIGNED"}
   * </ul>
   *
   * @param value 状态字符串
   * @return 枚举值，无法解析时返回 null
   */
  public static UserLifecycleStatusEnum parse(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    // 遗留 0/1 兼容（UserAccountDO 表历史数据）
    if ("0".equals(value)) {
      return DISABLED;
    }
    if ("1".equals(value)) {
      return ENABLED;
    }
    try {
      return UserLifecycleStatusEnum.valueOf(value.toUpperCase());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
