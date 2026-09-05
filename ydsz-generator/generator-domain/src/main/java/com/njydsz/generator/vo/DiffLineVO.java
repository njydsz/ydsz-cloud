package com.njydsz.generator.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Diff 行 VO（文件差异对比展示）。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiffLineVO {

  /** 行号。 */
  private Integer lineNumber;
  /** 原始内容。 */
  private String oldContent;
  /** 新内容。 */
  private String newContent;
  /** 变更类型（UNCHANGED/ADDED/REMOVED）。 */
  private String changeType;
}
