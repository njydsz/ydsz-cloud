package com.njydsz.userinfo.domain.vo;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 社交账号绑定信息 VO。
 *
 * <p>用于返回用户社交账号绑定列表，不包含 access_token、refresh_token 等敏感字段。
 * 由 {@code SocialAccountRepository} 从 DO 转换后返回。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class SocialAccountVO {

  /** 绑定记录 ID */
  private String id;

  /** 关联用户 ID */
  private String userId;

  /** 平台标识（WECHAT/DINGTALK/ENTERPRISE_WECHAT/GITHUB） */
  private String platform;

  /** 平台用户唯一标识 */
  private String openId;

  /** 平台统一应用标识（可为 null） */
  private String unionId;

  /** 社交昵称 */
  private String nickname;

  /** 头像 URL */
  private String avatarUrl;

  /** 令牌过期时间（可为 null） */
  private LocalDateTime expiresAt;

  /** 绑定时间 */
  private LocalDateTime createdAt;

  /** 更新时间 */
  private LocalDateTime updatedAt;
}
