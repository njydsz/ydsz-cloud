package com.njydsz.common.auth.constant;

/**
 * HTTP 请求头常量 — 网关内部签名。
 *
 * <p>定义网关与下游服务之间内部签名校验相关的 HTTP Header 名称常量。
 *
 * <p>归属说明：P0-3 将该常量由 ydsz-gateway 下沉至 ydsz-common-auth——网关（reactive 栈，
 * 禁止依赖 ydsz-common-web）与下游 Servlet 服务共同依赖本模块，保证签名头名称单一事实源。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class InternalSignatureHeaderConstants {

  private InternalSignatureHeaderConstants() {
    throw new UnsupportedOperationException("Utility class");
  }

  /** 网关内部签名 HTTP 头。HMAC-SHA256 签名值。 */
  public static final String X_INTERNAL_SIG = "X-Internal-Sig";
}
