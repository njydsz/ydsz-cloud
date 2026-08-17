package com.njydsz.system.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import com.njydsz.common.excel.annotation.ExcelProperty;

/**
 * 系统配置 Excel 导入导出 VO
 *
 * <p>用于 ydsz-common-excel 的导入导出映射，字段通过 {@link ExcelProperty} 注解与 Excel 列对应。
 *
 * @author ydsz-team
 * @since 1.9.0
 */
@Data
@Schema(description = "系统配置 Excel 导入导出")
public class ConfigExcelVO {

  /** 配置分组 */
  @ExcelProperty(value = "配置分组", order = 1, width = 20)
  private String configGroup;

  /** 配置键 */
  @ExcelProperty(value = "配置键", order = 2, width = 30)
  private String configKey;

  /** 配置值 */
  @ExcelProperty(value = "配置值", order = 3, width = 40)
  private String configValue;

  /** 值类型（STRING/NUMBER/BOOLEAN/JSON） */
  @ExcelProperty(value = "值类型", order = 4, width = 12)
  private String valueType;

  /** 默认值 */
  @ExcelProperty(value = "默认值", order = 5, width = 30)
  private String defaultValue;

  /** 配置描述 */
  @ExcelProperty(value = "配置描述", order = 6, width = 40)
  private String description;

  /** 是否公开（0=私有，1=公开） */
  @ExcelProperty(value = "是否公开", order = 7, width = 10)
  private Integer isPublic;

  /** 排序序号 */
  @ExcelProperty(value = "排序序号", order = 8, width = 10)
  private Integer sortOrder;

  /** 状态（ENABLED/DISABLED） */
  @ExcelProperty(value = "状态", order = 9, width = 12)
  private String status;
}
