package com.njydsz.generator.api.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * 表元数据 DTO。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("表元数据")
public class TableMetaDTO {

  /** 记录 ID。 */
  @ApiModelProperty("表元数据 ID")
  private Long id;

  /** 数据源 ID。 */
  @ApiModelProperty("数据源 ID")
  private Long datasourceId;

  /** 物理表名。 */
  @ApiModelProperty("物理表名")
  private String tableName;

  /** 表注释。 */
  @ApiModelProperty("表注释")
  private String comment;

  /** 别名。 */
  @ApiModelProperty("用户自定义别名")
  private String aliasName;

  /** 模块名称。 */
  @ApiModelProperty("模块名称")
  private String moduleName;

  /** 列数量。 */
  @ApiModelProperty("列数量")
  private Integer columnCount;
}
