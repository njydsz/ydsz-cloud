package com.njydsz.common.core.constant;

/**
 * Token 相关常量类。
 *
 * <p>定义 JWT/OAuth2 Token 的键名常量，供 auth / util 等模块引用。</p>
 *
 * <p>标准 HTTP 头常量已统一收口于 {@link HeaderConstants}，
 * 本模块仅保留 Token 特有的业务常量。</p>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class TokenConstants {

    private TokenConstants() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** 令牌自定义标识键名（标准 HTTP Authorization 头）。 */
    public static final String AUTHENTICATION = "Authorization";

    /** 补充令牌自定义标识键名（引用 {@link HeaderConstants#X_ACCESS_TOKEN}，与之一致）。 */
    public static final String SUPPLY_AUTHORIZATION = HeaderConstants.X_ACCESS_TOKEN;
}
