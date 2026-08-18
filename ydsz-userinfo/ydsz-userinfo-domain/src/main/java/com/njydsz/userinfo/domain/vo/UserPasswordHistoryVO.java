package com.njydsz.userinfo.domain.vo;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 密码历史 VO，用于 Controller 返回，不包含 deleted 等内部维护字段。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class UserPasswordHistoryVO {

  /** 记录唯一标识 */
  private String id;

  /** 用户 ID */
  private String userId;

  /** BCrypt 加密后的历史密码哈希 */
  private String passwordHash;

  /** 创建时间（该密码被设置的日期） */
  private LocalDateTime createdAt;
}
