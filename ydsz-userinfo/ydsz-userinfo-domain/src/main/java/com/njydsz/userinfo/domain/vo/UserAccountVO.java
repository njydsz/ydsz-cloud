package com.njydsz.userinfo.domain.vo;

import java.time.LocalDateTime;

import lombok.Data;

import com.njydsz.common.safe.sensitive.SensitiveData;
import com.njydsz.common.safe.sensitive.SensitiveType;

/**
 * 用户账号 VO，用于 Controller 返回，不包含密码、盐值等敏感字段。
 *
 * <p>由 {@code UserInfoConverter.entityToVO()} 从 {@code UserAccount} 实体转换而来， 供前端展示和跨模块查询使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class UserAccountVO {

  /** 启用状态对应的整数值（1=启用）。 */
  private static final int ENABLED_INT_VALUE = 1;

  /** 禁用状态对应的整数值（0=禁用）。 */
  private static final int DISABLED_INT_VALUE = 0;

  /** 用户唯一标识 */
  private String id;

  /** 登录用户名 */
  private String username;

  /** 真实姓名 */
  @SensitiveData(SensitiveType.CHINESE_NAME)
  private String realName;

  /** 手机号码 */
  @SensitiveData(SensitiveType.PHONE)
  private String phone;

  /** 邮箱地址 */
  @SensitiveData(SensitiveType.EMAIL)
  private String email;

  /** 头像 URL */
  private String avatar;

  /** 账号状态：1-启用、0-停用 */
  private Integer status;

  /** 用户类型，如 SYS（系统）、BIZ（业务） */
  private String userType;

  /** 所属公司 ID */
  private String companyId;

  /** 所属部门 ID（关联 ydsz_org_department.id，支持 dept: 审批人展开） */
  private String deptId;

  /** 直属上级用户 ID（关联 ydsz_acct_user.id，支持 leader: 审批人展开） */
  private String leaderId;

  /** 岗位编码（如 PM/DEV/QA/SA，支持 position: 审批人展开） */
  private String positionCode;

  /** 租户 ID */
  private String tenantId;

  /** 最后登录时间 */
  private LocalDateTime lastLoginAt;

  /** 最后登录 IP */
  private String lastLoginIp;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新时间 */
  private LocalDateTime updatedAt;

  /** 登录失败次数 */
  private Integer loginFailCount;

  /** 锁定截止时间（未锁定为 null，用于自助解锁功能） */
  private LocalDateTime lockedUntil;

  /**
   * 乐观锁版本号（P1-6）。
   *
   * <p>由查询响应返回给前端，前端编辑时原样回传，服务端据此做乐观锁冲突检测。
   */
  private Integer revision;

  // ==================== 封禁字段 ====================

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
   * 检查当前是否处于封禁状态（懒检查）。
   *
   * <p>临时封禁过期自动返回 false。永久封禁始终返回 true。
   *
   * @return true 表示当前处于封禁状态
   */
  public boolean isBanned() {
    if (this.banType == null) {
      return false;
    }
    // PERMANENT: 永久封禁始终返回 true
    if ("PERMANENT".equals(this.banType)) {
      return true;
    }
    // TEMPORARY: 检查是否过期
    return this.banExpireAt != null && this.banExpireAt.isAfter(LocalDateTime.now());
  }
}
