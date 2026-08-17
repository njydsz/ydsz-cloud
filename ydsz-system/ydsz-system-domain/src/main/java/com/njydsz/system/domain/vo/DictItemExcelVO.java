package com.njydsz.system.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import com.njydsz.common.excel.annotation.ExcelProperty;

/**
 * 字典项 Excel 导入导出 VO
 *
 * <p>用于 ydsz-common-excel 的导入导出映射，字段通过 {@link ExcelProperty} 注解与 Excel 列对应。
 *
 * @author ydsz-team
 * @since 1.9.0
 */
@Data
@Schema(description = "字典项 Excel 导入导出")
public class DictItemExcelVO {

  /** 字典类型编码 */
  @ExcelProperty(value = "字典类型编码", order = 1, width = 20)
  private String typeCode;

  /** 字典项编码 */
  @ExcelProperty(value = "字典项编码", order = 2, width = 20)
  private String itemCode;

  /** 字典项展示值 */
  @ExcelProperty(value = "字典项展示值", order = 3, width = 30)
  private String itemValue;

  /** 排序序号 */
  @ExcelProperty(value = "排序序号", order = 4, width = 10)
  private Integer sortOrder;

  /** 父级 ID（树形字典使用） */
  @ExcelProperty(value = "父级ID", order = 5, width = 20)
  private String parentId;

  /** 字典项描述 */
  @ExcelProperty(value = "字典项描述", order = 6, width = 40)
  private String description;

  /** 启用状态（ENABLED/DISABLED） */
  @ExcelProperty(value = "启用状态", order = 7, width = 12)
  private String status;
}
