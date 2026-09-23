package com.njydsz.nextwiki.domain.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.njydsz.nextwiki.domain.enums.DiffTypeEnum;

/**
 * 版本对比差异条目 VO（P3-1：从 VersionDiffService 内部类提取为独立领域 VO）。
 *
 * <p>标识单行文本的变更类型与内容，前端据此渲染 diff 高亮（红色删除 / 绿色新增 / 灰色未变）。
 *
 * @author ydsz-team
 * @since 26.09.23
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "版本对比差异条目")
public class DiffEntryVO implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 变更类型：新增 / 删除 / 未变 */
  @Schema(description = "变更类型")
  private DiffTypeEnum type;

  /** 行内容文本 */
  @Schema(description = "行内容")
  private String lineContent;

  /** 旧版本行号（0 表示不存在） */
  @Schema(description = "旧版本行号")
  private int oldLineNumber;

  /** 新版本行号（0 表示不存在） */
  @Schema(description = "新版本行号")
  private int newLineNumber;
}
