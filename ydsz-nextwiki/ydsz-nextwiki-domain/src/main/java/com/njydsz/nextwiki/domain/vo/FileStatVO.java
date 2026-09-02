package com.njydsz.nextwiki.domain.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件类型统计 VO
 *
 * <p>按文件后缀维度聚合统计结果，用于存储空间分析、文件分类占比展示等场景。
 *
 * <p><b>使用场景：</b>存储分析看板按后缀展示文件分布饼图 / 柱状图。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "文件类型统计")
public class FileStatVO implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 文件后缀（如 pdf、docx、xlsx），统一小写 */
  @Schema(description = "文件后缀")
  private String suffix;

  /** 该后缀对应的文件数量 */
  @Schema(description = "文件数量")
  private int fileCount;

  /** 该后缀对应的文件总大小（字节） */
  @Schema(description = "文件总大小（字节）")
  private long totalSize;
}
