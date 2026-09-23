package com.njydsz.nextwiki.domain.enums;

/**
 * 版本对比差异条目类型枚举（P3-1：从 VersionDiffService 内部枚举提取为领域枚举）。
 *
 * <p>标识文本行级别 diff 操作类型，用于前端渲染差异高亮样式。
 *
 * @author ydsz-team
 * @since 26.09.23
 */
public enum DiffTypeEnum {

  /** 新增行 */
  ADD,

  /** 删除行 */
  DELETE,

  /** 未变更行 */
  UNCHANGED
}
