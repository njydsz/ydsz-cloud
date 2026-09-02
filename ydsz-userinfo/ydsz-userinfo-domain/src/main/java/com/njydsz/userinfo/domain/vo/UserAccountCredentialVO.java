package com.njydsz.userinfo.domain.vo;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 用户账号认证凭据 VO
 *
 * <p>专用于认证场景，包含密码哈希、锁定状态等敏感字段。
 * 由 {@code UserInfoConverter#entityToCredentialVO(UserAccount)} 从 {@code UserAccount} 转换而来。
 *
 * <p><b>安全注意：</b>本 VO 包含密码哈希，仅在认证服务内部使用，禁止返回给前端或跨服务传输。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class UserAccountCredentialVO {

  /** 用户唯一标识 */
  private String id;

  /** 登录用户名 */
  private String username;

  /** 密码哈希（BCrypt） */
  private String password;

  /** 账号状态：1-启用、0-停用 */
  private Integer status;

  /** 连续登录失败次数 */
  private Integer loginFailCount;

  /** 锁定截止时间（未锁定为 null） */
  private LocalDateTime lockedUntil;

  /** 租户 ID */
  private String tenantId;

  /** 手机号码 */
  @com.njydsz.common.safe.sensitive.SensitiveData(com.njydsz.common.safe.sensitive.SensitiveType.PHONE)
  private String phone;

  /** 邮箱地址 */
  @com.njydsz.common.safe.sensitive.SensitiveData(com.njydsz.common.safe.sensitive.SensitiveType.EMAIL)
  private String email;

  /** 封禁类型（TEMPORARY/PERMANENT/null），null 表示未封禁 */
  private String banType;

  /** 封禁原因 */
  private String banReason;

  /** 封禁到期时间（临时封禁使用，永久封禁为 null） */
  private LocalDateTime banExpireAt;

  /** 封禁操作人标识 */
  private String bannedBy;

  /** 封禁操作时间 */
  private LocalDateTime bannedAt;

  /**
   * 判断账号是否被锁定。
   *
   * @return true 表示账号处于锁定状态
   */
  public boolean isLocked() {
    return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
  }
}
