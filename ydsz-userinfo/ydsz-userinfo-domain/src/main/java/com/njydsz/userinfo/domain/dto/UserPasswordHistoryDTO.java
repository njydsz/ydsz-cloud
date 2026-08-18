package com.njydsz.userinfo.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * 密码历史 DTO。
 *
 * <p>用于保存密码历史记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class UserPasswordHistoryDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 用户 ID */
  private String userId;

  /** BCrypt 加密后的密码哈希 */
  private String passwordHash;
}
