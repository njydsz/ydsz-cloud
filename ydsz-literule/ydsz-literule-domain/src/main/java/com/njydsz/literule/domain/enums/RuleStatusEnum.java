package com.njydsz.literule.domain.enums;

import com.njydsz.common.domain.enums.BaseStatusEnum;

/**
 * 规则生命周期状态枚举。
 *
 * <p>对应 {@code ydsz_rule_def.status} 字段（DRAFT / PUBLISHED / DISABLED）， 实现 {@link BaseStatusEnum}
 * 契约，提供规则发布/下线状态流转校验。
 *
 * <p><b>状态流转：</b>
 *
 * <pre>
 *   DRAFT ──▶ PUBLISHED ──▶ DISABLED
 *     ▲          │             │
 *     └──────────┴─────────────┘（重新编辑回到 DRAFT）
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public enum RuleStatusEnum implements BaseStatusEnum<RuleStatusEnum> {

  /** 草稿（编辑中，未发布） */
  DRAFT,
  /** 已发布（线上生效） */
  PUBLISHED,
  /** 已停用（下线，不再参与匹配） */
  DISABLED;

  /**
   * 解析字符串为枚举值（大小写不敏感，容忍 null）。
   *
   * @param value 状态字符串（如 "PUBLISHED"）
   * @return 枚举值，无法解析时返回 null
   */
  public static RuleStatusEnum parse(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return RuleStatusEnum.valueOf(value.toUpperCase());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  /**
   * 是否为终态。
   *
   * <p>DISABLED 规则可重新编辑进入 DRAFT，故本枚举无严格终态。
   *
   * @return 恒为 false
   */
  @Override
  public boolean isTerminal() {
    return false;
  }

  /**
   * 校验状态流转是否合法。
   *
   * <p>流转规则：
   *
   * <ul>
   *   <li>DRAFT → PUBLISHED / DISABLED
   *   <li>PUBLISHED → DISABLED / DRAFT（下线或重新编辑）
   *   <li>DISABLED → DRAFT / PUBLISHED（重新编辑或直接恢复发布）
   * </ul>
   *
   * @param target 目标状态
   * @return true 表示允许流转
   */
  @Override
  public boolean canTransitTo(RuleStatusEnum target) {
    if (this == target) {
      return true;
    }
    return switch (this) {
      case DRAFT -> target == PUBLISHED || target == DISABLED;
      case PUBLISHED -> target == DISABLED || target == DRAFT;
      case DISABLED -> target == DRAFT || target == PUBLISHED;
    };
  }
}
