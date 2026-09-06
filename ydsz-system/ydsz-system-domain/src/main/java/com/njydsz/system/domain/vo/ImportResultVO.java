package com.njydsz.system.domain.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.excel.api.result.ExcelImportResult;

/**
 * 导入结果 VO（系统模块）。
 *
 * <p>封装 Excel 导入的结果统计信息，继承通用 {@link ExcelImportResult} 基类。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ImportResultVO extends ExcelImportResult {

  /** 导入结果消息 */
  private String message;
}
