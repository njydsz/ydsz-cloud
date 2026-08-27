package com.njydsz.auth.domain.vo;

/**
 * OAuth2 授权类型枚举。
 *
 * <p>定义支持的 OAuth2 授权类型（RFC 6749）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum GrantType {

    /** 授权码模式（RFC 6749 §4.1） */
    AUTHORIZATION_CODE("authorization_code"),

    /** 客户端凭证模式（RFC 6749 §4.4） */
    CLIENT_CREDENTIALS("client_credentials"),

    /** 刷新令牌（RFC 6749 §6） */
    REFRESH_TOKEN("refresh_token"),

    /** 密码模式（RFC 6749 §4.3）— 不推荐，仅兼容存量 */
    PASSWORD("password");

    private final String value;

    GrantType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * 根据字符串值解析枚举。
     *
     * @param value 授权类型字符串值
     * @return 对应的枚举值；未匹配返回 null
     */
    public static GrantType fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (GrantType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return null;
    }
}
