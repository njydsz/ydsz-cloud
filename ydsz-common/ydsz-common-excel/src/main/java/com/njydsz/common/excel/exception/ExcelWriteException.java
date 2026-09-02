package com.njydsz.common.excel.exception;

import com.njydsz.common.util.message.MessageUtils;

/**
 * Excel 写入异常类
 *
 * <p>封装 Excel 文件写入过程中的各类错误，包括文件创建失败、 样式配置错误、数据写入错误等场景。自包含的异常体系，不依赖全局异常处理模块。
 *
 * <h3>使用示例</h3>
 *
 * <pre>{@code
 * try {
 *     ExcelFacade.write("output.xlsx", User.class)
 *         .sheet("用户数据")
 *         .headRowNumber(1)
 *         .doWrite(userList);
 * } catch (ExcelWriteException e) {
 *     LOG.error("写入失败 - 错误码: {}, 消息: {}", e.getCode(), e.getMessage(), e);
 *     // 获取异常码枚举
 *     ExcelExceptionCode code = e.getExceptionCode();
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see ExcelException
 * @see ExcelExceptionCode
 */
public class ExcelWriteException extends ExcelException {

  private static final long serialVersionUID = 1L;

  /** 写入失败的数据对象索引 */
  private Integer dataIndex;

  /** 写入失败的字段名 */
  private String fieldName;

  public ExcelWriteException() {
    super();
  }

  public ExcelWriteException(String message) {
    super(message);
  }

  public ExcelWriteException(String message, Throwable cause) {
    super(message, cause);
  }

  public ExcelWriteException(ExcelExceptionCode exceptionCode) {
    super(exceptionCode);
  }

  public ExcelWriteException(ExcelExceptionCode exceptionCode, String message) {
    super(exceptionCode, message);
  }

  public ExcelWriteException(ExcelExceptionCode exceptionCode, String message, Throwable cause) {
    super(exceptionCode, message, cause);
  }

  public Integer getDataIndex() {
    return dataIndex;
  }

  public void setDataIndex(Integer dataIndex) {
    this.dataIndex = dataIndex;
  }

  public String getFieldName() {
    return fieldName;
  }

  public void setFieldName(String fieldName) {
    this.fieldName = fieldName;
  }

  /**
   * 创建文件访问异常
   *
   * @param filePath 文件路径
   * @param reason 原因
   * @return 携带 {@code WRITE_FILE_ACCESS_FAILED} 错误码的异常实例，不会为 {@code null}；
   *     上下文参数依次为 {@code filePath}、{@code reason}
   */
  public static ExcelWriteException fileAccessFailed(String filePath, String reason) {
    String message = MessageUtils.getMessage(
        "excel.write.fileAccessFailed.detail", new Object[] {reason}, "文件访问失败: " + reason);
    ExcelWriteException ex =
        new ExcelWriteException(ExcelExceptionCode.WRITE_FILE_ACCESS_FAILED, message);
    ex.setContext(new Object[] {filePath, reason});
    return ex;
  }

  /**
   * 创建磁盘空间不足异常
   *
   * @param filePath 文件路径
   * @param requiredSpace 所需空间
   * @param availableSpace 可用空间
   * @return 携带 {@code WRITE_INSUFFICIENT_SPACE} 错误码的异常实例，不会为 {@code null}；
   *     上下文参数依次为 {@code filePath}、{@code requiredSpace}、{@code availableSpace}（单位：字节）
   */
  public static ExcelWriteException insufficientSpace(
      String filePath, long requiredSpace, long availableSpace) {
    String message = MessageUtils.getMessage(
        "excel.write.insufficientSpace.detail",
        new Object[] {requiredSpace, availableSpace},
        String.format("磁盘空间不足: 所需=%d字节, 可用=%d字节", requiredSpace, availableSpace));
    ExcelWriteException ex =
        new ExcelWriteException(ExcelExceptionCode.WRITE_INSUFFICIENT_SPACE, message);
    ex.setContext(new Object[] {filePath, requiredSpace, availableSpace});
    return ex;
  }

  /**
   * 创建注解配置错误异常
   *
   * @param clazz 目标类型
   * @param fieldName 字段名
   * @param reason 原因
   * @return 携带 {@code WRITE_ANNOTATION_ERROR} 错误码的异常实例，不会为 {@code null}；
   *     出错字段名已回填，上下文参数使用 {@code clazz} 的全限定名而非简单名
   */
  public static ExcelWriteException invalidAnnotation(
      Class<?> clazz, String fieldName, String reason) {
    String message = MessageUtils.getMessage(
        "excel.write.annotationError.detail",
        new Object[] {clazz.getSimpleName(), fieldName, reason},
        String.format("注解配置错误 [%s.%s]: %s", clazz.getSimpleName(), fieldName, reason));
    ExcelWriteException ex =
        new ExcelWriteException(ExcelExceptionCode.WRITE_ANNOTATION_ERROR, message);
    ex.setFieldName(fieldName);
    ex.setContext(new Object[] {clazz.getName(), fieldName, reason});
    return ex;
  }

  /**
   * 创建数据写入异常
   *
   * @param index 索引
   * @param fieldName 字段名
   * @param value 值
   * @param cause 原因
   * @return 携带 {@code WRITE_DATA_FAILED} 错误码的异常实例，不会为 {@code null}；
   *     数据索引与字段名已回填，底层写入异常被保留为 {@code cause}
   */
  public static ExcelWriteException dataWriteFailed(
      int index, String fieldName, Object value, Throwable cause) {
    String message = MessageUtils.getMessage(
        "excel.write.dataFailed.detail",
        new Object[] {index, fieldName, value},
        String.format("数据写入失败: 索引=%d, 字段=%s, 值=%s", index, fieldName, value));
    ExcelWriteException ex =
        new ExcelWriteException(ExcelExceptionCode.WRITE_DATA_FAILED, message, cause);
    ex.setDataIndex(index);
    ex.setFieldName(fieldName);
    return ex;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("ExcelWriteException");
    String code = getCode();
    if (code != null) {
      sb.append(" [").append(code).append("]");
    }
    sb.append(": ").append(getMessage());
    if (dataIndex != null) {
      sb.append(" [数据索引=").append(dataIndex);
      if (fieldName != null) {
        sb.append(", 字段=").append(fieldName);
      }
      sb.append("]");
    }
    return sb.toString();
  }
}
