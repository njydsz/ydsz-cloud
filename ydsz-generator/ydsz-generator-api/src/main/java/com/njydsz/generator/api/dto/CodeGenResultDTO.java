package com.njydsz.generator.api.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * 代码生成结果 DTO。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("代码生成结果（预览/生成通用）")
public class CodeGenResultDTO {

  /** 任务 ID（正式生成时返回）。 */
  @ApiModelProperty("任务 ID")
  private Long historyId;

  /** 文件名。 */
  @ApiModelProperty("文件名")
  private String fileName;

  /** 文件路径。 */
  @ApiModelProperty("文件路径")
  private String filePath;

  /** 代码内容。 */
  @ApiModelProperty("代码内容")
  private String content;

  /** 是否冲突。 */
  @ApiModelProperty("是否冲突")
  private Boolean conflict;

  /** 生成文件总数。 */
  @ApiModelProperty("生成文件总数")
  private Integer fileCount;

  /** 成功文件数。 */
  @ApiModelProperty("成功文件数")
  private Integer successCount;

  /** 跳过文件数。 */
  @ApiModelProperty("跳过文件数")
  private Integer skipCount;

  /** 失败文件数。 */
  @ApiModelProperty("失败文件数")
  private Integer failCount;
}
