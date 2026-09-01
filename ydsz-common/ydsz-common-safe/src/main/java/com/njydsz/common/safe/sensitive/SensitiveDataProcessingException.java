package com.njydsz.common.safe.sensitive;

/**
 * 敏感数据处理异常
 *
 * <p>当敏感数据脱敏处理遇到不可恢复的错误时抛出（fail-closed 策略）：
 *
 * <ul>
 *   <li>递归深度超限（对象图过深，可能存在未脱敏的深层敏感数据）
 *   <li>对象复制/重建失败且无法安全降级
 * </ul>
 *
 * <p>抛出本异常后由上层（如 {@link SensitiveDataAdvice}）统一兜底为安全空对象， 禁止向调用方返回包含未脱敏数据的原始对象。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class SensitiveDataProcessingException extends RuntimeException {

  /**
   * 以错误消息构造异常
   *
   * @param message 错误消息，需包含上下文信息
   */
  public SensitiveDataProcessingException(String message) {
    super(message);
  }

  /**
   * 以错误消息与根因构造异常
   *
   * @param message 错误消息，需包含上下文信息
   * @param cause 根因异常
   */
  public SensitiveDataProcessingException(String message, Throwable cause) {
    super(message, cause);
  }
}
