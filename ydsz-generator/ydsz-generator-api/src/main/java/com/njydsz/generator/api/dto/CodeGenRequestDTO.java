package com.njydsz.generator.api.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * 代码生成请求 DTO。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("代码生成请求")
public class CodeGenRequestDTO {

  /** 数据源 ID。 */
  @ApiModelProperty(value = "数据源 ID", required = true)
  private Long datasourceId;

  /** 模板分组 ID。 */
  @ApiModelProperty(value = "模板分组 ID", required = true)
  private Long templateGroupId;

  /** 表名（单表生成时填写）。 */
  @ApiModelProperty("表名")
  private String tableName;

  /** 输出目录。 */
  @ApiModelProperty(value = "输出目录", required = true)
  private String outputDir;

  /** 冲突策略：SKIP/OVERRIDE/MERGE。 */
  @ApiModelProperty("冲突策略")
  private String conflictStrategy;

  /** 触发人。 */
  @ApiModelProperty("触发人")
  private String triggeredBy;
}
