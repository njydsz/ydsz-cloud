package com.njydsz.auth.domain.vo;

/**
 * Token 类型枚举。
 *
 * <p>定义 OAuth2 支持的令牌类型。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum TokenType {

    /** 访问令牌 */
    ACCESS_TOKEN("access_token"),

    /** 刷新令牌 */
    REFRESH_TOKEN("refresh_token"),

    /** OIDC ID 令牌 */
    ID_TOKEN("id_token");

    private final String value;

    TokenType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
