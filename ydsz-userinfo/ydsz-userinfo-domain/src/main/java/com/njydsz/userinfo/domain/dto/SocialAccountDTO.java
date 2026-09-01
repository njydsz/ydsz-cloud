package com.njydsz.userinfo.domain.dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 社交账号绑定统一 DTO（P1-1 CUD 入参）。
 *
 * <p>用于 {@code SocialAccountRepository.save()} 持久化社交账号绑定记录，由 Service 层组装后传入。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class SocialAccountDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 关联用户 ID */
  private String userId;

  /** 平台标识（WECHAT/DINGTALK/ENTERPRISE_WECHAT/GITHUB） */
  private String platform;

  /** 平台用户唯一标识 */
  private String openId;

  /** 平台统一应用标识（可为 null） */
  private String unionId;

  /** 社交昵称（可为 null） */
  private String nickname;

  /** 头像 URL（可为 null） */
  private String avatarUrl;

  /** 访问令牌（明文，由 Repository 加密后存储） */
  private String accessToken;

  /** 刷新令牌（明文，由 Repository 加密后存储，可为 null） */
  private String refreshToken;

  /** 令牌过期时间（可为 null） */
  private LocalDateTime expiresAt;
}
