package com.remisoft.common.core.constant.header;

/**
 * 认证与身份相关 HTTP 请求头常量
 *
 * <p>定义身份认证、用户标识、设备标识、服务等基础认证层面的 header。
 *
 * <p>对应模块：remi-common-web（解析）、remi-common-auth（写入）
 *
 * @author remi-team
 * @since 1.8.0
 */
public final class AuthHeaders {

    private AuthHeaders() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 登录访问令牌
     *
     * <p>用户登录后颁发的 AccessToken，用于身份认证与用户信息加载。
     */
    public static final String X_ACCESS_TOKEN = "X-Access-Token";

    /**
     * 用户系统语言
     *
     * <p>格式示例：{@code zh-CN}、{@code en-US}。
     */
    public static final String X_USER_LANGUAGE = "X-User-Language";

    /**
     * 用户设备唯一标识
     *
     * <p>用于设备追踪与多端识别。
     */
    public static final String X_DISTINCT_ID = "X-Distinct-Id";

    /**
     * 身份类型
     *
     * <p>用于区分公司用户、访客用户等身份类型。
     */
    public static final String X_IDENTITY_TYPE = "X-Identity-Type";

    /**
     * 服务类型
     *
     * <p>用于区分请求来源服务类型（WEB_SERVICE / APP_SERVICE 等）。
     */
    public static final String X_SERVICE_TYPE = "X-Service-Type";

    /**
     * 幂等键
     *
     * <p>客户端通过此 Header 传递幂等键，服务端据此保证操作幂等性。
     * 参考 Stripe API 的 Idempotency-Key 设计。
     */
    public static final String IDEMPOTENCY_KEY = "X-Idempotency-Key";
}
