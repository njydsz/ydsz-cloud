package com.njydsz.common.base.idempotent;

/**
 * 幂等性校验异常。
 *
 * <p>当检测到重复请求时抛出此异常，表示该请求已被处理过。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class IdempotentException extends RuntimeException {

  private final String errorCode;

  /**
   * 构造幂等性异常。
   *
   * @param message 异常消息
   */
  public IdempotentException(String message) {
    super(message);
    this.errorCode = "IDEMPOTENT_REJECT";
  }

  /**
   * 构造幂等性异常。
   *
   * @param errorCode 错误码
   * @param message 异常消息
   */
  public IdempotentException(String errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

  /**
   * 获取错误码。
   *
   * @return 错误码字符串
   */
  public String getErrorCode() {
    return errorCode;
  }
}
