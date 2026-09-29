package com.njydsz.agent.domain.insight;

import java.util.List;

import com.njydsz.common.domain.enums.BaseStatusEnum;

/**
 * 洞察报告状态枚举。
 *
 * <p>描述洞察报告从创建到导出全生命周期的状态流转。
 *
 * @author ydsz-team
 * @since 26.09.07
 */
public enum InsightReportStatus implements BaseStatusEnum<InsightReportStatus> {

  /** 草稿（正在生成中） */
  DRAFT("draft", "草稿"),

  /** 生成完成 */
  COMPLETED("completed", "已完成"),

  /** 生成失败 */
  FAILED("failed", "生成失败"),

  /** 已导出 */
  EXPORTED("exported", "已导出");

  /** 状态编码（持久化用） */
  private final String code;

  /** 状态描述（展示用） */
  private final String description;

  /**
   * 构造状态枚举。
   *
   * @param code 状态编码
   * @param description 状态描述
   */
  InsightReportStatus(String code, String description) {
    this.code = code;
    this.description = description;
  }

  /**
   * 获取状态编码。
   *
   * @return 状态编码
   */
  public String getCode() {
    return code;
  }

  /**
   * 获取状态描述。
   *
   * @return 状态描述
   */
  public String getDescription() {
    return description;
  }

  /**
   * 是否为终态。
   *
   * <p>生成失败和已导出均为终态。
   *
   * @return true 表示终态
   */
  @Override
  public boolean isTerminal() {
    return this == FAILED || this == EXPORTED;
  }

  /**
   * 校验状态流转合法性。
   *
   * <pre>
   * DRAFT     → COMPLETED | FAILED
   * COMPLETED → EXPORTED
   * FAILED    → 终态
   * EXPORTED  → 终态
   * </pre>
   *
   * @param target 目标状态
   * @return true 表示允许从当前状态流转到目标状态
   */
  @Override
  public boolean canTransitTo(InsightReportStatus target) {
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
      case DRAFT -> target == COMPLETED || target == FAILED;
      case COMPLETED -> target == EXPORTED;
      default -> false;
    };
  }

  /**
   * 返回所有枚举值。
   *
   * @return 全量状态列表
   */
  @Override
  public List<InsightReportStatus> allStates() {
    return List.of(values());
  }

  /**
   * 根据编码解析枚举值。
   *
   * @param code 状态编码
   * @return 对应的枚举值，未知编码返回 DRAFT
   */
  public static InsightReportStatus fromCode(String code) {
    if (code == null || code.isBlank()) {
      return DRAFT;
    }
    for (InsightReportStatus status : values()) {
      if (status.code.equals(code)) {
        return status;
      }
    }
    return DRAFT;
  }
}
