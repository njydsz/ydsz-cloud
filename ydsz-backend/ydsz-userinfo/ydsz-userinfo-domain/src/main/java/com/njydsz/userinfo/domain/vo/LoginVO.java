package com.njydsz.userinfo.domain.vo;

import lombok.Data;

/**
 * 登录结果 VO（符合 OAuth2 Token Response 规范）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class LoginVO {

    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private long expiresIn;
    private String scope;
    private UserInfoVO userInfo;

    /**
     * 用户基本信息。
     */
    @Data
    public static class UserInfoVO {
        private String userId;
        private String username;
        private String realName;
        private String roleCode;
        private String roleName;
        private String tenantId;
        private String avatar;
    }
}
