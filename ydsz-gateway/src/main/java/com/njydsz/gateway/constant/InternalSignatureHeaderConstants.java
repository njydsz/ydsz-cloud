package com.njydsz.gateway.constant;

/**
 * HTTP 请求头常量 — 网关内部签名。
 *
 * <p>定义下游服务与网关之间内部签名校验相关的 HTTP Header 名称常量。
 *
 * @author ydsz-team
 * @since 1.11.0
 */
public final class InternalSignatureHeaderConstants {

    private InternalSignatureHeaderConstants() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** 网关内部签名 HTTP 头。HMAC-SHA256 签名值。 */
    public static final String X_INTERNAL_SIG = "X-Internal-Sig";

    /** 网关内部签名时间戳 HTTP 头（毫秒）。与 X_INTERNAL_SIG 配套使用。 */
    public static final String X_INTERNAL_TS = "X-Internal-Ts";

    /** 网关内部签名 nonce HTTP 头（防重放）。 */
    public static final String X_INTERNAL_NONCE = "X-Internal-Nonce";
}
