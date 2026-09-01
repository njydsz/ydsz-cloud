package com.njydsz.system.server.vo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import com.njydsz.common.excel.annotation.ExcelProperty;



/**
 * 系统变量 Excel 导入导出 VO
 *
 * <p>用于 ydsz-common-excel 的导入导出映射，字段通过 {@link ExcelProperty} 注解与 Excel 列对应。
 *
 * <p><b>P1-3 分层调整：</b>从 domain 层移至 server 层，剥离 domain 对 common-excel 的依赖。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Schema(description = "系统变量 Excel 导入导出")
public class VariableExcelVO {

  /** 变量键 */
  @ExcelProperty(value = "变量键", order = 1, width = 30)
  private String variableKey;

  /** 变量值 */
  @ExcelProperty(value = "变量值", order = 2, width = 40)
  private String variableValue;

  /** 值类型（STRING/NUMBER/BOOLEAN/JSON） */
  @ExcelProperty(value = "值类型", order = 3, width = 12)
  private String valueType;

  /** 变量描述 */
  @ExcelProperty(value = "变量描述", order = 4, width = 40)
  private String description;

  /** 启用状态（ENABLED/DISABLED） */
  @ExcelProperty(value = "启用状态", order = 5, width = 12)
  private String status;
}
