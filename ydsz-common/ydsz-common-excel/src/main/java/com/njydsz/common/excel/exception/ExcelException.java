package com.njydsz.common.excel.exception;

import java.util.Locale;

import com.njydsz.common.excel.util.ExcelI18nHelper;

/**
 * Excel 模块基础异常类
 *
 * <p>继承 RuntimeException，绑定 {@link ExcelExceptionCode} 异常码枚举。 自包含的异常体系，不依赖全局异常处理模块。
 *
 * <p><b>国际化</b>：所有接受 {@link ExcelExceptionCode} 的构造器均通过 {@link
 * ExcelI18nHelper#getMessage(String, Object[], String)} 解析 messageKey 为本地化文本后传给 super。 MessageSource
 * 不可用时回退到 messageKey 本身，{@link #getMessage()} 永不返回裸 key。
 *
 * <h3>异常层次</h3>
 *
 * <ul>
 *   <li>{@link ExcelReadException} - Excel 读取异常
 *   <li>{@link ExcelWriteException} - Excel 写入异常
 * </ul>
 *
 * <h3>使用示例</h3>
 *
 * <pre>{@code
 * // 使用异常码枚举（i18n 自动解析）
 * throw new ExcelException(ExcelExceptionCode.CONFIG_INVALID_PARAMETER);
 *
 * // 带自定义消息
 * throw new ExcelException(ExcelExceptionCode.READ_IO_ERROR, "文件读取失败: " + filePath);
 *
 * // 带原始异常
 * throw new ExcelException(ExcelExceptionCode.READ_CONVERSION_FAILED, "类型转换失败", e);
 * }</pre>
 *
 * <h3>全局异常体系桥接</h3>
 *
 * <p>本异常类自包含定义，不直接依赖全局异常体系。 如需接入全局异常处理器，可在调用方通过 instanceof 判断并适配：
 *
 * <pre>{@code
 * // 全局异常处理器中适配示例
 * if (e instanceof ExcelException) {
 *     ExcelExceptionCode code = ((ExcelException) e).getExceptionCode();
 *     // 转换为全局响应...
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see ExcelReadException
 * @see ExcelWriteException
 * @see ExcelExceptionCode
 */
public class ExcelException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** 错误码枚举 */
  private final ExcelExceptionCode exceptionCode;

  /** 国际化消息键（冗余存储，用于 i18n 解析） */
  private final String messageKey;

  /** 错误上下文数据（可选，用于格式化消息） */
  private transient Object[] context;

  /** 构造 Excel 异常（使用通用错误码）。 */
  public ExcelException() {
    super(resolveMessage(ExcelExceptionCode.CONFIG_INVALID_PARAMETER, null));
    this.exceptionCode = ExcelExceptionCode.CONFIG_INVALID_PARAMETER;
    this.messageKey = exceptionCode.getKey();
  }

  /**
   * 构造带错误信息的 Excel 异常。
   *
   * @param message 错误描述
   */
  public ExcelException(String message) {
    super(message);
    this.exceptionCode = ExcelExceptionCode.CONFIG_INVALID_PARAMETER;
    this.messageKey = exceptionCode.getKey();
  }

  /**
   * 构造带错误信息和原因的 Excel 异常。
   *
   * @param message 错误描述
   * @param cause 原始异常
   */
  public ExcelException(String message, Throwable cause) {
    super(message, cause);
    this.exceptionCode = ExcelExceptionCode.CONFIG_INVALID_PARAMETER;
    this.messageKey = exceptionCode.getKey();
  }

  /**
   * 构造带异常码的 Excel 异常。
   *
   * @param exceptionCode 异常码枚举
   */
  public ExcelException(ExcelExceptionCode exceptionCode) {
    super(resolveMessage(exceptionCode, null));
    this.exceptionCode =
        exceptionCode != null ? exceptionCode : ExcelExceptionCode.CONFIG_INVALID_PARAMETER;
    this.messageKey = this.exceptionCode.getKey();
  }

  /**
   * 构造带异常码和自定义消息的 Excel 异常。
   *
   * <p>自定义消息优先于此键的 i18n 解析结果；当 message 为 null 或空时回退到 i18n 解析。
   *
   * @param exceptionCode 异常码枚举
   * @param message 自定义错误描述
   */
  public ExcelException(ExcelExceptionCode exceptionCode, String message) {
    super(message != null && !message.isEmpty() ? message : resolveMessage(exceptionCode, null));
    this.exceptionCode =
        exceptionCode != null ? exceptionCode : ExcelExceptionCode.CONFIG_INVALID_PARAMETER;
    this.messageKey = this.exceptionCode.getKey();
  }

  /**
   * 构造带异常码、自定义消息和原因的 Excel 异常。
   *
   * @param exceptionCode 异常码枚举
   * @param message 自定义错误描述
   * @param cause 原始异常
   */
  public ExcelException(ExcelExceptionCode exceptionCode, String message, Throwable cause) {
    super(message != null && !message.isEmpty() ? message : resolveMessage(exceptionCode, null),
        cause);
    this.exceptionCode =
        exceptionCode != null ? exceptionCode : ExcelExceptionCode.CONFIG_INVALID_PARAMETER;
    this.messageKey = this.exceptionCode.getKey();
  }

  /**
   * 解析异常码 messageKey 为本地化文本。
   *
   * <p>委托 {@link ExcelI18nHelper#getMessage(String, Object[], String)}，MessageSource 不可用时回退 messageKey。
   *
   * @param code 异常码；可为 null
   * @param ctx  上下文参数（可为 null）
   * @return 解析后的本地化消息；解析失败时回退到 messageKey 本身
   */
  private static String resolveMessage(ExcelExceptionCode code, Object[] ctx) {
    if (code == null) {
      return "Excel处理异常";
    }
    return ExcelI18nHelper.getMessage(code.getKey(), ctx, code.getKey());
  }

  /**
   * 获取异常码枚举。
   *
   * @return ExcelExceptionCode 枚举值
   */
  public ExcelExceptionCode getExceptionCode() {
    return exceptionCode;
  }

  /**
   * 获取异常码字符串。
   *
   * @return 异常码
   */
  public String getCode() {
    return exceptionCode != null ? exceptionCode.getCode() : null;
  }

  /**
   * 获取 HTTP 状态码。
   *
   * @return HTTP 状态码，默认为 400
   */
  public int getHttpStatus() {
    return exceptionCode != null ? exceptionCode.getHttpStatus() : 400;
  }

  /**
   * 获取国际化消息键。
   *
   * @return 消息键
   */
  public String getMessageKey() {
    return messageKey;
  }

  /**
   * 获取错误上下文数据。
   *
   * @return 上下文数据数组
   */
  public Object[] getContext() {
    return context;
  }

  /**
   * 设置错误上下文数据。
   *
   * @param context 上下文数据数组
   */
  public void setContext(Object[] context) {
    this.context = context;
  }

  /**
   * 获取格式化的错误消息（使用上下文数据格式化）。
   *
   * @return 格式化后的错误消息
   */
  public String getFormattedMessage() {
    String msg = getMessage();
    if (context == null || context.length == 0 || msg == null) {
      return msg;
    }
    try {
      return String.format(msg, context);
    } catch (Exception e) {
      return msg;
    }
  }
}
