package com.njydsz.message.domain.enums.template;


/**
 * 模板审核状态枚举。
 *
 * <p>定义模板从草稿到最终审核通过/驳回的全生命周期状态流转。 实现 {@link BaseStatusEnum} 契约，提供 {@link #canTransitTo} 状态流转校验。
 *
 * <p><b>状态流转规则：</b>
 *
 * <ul>
 *   <li>DRAFT → AUDITING（提交审核）
 *   <li>AUDITING → APPROVED（审核通过）
 *   <li>AUDITING → REJECTED（审核驳回）
 *   <li>APPROVED / REJECTED 为终态，不可再流转
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public enum TemplateAuditStatusEnum implements BaseStatusEnum<TemplateAuditStatusEnum> {

  /** 草稿 */
  DRAFT,
  /** 审核中 */
  AUDITING,
  /** 已通过 */
  APPROVED,
  /** 已驳回 */
  REJECTED;

  /**
   * {@inheritDoc}
   *
   * <p>APPROVED 和 REJECTED 为终态，不可再流转。
   */
  @Override
  public boolean isTerminal() {
    return this == APPROVED || this == REJECTED;
  }

  /**
   * {@inheritDoc}
   *
   * <p>流转规则：
   *
   * <ul>
   *   <li>DRAFT → AUDITING
   *   <li>AUDITING → APPROVED / REJECTED
   *   <li>APPROVED / REJECTED 为终态，不可再流转
   * </ul>
   *
   * @param target 目标状态
   * @return true 表示允许流转
   */
  @Override
  public boolean canTransitTo(TemplateAuditStatusEnum target) {
    if (this == target) {
      return true;
    }
    return switch (this) {
      case DRAFT -> target == AUDITING;
      case AUDITING -> target == APPROVED || target == REJECTED;
      case APPROVED, REJECTED -> false;
    };
  }
}
