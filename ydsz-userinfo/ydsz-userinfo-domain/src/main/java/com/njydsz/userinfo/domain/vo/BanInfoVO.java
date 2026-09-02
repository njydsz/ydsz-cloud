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
 * <p><b>字段语义：</b>
 *
 * <ul>
 *   <li>{@code banned}：当前是否处于封禁状态（临时封禁过期后自动为 false）
 *   <li>{@code banType}：封禁类型（TEMPORARY/PERMANENT），未封禁时为 null
 *   <li>{@code banReason}：封禁原因
 *   <li>{@code banExpireAt}：封禁到期时间（永久封禁为 null）
 *   <li>{@code bannedBy}：操作人标识
 *   <li>{@code bannedAt}：封禁操作时间
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
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
