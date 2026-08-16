package com.njydsz.userinfo.domain.vo;

import lombok.Data;
import com.njydsz.common.safe.sensitive.SensitiveData;
import com.njydsz.common.safe.sensitive.SensitiveType;

/**
 * 登录结果 VO，遵循 OAuth2 Token Response 规范（RFC 6749 §5.1）。
 *
 * <p>登录成功后由 {@code AuthServiceImpl.login()} 组装，
 * 包含访问令牌、刷新令牌和当前登录用户的基本信息。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class LoginVO {

    /** 访问令牌（Access Token），用于后续 API 请求的 Bearer 认证 */
    private String accessToken;
    /** 刷新令牌（Refresh Token），用于在 accessToken 过期后换取新的令牌 */
    private String refreshToken;
    /** 令牌类型，固定为 {@code Bearer} */
    private String tokenType;
    /** 访问令牌有效期（秒），到期前需使用 refreshToken 刷新 */
    private long expiresIn;
    /** 授权范围，如 {@code read write}，空表示全部权限 */
    private String scope;
    /** 当前登录用户的基本信息 */
    private UserInfoVO userInfo;

    /**
     * 当前登录用户的基本信息。
     *
     * <p>仅包含前端首屏渲染所需的最小字段集，
     * 权限列表和详细用户资料通过单独接口获取。
     */
    @Data
    public static class UserInfoVO {
        /** 用户唯一标识 */
        private String userId;
        /** 登录用户名 */
        private String username;
        /** 用户真实姓名 */
        @SensitiveData(SensitiveType.CHINESE_NAME)
        private String realName;
        /** 主角色编码，用于前端权限路由判断 */
        private String roleCode;
        /** 主角色名称 */
        private String roleName;
        /** 租户 ID，多租户场景下标识所属租户 */
        private String tenantId;
        /** 用户头像 URL */
        private String avatar;
    }
}
