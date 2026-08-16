package com.njydsz.common.util.security.crypto;

/**
 * 加密操作异常——所有加密/解密失败都包装为此异常。
 *
 * <p>区分 {@link IllegalArgumentException}（参数错误，编程时可预见） 和 {@link
 * CryptoException}（运行时加密失败，如密文被篡改、算法不可用）。
 *
 * @author ydsz-team
 * @since 3.0.0
 */
public class CryptoException extends RuntimeException {

  public CryptoException(String message) {
    super(message);
  }

  public CryptoException(String message, Throwable cause) {
    super(message, cause);
  }
}
