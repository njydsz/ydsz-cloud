package com.njydsz.literule.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

/**
 * 规则引擎监控大盘 - 分布指标 VO
 *
 * <p>用于饼图展示规则在多个维度的分布情况。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Builder
public class RuleDashboardDistributionVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 按状态分布：DRAFT/REVIEW/PUBLISHED/DISABLED/ARCHIVED → 数量 */
  private Map<String, Long> byStatus;

  /** 按类别分布：category → 数量 */
  private Map<String, Long> byCategory;

  /** 按严重度分布（今日触发结果中）：RED/YELLOW/NORMAL → 数量 */
  private Map<String, Long> bySeverity;

  /** 按场景分布（今日触发结果中）：scenario → 数量 */
  private Map<String, Long> byScenario;

  /** 按租户分布：tenantId → 数量 */
  private Map<String, Long> byTenant;

  /** 按责任人分布：owner → 数量 */
  private Map<String, Long> byOwner;

  /** 状态分布条目列表（用于前端饼图直接渲染） */
  private List<PieItem> statusPie;

  /** 类别分布条目列表 */
  private List<PieItem> categoryPie;

  /** 严重度分布条目列表 */
  private List<PieItem> severityPie;

  /** 场景分布条目列表 */
  private List<PieItem> scenarioPie;

  /** 饼图条目 */
  @Data
  @Builder
  public static class PieItem implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    /** 名称 */
    private String name;

    /** 数量 */
    private long value;
  }
}
