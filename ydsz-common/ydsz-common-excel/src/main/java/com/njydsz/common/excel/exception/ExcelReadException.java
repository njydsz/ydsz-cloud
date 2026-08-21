package com.njydsz.common.excel.exception;

/**
 * Excel 读取异常类
 *
 * <p>封装 Excel 文件读取过程中的各类错误，包括文件解析失败、 数据类型转换错误、注解配置错误等场景。自包含的异常体系，不依赖全局异常处理模块。
 *
 * <h3>使用示例</h3>
 *
 * <pre>{@code
 * try {
 *     ExcelFacade.read("data.xlsx", User.class)
 *         .sheet(0)
 *         .headRowNumber(1)
 *         .doRead(new ReadListener<User>() {
 *             @Override
 *             public void onData(AnalysisContext context, User data) {
 *                 // 处理每行数据
 *             }
 *         });
 * } catch (ExcelReadException e) {
 *     LOG.error("读取失败 - 错误码: {}, 消息: {}", e.getCode(), e.getMessage(), e);
 *     // 获取异常码枚举
 *     ExcelExceptionCode code = e.getExceptionCode();
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see ExcelException
 * @see ExcelExceptionCode
 */
public class ExcelReadException extends ExcelException {

  private static final long serialVersionUID = 1L;

  /** 发生错误的行号 */
  private Integer rowNumber;

  /** 发生错误的列号 */
  private Integer columnNumber;

  /** 原始单元格值 */
  private transient Object rawCellValue;

  public ExcelReadException() {
    super();
  }

  public ExcelReadException(String message) {
    super(message);
  }

  public ExcelReadException(String message, Throwable cause) {
    super(message, cause);
  }

  public ExcelReadException(ExcelExceptionCode exceptionCode) {
    super(exceptionCode);
  }

  public ExcelReadException(ExcelExceptionCode exceptionCode, String message) {
    super(exceptionCode, message);
  }

  public ExcelReadException(ExcelExceptionCode exceptionCode, String message, Throwable cause) {
    super(exceptionCode, message, cause);
  }

  public Integer getRowNumber() {
    return rowNumber;
  }

  public void setRowNumber(Integer rowNumber) {
    this.rowNumber = rowNumber;
  }

  public Integer getColumnNumber() {
    return columnNumber;
  }

  public void setColumnNumber(Integer columnNumber) {
    this.columnNumber = columnNumber;
  }

  public Object getRawCellValue() {
    return rawCellValue;
  }

  public void setRawCellValue(Object rawCellValue) {
    this.rawCellValue = rawCellValue;
  }

  /**
   * 创建文件不存在的异常
   *
   * @param filePath 文件路径
   * @return 返回值说明
   */
  public static ExcelReadException fileNotFound(String filePath) {
    ExcelReadException ex =
        new ExcelReadException(ExcelExceptionCode.READ_FILE_NOT_FOUND, "Excel文件不存在");
    ex.setContext(new Object[] {filePath});
    return ex;
  }

  /**
   * 创建文件格式错误的异常
   *
   * @param filePath 文件路径
   * @param reason 原因
   * @return 返回值说明
   */
  public static ExcelReadException invalidFormat(String filePath, String reason) {
    ExcelReadException ex =
        new ExcelReadException(ExcelExceptionCode.READ_INVALID_FORMAT, "Excel文件格式无效: " + reason);
    ex.setContext(new Object[] {filePath, reason});
    return ex;
  }

  /**
   * 创建数据类型转换异常
   *
   * @param row 行对象
   * @param col 列号
   * @param rawValue 原始值
   * @param targetType 目标类型
   * @param cause 原因
   * @return 返回值说明
   */
  public static ExcelReadException conversionFailed(
      int row, int col, Object rawValue, Class<?> targetType, Throwable cause) {
    ExcelReadException ex =
        new ExcelReadException(
            ExcelExceptionCode.READ_CONVERSION_FAILED,
            String.format(
                "数据类型转换失败: 行=%d, 列=%d, 值=%s, 目标类型=%s",
                row, col, rawValue, targetType.getSimpleName()),
            cause);
    ex.setRowNumber(row);
    ex.setColumnNumber(col);
    ex.setRawCellValue(rawValue);
    return ex;
  }

  /**
   * 创建数据验证失败的异常
   *
   * @param row 行对象
   * @param fieldName 字段名
   * @param value 值
   * @param reason 原因
   * @return 返回值说明
   */
  public static ExcelReadException validationFailed(
      int row, String fieldName, Object value, String reason) {
    ExcelReadException ex =
        new ExcelReadException(
            ExcelExceptionCode.READ_VALIDATION_FAILED,
            String.format("数据验证失败: 行=%d, 字段=%s, 值=%s, 原因=%s", row, fieldName, value, reason));
    ex.setRowNumber(row);
    return ex;
  }

  /**
   * 创建文件过大的异常
   *
   * @param actualSizeMB 实际文件大小 (MB)
   * @param maxSizeMB 最大允许大小 (MB)
   * @return 文件过大异常实例
   */
  public static ExcelReadException fileTooLarge(long actualSizeMB, int maxSizeMB) {
    return new ExcelReadException(
        ExcelExceptionCode.READ_FILE_TOO_LARGE,
        String.format("Excel文件过大: %dMB, 超过最大限制 %dMB", actualSizeMB, maxSizeMB));
  }

  /**
   * 创建读取 IO 错误的异常
   *
   * @param row 当前读取行号
   * @param cause 原始异常
   * @return IO 错误异常实例
   */
  public static ExcelReadException ioError(int row, Throwable cause) {
    ExcelReadException ex =
        new ExcelReadException(
            ExcelExceptionCode.READ_IO_ERROR, String.format("Excel读取IO异常: 当前行=%d", row), cause);
    ex.setRowNumber(row);
    return ex;
  }

  /**
   * 创建内存溢出的异常
   *
   * @param cause 原始内存溢出错误
   * @return 内存溢出异常实例
   */
  public static ExcelReadException outOfMemory(Throwable cause) {
    return new ExcelReadException(
        ExcelExceptionCode.READ_OUT_OF_MEMORY, "Excel文件过大导致内存溢出, 请限制读取行数或分批处理", cause);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("ExcelReadException");
    String code = getCode();
    if (code != null) {
      sb.append(" [").append(code).append("]");
    }
    sb.append(": ").append(getMessage());
    if (rowNumber != null) {
      sb.append(" [行号=").append(rowNumber);
      if (columnNumber != null) {
        sb.append(", 列号=").append(columnNumber);
      }
      sb.append("]");
    }
    return sb.toString();
  }
}
