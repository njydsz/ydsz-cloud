package com.njydsz.literule.domain.enums;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 规则生命周期状态枚举
 *
 * @author ydsz
 * @since 26.09.01
 */
public enum RuleStatus {

  /** 草稿：规则已创建但未提交审核 */
  DRAFT("草稿"),

  /** 待审核：规则已提交，等待审核（向后兼容，等价于 REVIEW_L1） */
  REVIEW("待审核"),

  /** 一级审核中（P1-3 多级审批流） */
  REVIEW_L1("一级审核中"),

  /** 二级审核中（P1-3 多级审批流） */
  REVIEW_L2("二级审核中"),

  /** 终审中（P1-3 多级审批流） */
  REVIEW_FINAL("终审中"),

  /** 已发布：规则已审核通过并生效 */
  PUBLISHED("已发布"),

  /** 已停用：规则被手动停用 */
  DISABLED("已停用"),

  /** 已归档：规则已废弃，仅保留历史记录 */
  ARCHIVED("已归档");

  private static final Logger LOG = LoggerFactory.getLogger(RuleStatus.class);

  private final String desc;

  RuleStatus(String desc) {
    this.desc = desc;
  }

  public String getDesc() {
    return desc;
  }

  /**
   * 从字符串安全解析状态枚举
   *
   * @param code 状态编码（大小写不敏感）
   * @return 对应的 RuleStatus；未匹配返回 null
   * @since 26.09.01
   */
  public static RuleStatus fromCode(String code) {
    if (code == null || code.isBlank()) {
      return null;
    }
    try {
      return RuleStatus.valueOf(code.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      LOG.warn("[RuleStatus] 枚举解析失败 code={}: {}", code, e.getMessage());
      return null;
    }
  }

  /**
   * 检查是否允许的转换
   *
   * <p>P1-3 多级审批流状态转换路径：
   *
   * <ul>
   *   <li>DRAFT → REVIEW_L1（提交多级审核）/ REVIEW（兼容单级审核）/ PUBLISHED / ARCHIVED
   *   <li>REVIEW_L1 → REVIEW_L2（一级通过）/ DRAFT（一级驳回）/ ARCHIVED（一级拒绝） / PUBLISHED（1 级审批流直通发布）
   *   <li>REVIEW_L2 → REVIEW_FINAL（二级通过）/ REVIEW_L1（二级驳回）/ ARCHIVED（二级拒绝） / PUBLISHED（2 级审批流直通发布）
   *   <li>REVIEW_FINAL → PUBLISHED（终审通过）/ REVIEW_L2（终审驳回）/ ARCHIVED（终审拒绝）
   *   <li>REVIEW → PUBLISHED / DRAFT / ARCHIVED / REVIEW_L2（向后兼容，等价于 REVIEW_L1）
   * </ul>
   *
   * <p>设计说明：REVIEW_L1/REVIEW_L2 均允许直通 PUBLISHED，以支持 1 级、2 级、3 级 审批流灵活发布。例如 2 级审批流序列为 REVIEW_L1 →
   * REVIEW_L2 → PUBLISHED； 3 级审批流序列为 REVIEW_L1 → REVIEW_L2 → REVIEW_FINAL → PUBLISHED。
   *
   * @param target 目标状态
   * @return 是否允许从当前状态迁移到目标状态
   */
  public boolean canTransitionTo(RuleStatus target) {
    return switch (this) {
      case DRAFT ->
          target == REVIEW || target == REVIEW_L1 || target == PUBLISHED || target == ARCHIVED;
      case REVIEW_L1 ->
          target == REVIEW_L2 || target == DRAFT || target == ARCHIVED || target == PUBLISHED;
      case REVIEW_L2 ->
          target == REVIEW_FINAL
              || target == REVIEW_L1
              || target == ARCHIVED
              || target == PUBLISHED;
      case REVIEW_FINAL -> target == PUBLISHED || target == REVIEW_L2 || target == ARCHIVED;
        // REVIEW 向后兼容：等价于 REVIEW_L1，同时保留 REVIEW → PUBLISHED 的单级审批直通
      case REVIEW ->
          target == PUBLISHED || target == DRAFT || target == ARCHIVED || target == REVIEW_L2;
      case PUBLISHED -> target == DISABLED || target == ARCHIVED;
      case DISABLED -> target == PUBLISHED || target == ARCHIVED;
      case ARCHIVED -> false; // 已归档不可再变更
    };
  }
}
