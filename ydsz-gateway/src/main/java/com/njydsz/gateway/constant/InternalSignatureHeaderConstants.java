package com.njydsz.gateway.constant;

/**
 * HTTP 请求头常量 — 网关内部签名。
 *
 * <p>定义下游服务与网关之间内部签名校验相关的 HTTP Header 名称常量。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class InternalSignatureHeaderConstants {

  private InternalSignatureHeaderConstants() {
    throw new UnsupportedOperationException("Utility class");
  }

  /** 网关内部签名 HTTP 头。HMAC-SHA256 签名值。 */
  /** X_INTERNAL_SIG 常量 */
  public static final String X_INTERNAL_SIG = "X-Internal-Sig";
}
