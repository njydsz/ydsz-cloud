package com.njydsz.system.domain.vo;

import java.util.ArrayList;
import java.util.List;

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
 * <p>结构化错误（{@link #errorItems}）与旧错误（{@link #errors}）并存： {@link #errorItems} 供前端精准定位错误行/字段； {@link #errors} 供旧版前端展示纯文本错误。
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

  /**
   * 结构化错误列表（含行号、字段名、错误码、消息）。
   *
   * <p>前端可按此渲染错误定位单元格，无需解析字符串。若为空则兼容旧的 {@link #errors} 纯文本列表。
   */
  @SuperBuilder.Default
  private List<ImportErrorItem> errorItems = new ArrayList<>(16);

  /**
   * 导入错误项 DTO（结构化表示单条错误的位置和原因）。
   *
   * <p>供前端精准定位到 Excel 的具体行和字段，渲染高亮单元格和错误提示。
   */
  @Data
  public static class ImportErrorItem {
    /** Excel 行号（从 2 开始，第 1 行为表头） */
    private int row;
    /** 出错字段名（如 typeCode / itemCode / configValue） */
    private String field;
    /** 错误码（如 DUPLICATE / REQUIRED / INVALID_FORMAT） */
    private String code;
    /** 错误描述（中文可读文案） */
    private String message;
  }
}
