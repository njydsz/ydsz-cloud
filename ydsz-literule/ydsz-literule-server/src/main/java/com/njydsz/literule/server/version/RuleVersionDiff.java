package com.njydsz.literule.server.version;

import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 规则版本结构化 Diff 结果
 *
 * <p>对两个版本的定义进行字段级对比，产出变更项列表。 与纯文本 Diff 不同，本结果基于 {@link com.njydsz.literule.api.RuleDefinition}
 * 的字段语义进行结构化对比，前端可据此高亮具体变更字段。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleVersionDiff implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 旧版本号 */
  private int oldVersion;

  /** 新版本号 */
  private int newVersion;

  /** 规则编码 */
  private String ruleCode;

  /** 变更项列表 */
  private List<DiffEntry> entries;

  /** 变更摘要（人类可读） */
  private String summary;

  /** 单个字段的变更项 */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class DiffEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 变更类型 */
    private DiffType type;

    /** 字段名 */
    private String field;

    /** 字段中文名 */
    private String fieldLabel;

    /** 旧值 */
    private String oldValue;

    /** 新值 */
    private String newValue;
  }

  /** 变更类型枚举 */
  public enum DiffType {
    /** 新增字段 */
    ADDED,
    /** 删除字段 */
    REMOVED,
    /** 修改字段值 */
    MODIFIED,
    /** 未变更 */
    UNCHANGED
  }

  /** 是否有变更 */
  public boolean hasChanges() {
    return entries != null && entries.stream().anyMatch(e -> e.getType() != DiffType.UNCHANGED);
  }

  /** 变更字段数 */
  public int changeCount() {
    if (entries == null) return 0;
    return (int) entries.stream().filter(e -> e.getType() != DiffType.UNCHANGED).count();
  }
}
