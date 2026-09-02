package com.njydsz.userinfo.domain.vo;

import java.io.Serializable;

import lombok.Data;

/**
 * WebAuthn 挑战码视图对象
 *
 * <p>注册和认证时生成的临时挑战码（Challenge），用于防止重放攻击。
 * 挑战码存储在 Redis 中，带 TTL 过期。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class WebAuthnChallengeVO implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 挑战码（Base64URL 编码的随机字节） */
  private String challenge;

  /** 用户 ID（认证流程中可为 null，注册流程中必填） */
  private String userId;

  /** 挑战类型（REGISTER / AUTHENTICATE） */
  private String type;

  /** 创建时间戳 */
  private long createdAt;

  /** 有效期（秒） */
  private long ttlSeconds;
}
