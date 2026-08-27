package com.njydsz.auth.domain.vo;

/**
 * 客户端认证方式枚举。
 *
 * <p>定义 OAuth2 客户端支持的身份认证方式（RFC 6749 §2.3）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum ClientAuthMethod {

    /** 基于 HTTP Basic 的客户端认证（client_secret_basic） */
    CLIENT_SECRET_BASIC("client_secret_basic"),

    /** 基于 POST 请求体的客户端认证（client_secret_post） */
    CLIENT_SECRET_POST("client_secret_post"),

    /** 不使用客户端认证（public 客户端） */
    NONE("none"),

    /** 基于 JWT 的客户端认证（private_key_jwt） */
    PRIVATE_KEY_JWT("private_key_jwt"),

    /** 基于 JWT 的客户端认证（client_secret_jwt） */
    CLIENT_SECRET_JWT("client_secret_jwt");

    private final String value;

    ClientAuthMethod(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * 根据字符串值解析枚举。
     *
     * @param value 认证方式字符串值
     * @return 对应的枚举值；未匹配返回 {@link #NONE}
     */
    public static ClientAuthMethod fromValue(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }
        for (ClientAuthMethod method : values()) {
            if (method.value.equals(value)) {
                return method;
            }
        }
        return NONE;
    }
}
