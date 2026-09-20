package com.njydsz.common.docs.exception;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.exception.enums.ExceptionCode;

/**
 * 文档处理异常
 *
 * <p>文档解析、预处理、安全扫描、PII 检测、脱敏、水印等操作失败时抛出。
 *
 * <p>支持两种消息模式：
 * <ul>
 *   <li><b>i18n 透传</b>：使用 {@link #DocumentException(ExceptionCode, Object...)} 将动态值作为占位参数传入，
 *       由全局异常处理器按请求 Locale 解析为对应语言文案</li>
 *   <li><b>自定义消息</b>：使用 {@link #DocumentException(ExceptionCode, String)} 显式指定消息文本，
 *       跳过 i18n 解析（仅用于异常链追溯等无需国际化的场景）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class DocumentException extends BusinessException {

  private static final long serialVersionUID = 1L;

  /**
   * 构造文档处理异常
   *
   * @param exceptionCode 异常码
   */
  public DocumentException(ExceptionCode exceptionCode) {
    super(exceptionCode);
  }

  /**
   * 构造文档处理异常（带 i18n 占位参数）
   *
   * <p>异常码对应的 i18n key 可以使用 {@code {0}}、{@code {1}} 等占位符， 运行时由 {@link
   * org.springframework.context.MessageSource} 按当前 Locale 格式化。 例如 key 为 {@code
   * doc.format.unsupported=不支持的目标格式: {0}}， 则以 {@code new DocumentException(CODE, "xyz")}
   * 抛出时，中文用户看到 {@code 不支持的目标格式: xyz}。
   *
   * @param exceptionCode 异常码
   * @param arguments 填充到 i18n 占位符的参数，可为空
   */
  public DocumentException(ExceptionCode exceptionCode, Object... arguments) {
    super(exceptionCode);
    if (arguments != null && arguments.length > 0) {
      this.params = arguments;
      this.messageParams = arguments;
      invalidateMessageCache();
    }
  }

  /**
   * 构造文档处理异常（带原因）
   *
   * @param exceptionCode 异常码
   * @param cause 导致此异常的原始原因
   */
  public DocumentException(ExceptionCode exceptionCode, Throwable cause) {
    super(exceptionCode, cause);
  }

  /**
   * 构造文档处理异常（带自定义消息）
   *
   * <p>设置后会跳过 i18n 解析直接返回该文本。仅在异常链追溯、 包装第三方异常等无需国际化的场景下使用。
   *
   * @param exceptionCode 异常码
   * @param message 自定义异常消息
   */
  public DocumentException(ExceptionCode exceptionCode, String message) {
    super(exceptionCode);
    setMessage(message);
  }

  /**
   * 构造文档处理异常（带自定义消息和原因）
   *
   * <p>设置后会跳过 i18n 解析直接返回该文本。仅在异常链追溯、 包装第三方异常等无需国际化的场景下使用。
   *
   * @param exceptionCode 异常码
   * @param message 自定义异常消息
   * @param cause 导致此异常的原始原因
   */
  public DocumentException(ExceptionCode exceptionCode, String message, Throwable cause) {
    super(exceptionCode, cause);
    setMessage(message);
  }
}
