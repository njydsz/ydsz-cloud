package com.njydsz.userinfo.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 账号封禁信息 VO。
 *
 * <p>供管理端 API 返回，展示封禁类型、原因、到期时间、操作人等信息。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class BanInfoVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 当前是否处于封禁状态 */
  private boolean banned;

  /** 封禁类型（TEMPORARY/PERMANENT），未封禁时为 null */
  private String banType;

  /** 封禁原因 */
  private String banReason;

  /** 封禁到期时间（永久封禁为 null） */
  private LocalDateTime banExpireAt;

  /** 操作人标识 */
  private String bannedBy;

  /** 封禁操作时间 */
  private LocalDateTime bannedAt;
}
