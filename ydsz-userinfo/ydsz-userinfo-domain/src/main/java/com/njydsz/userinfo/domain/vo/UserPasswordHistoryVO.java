package com.njydsz.userinfo.domain.vo;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 密码历史视图对象。
 *
 * <p>记录用户密码变更历史（BCrypt 哈希），用于防止用户在短期内重复使用最近 N 个已用过的密码
 * （由 ydsz.userinfo.password-history-count 配置控制）。
 *
 * <p>不包含 deleted 等内部维护字段。注意：passwordHash 仅用于历史比对，不可逆转为明文。
 *
 * @author ydsz-team
 * @since 26.09.01
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
