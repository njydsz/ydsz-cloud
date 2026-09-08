package com.njydsz.userinfo.server.auth;

import lombok.Data;

import com.njydsz.userinfo.domain.enums.SocialPlatformLinkingStrategy;

/**
 * 社交登录结果 VO。
 *
 * <p>社交登录回调成功后返回，包含访问令牌和用户信息。结构参考 {@code LoginVO}，
 * 专用于社交登录场景，额外携带来源平台信息和绑定状态。
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

  /**
   * 绑定状态标识。
   *
   * <ul>
   *   <li>{@code BIND_OK} — 已绑定且 Token 已签发</li>
   *   <li>{@code PENDING_BIND} — 社交用户未绑定，需前端引导绑定</li>
   *   <li>{@code AUTO_BOUND} — 自动创建用户并绑定完成（AUTO_BIND 策略）</li>
   * </ul>
   */
  private String bindStatus;

  /** 当前平台配置的绑定策略 */
  private SocialPlatformLinkingStrategy linkingStrategy;

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
