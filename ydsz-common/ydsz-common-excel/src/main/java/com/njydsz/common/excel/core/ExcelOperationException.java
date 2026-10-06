package com.njydsz.common.excel.core;

/**
 * Excel 操作异常（unchecked）。
 *
 * <p>当 {@link ExcelFacade} 写入/读取 Excel 文件失败时抛出，包装 IO 异常或 EasyExcel 运行时异常。
 *
 * @author ydsz-team
 * @since 26.10.03
 */
public class ExcelOperationException extends RuntimeException {

  /** 序列化版本号。 */
  private static final long serialVersionUID = 1L;

  /**
   * 构造 Excel 操作异常。
   *
   * @param message 异常描述信息
   * @param cause 根因异常
   */
  public ExcelOperationException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * 构造 Excel 操作异常（无根因）。
   *
   * @param message 异常描述信息
   */
  public ExcelOperationException(String message) {
    super(message);
  }
}
