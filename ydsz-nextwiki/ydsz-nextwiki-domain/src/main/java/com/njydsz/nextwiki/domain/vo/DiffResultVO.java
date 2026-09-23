package com.njydsz.nextwiki.domain.vo;

import java.io.Serializable;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 版本对比结果 VO（P3-1：从 VersionDiffService 内部类提取为统一领域 VO）。
 *
 * <p>聚合一次 diff 计算的全量结果，包含条目列表、行数统计与摘要信息，直接用于前端可视化渲染。
 *
 * @author ydsz-team
 * @since 26.09.23
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "版本对比结果")
public class DiffResultVO implements Serializable {

  private static final long serialVersionUID = 1L;

  /** diff 条目列表 */
  @Schema(description = "差异条目列表")
  private List<DiffEntryVO> entries;

  /** 旧版本总行数 */
  @Schema(description = "旧版本总行数")
  private int oldLineCount;

  /** 新版本总行数 */
  @Schema(description = "新版本总行数")
  private int newLineCount;

  /** 新增行数 */
  @Schema(description = "新增行数")
  private int additions;

  /** 删除行数 */
  @Schema(description = "删除行数")
  private int deletions;
}
