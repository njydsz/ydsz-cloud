package com.njydsz.userinfo.server.auth;

import lombok.Data;

/**
 * 社交登录结果 VO。
 *
 * <p>社交登录回调成功后返回，包含访问令牌和用户信息。结构参考 {@code LoginVO}，
 * 专用于社交登录场景，额外携带来源平台信息。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class SocialLoginVO {

  /** 访问令牌（Access Token），用于后续 API 请求的 Bearer 认证 */
  private String accessToken;

  /** 刷新令牌（Refresh Token），用于在 accessToken 过期后换取新的令牌 */
  private String refreshToken;

  /** 令牌类型，固定为 {@code Bearer} */
  private String tokenType;

  /** 访问令牌有效期（秒） */
  private long expiresIn;

  /** 授权范围 */
  private String scope;

  /** 登录来源平台（WECHAT/DINGTALK/GITHUB 等） */
  private String platform;

  /** 社交用户信息 */
  private SocialUserInfoVO socialUserInfo;

  /**
   * 社交用户信息。
   */
  @Data
  public static class SocialUserInfoVO {

    /** 平台用户唯一标识 */
    private String openId;

    /** 用户昵称 */
    private String nickname;

    /** 头像 URL */
    private String avatar;

    /** 用户邮箱（可为 null） */
    private String email;
  }
}
