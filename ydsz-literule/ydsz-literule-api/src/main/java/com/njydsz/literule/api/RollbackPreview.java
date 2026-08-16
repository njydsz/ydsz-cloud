package com.njydsz.literule.api;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 规则回滚预览（P3-1）
 *
 * <p>在执行回滚前，对比当前版本与目标版本的差异，生成预览报告。 前端可基于此报告展示变更项，经用户确认后执行一键回滚。
 *
 * <h3>字段差异类型</h3>
 *
 * <ul>
 *   <li>{@link DiffType#MODIFIED}：字段值已修改
 *   <li>{@link DiffType#ADDED}：目标版本有该字段，当前版本无
 *   <li>{@link DiffType#REMOVED}：当前版本有该字段，目标版本无
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RollbackPreview implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 规则编码 */
  private String ruleCode;

  /** 规则名称 */
  private String ruleName;

  /** 当前版本号 */
  private int currentVersion;

  /** 目标版本号 */
  private int targetVersion;

  /** 目标版本操作人 */
  private String targetVersionOperator;

  /** 目标版本变更描述 */
  private String targetVersionChangeDesc;

  /** 目标版本创建时间 */
  private LocalDateTime targetVersionCreatedAt;

  /** 是否允许回滚 */
  private boolean rollbackAllowed;

  /** 不允许回滚的原因（rollbackAllowed=false 时填写） */
  private String rollbackBlockedReason;

  /** 字段差异列表 */
  @Builder.Default private List<FieldDiff> diffs = new ArrayList<>();

  /** 差异数量 */
  public int getDiffCount() {
    return diffs != null ? diffs.size() : 0;
  }

  /** 字段差异 */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class FieldDiff implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 字段名 */
    private String field;

    /** 字段中文名 */
    private String fieldLabel;

    /** 当前版本的值 */
    private String currentValue;

    /** 目标版本的值 */
    private String targetValue;

    /** 差异类型 */
    private DiffType diffType;

    /** 生成差异描述 */
    public String describe() {
      return switch (diffType) {
        case MODIFIED -> fieldLabel + ": " + currentValue + " → " + targetValue;
        case ADDED -> fieldLabel + ": (无) → " + targetValue;
        case REMOVED -> fieldLabel + ": " + currentValue + " → (无)";
      };
    }
  }

  /** 差异类型 */
  public enum DiffType {
    /** 字段值已修改 */
    MODIFIED,
    /** 目标版本新增了该字段值 */
    ADDED,
    /** 目标版本移除了该字段值 */
    REMOVED
  }
}
