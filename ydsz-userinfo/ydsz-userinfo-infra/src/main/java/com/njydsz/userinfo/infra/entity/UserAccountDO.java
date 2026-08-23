package com.njydsz.userinfo.infra.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;
import com.njydsz.common.jdbc.handler.IntegerStringTypeHandler;
import com.njydsz.common.safe.encrypt.EncryptField;
import com.njydsz.common.safe.encrypt.EncryptTypeHandler;
import com.njydsz.userinfo.domain.enums.BanType;
import com.njydsz.userinfo.domain.enums.EnableStatusEnum;
import com.njydsz.userinfo.domain.enums.UserLifecycleStatusEnum;
import com.njydsz.userinfo.domain.vo.BanInfoVO;

/**
 * 用户账号实体
 *
 * <p>对应数据库表 {@code ydsz_user_account}，存储系统用户账号信息。是用户中心服务的核心实体，被各业务模块通过 Feign 远程查询。
 *
 * <p><b>安全敏感字段：</b>
 *
 * <ul>
 *   <li>{@code password}：BCrypt 加密（cost=10），禁止明文存储与返回
 *   <li>{@code realName}：AES-256-GCM 字段级加密（{@code @EncryptField}），密文存储，明文仅在内存中出现
 *   <li>{@code phone} / {@code email}：敏感信息，返回时脱敏
 *   <li>{@code loginFailCount} / {@code lockedUntil}：登录失败保护，达到阈值自动锁定
 * </ul>
 *
 * <p><b>状态字段说明：</b>DB 列使用整数（0=禁用, 1=启用，历史遗留），通过 {@link IntegerStringTypeHandler} 自动转换为 String。
 * 业务代码通过 {@link #getStatusEnum()} / {@link #setStatusEnum(EnableStatusEnum)} 使用枚举类型，
 * {@link EnableStatusEnum#parse(String)} 兼容两种格式。
 *
 * <p><b>审批人展开支持：</b>
 *
 * <ul>
 *   <li>{@code deptId}：所属部门，支持 {@code dept:xxx} 审批人展开
 *   <li>{@code leaderId}：直属上级用户 ID，支持 {@code leader:xxx} 展开
 *   <li>{@code positionCode}：岗位编码（PM/DEV/QA/SA），支持 {@code position:xxx} 展开
 * </ul>
 *
 * <p><b>索引设计：</b>唯一索引 {@code uk_username}（{@code username}），普通索引 {@code idx_phone}、{@code idx_dept_id}。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_user_account")
public class UserAccountDO extends MpBaseEntity<String> {

  /** 登录用户名（全局唯一） */
  private String username;

  /** 登录密码（BCrypt 加密，禁止明文存储/返回） */
  private String password;

  /**
   * 真实姓名（AES-256-GCM 加密存储）
   *
   * <p>使用 common-safe 的 {@link EncryptField} + {@link EncryptTypeHandler} 实现字段级加密，
   * 明文仅在应用内存中出现，数据库存储密文。解密由 TypeHandler 自动完成，业务代码无需感知。
   *
   * <p><b>注意：</b>加密字段不可用于 WHERE/LIKE 条件查询（AES-GCM 随机 IV 导致明文相同密文不同）， 本字段仅用于 SELECT 展示，不参与条件检索。
   *
   * @see EncryptField
   * @see EncryptTypeHandler
   */
  @TableField(typeHandler = EncryptTypeHandler.class)
  @EncryptField
  private String realName;

  /** 手机号（用于短信验证/找回密码，脱敏返回） */
  private String phone;

  /** 邮箱（用于通知/找回密码，脱敏返回） */
  private String email;

  /** 头像 URL */
  private String avatar;

  /**
   * 账号状态（DB 整数列 0/1，通过 {@link IntegerStringTypeHandler} 自动转换为 String）。
   *
   * <p>业务代码建议通过 {@link #getStatusEnum()} / {@link #setStatusEnum(EnableStatusEnum)} 使用枚举类型。
   */
  @TableField(value = "status", typeHandler = IntegerStringTypeHandler.class)
  private String status;

  /** 用户类型（PLATFORM/ISV/TENANT_ADMIN/REGULAR 等） */
  private String userType;

  /** 所属公司 ID（关联 {@code ydsz_company.id}） */
  private String companyId;

  /** 最近登录时间 */
  private LocalDateTime lastLoginAt;

  /** 最近登录 IP */
  private String lastLoginIp;

  /** 连续登录失败次数（达到阈值触发账号锁定） */
  private Integer loginFailCount;

  /** 账号锁定截止时间（解锁后自动清零 loginFailCount） */
  private LocalDateTime lockedUntil;

  /** 所属部门 ID（关联 ydsz_department.id，支持 dept: 审批人展开） */
  private String deptId;

  /** 直属上级用户 ID（关联 ydsz_user_account.id，支持 leader: 审批人展开） */
  private String leaderId;

  /** 岗位编码（如 PM/DEV/QA/SA，支持 position: 审批人展开） */
  private String positionCode;

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
   * 获取状态枚举。
   *
   * <p>兼容 "0"/"1"（DB 存储）和 "ENABLED"/"DISABLED"（枚举字面量）两种格式。
   *
   * @return 状态枚举，无法解析时返回 null
   */
  public EnableStatusEnum getStatusEnum() {
    return EnableStatusEnum.parse(this.status);
  }

  /**
   * 从枚举设置状态。
   *
   * @param statusEnum 状态枚举，为 null 时清除状态
   */
  public void setStatusEnum(EnableStatusEnum statusEnum) {
    if (statusEnum == null) {
      this.status = null;
    } else {
      this.status = statusEnum == EnableStatusEnum.ENABLED ? "1" : "0";
    }
  }

  // ==================== 领域行为（Domain Behavior）====================

  // ==================== 封禁行为 ====================

  /**
   * 封禁账号。
   *
   * <p>充血模型：封禁逻辑封装在实体内部，设置封禁类型、原因、到期时间与操作信息。
   * 临时封禁到达期后自动解除（通过 {@link #isBanned()} 懒检查）。
   *
   * @param type 封禁类型（TEMPORARY/PERMANENT），不可为 null
   * @param reason 封禁原因，不可为空白
   * @param expireAt 封禁到期时间（临时封禁必填，永久封禁传 null）
   * @param operator 操作人标识，不可为空白
   */
  public void ban(BanType type, String reason, LocalDateTime expireAt, String operator) {
    this.banType = type.name();
    this.banReason = reason;
    this.banExpireAt = expireAt;
    this.bannedBy = operator;
    this.bannedAt = LocalDateTime.now();
  }

  /**
   * 解封账号。
   *
   * <p>清空所有封禁字段，恢复到未封禁状态。
   *
   * @param operator 操作人标识
   */
  public void unban(String operator) {
    this.banType = null;
    this.banReason = null;
    this.banExpireAt = null;
    this.bannedBy = operator;
    this.bannedAt = LocalDateTime.now();
  }

  /**
   * 检查当前是否处于封禁状态。
   *
   * <p>临时封禁过期自动解除（懒检查）：过期时将 banType 清除并返回 false。
   * 永久封禁始终返回 true。
   *
   * @return true 表示当前处于封禁状态
   */
  public boolean isBanned() {
    if (this.banType == null) {
      return false;
    }
    BanType type = BanType.valueOf(this.banType);
    if (type == BanType.PERMANENT) {
      return true;
    }
    // TEMPORARY: 检查是否过期
    boolean expired =
        this.banExpireAt != null && !this.banExpireAt.isAfter(LocalDateTime.now());
    if (expired) {
      // 懒清除：临时封禁已到期，自动解除
      this.banType = null;
      this.banReason = null;
      this.banExpireAt = null;
      this.bannedBy = null;
      this.bannedAt = null;
      return false;
    }
    return true;
  }

  /**
   * 转换为封禁信息 VO。
   *
   * @return 封禁信息 VO
   */
  public BanInfoVO toBanInfo() {
    BanInfoVO vo = new BanInfoVO();
    vo.setBanned(isBanned());
    if (this.banType != null) {
      vo.setBanType(this.banType);
    }
    vo.setBanReason(this.banReason);
    vo.setBanExpireAt(this.banExpireAt);
    vo.setBannedBy(this.bannedBy);
    vo.setBannedAt(this.bannedAt);
    return vo;
  }

  /**
   * 激活账号（PENDING → ENABLED）。
   *
   * <p>将状态设为 {@link UserLifecycleStatusEnum#ENABLED}，清除锁定信息。仅当前状态为 {@link UserLifecycleStatusEnum#PENDING} 时允许。
   *
   * @throws IllegalStateException 当前状态不允许激活时抛出
   */
  public void activate() {
    UserLifecycleStatusEnum current = getLifecycleStatus();
    if (current != null) {
      current.requireTransitTo(UserLifecycleStatusEnum.ENABLED);
    }
    setLifecycleStatus(UserLifecycleStatusEnum.ENABLED);
    this.lockedUntil = null;
    this.loginFailCount = 0;
  }

  /**
   * 暂停账号（ENABLED → SUSPENDED）。
   *
   * <p>将状态设为 {@link UserLifecycleStatusEnum#SUSPENDED}。仅当前状态为 {@link UserLifecycleStatusEnum#ENABLED} 时允许。
   *
   * @throws IllegalStateException 当前状态不允许暂停时抛出
   */
  public void suspend() {
    UserLifecycleStatusEnum current = getLifecycleStatus();
    if (current != null) {
      current.requireTransitTo(UserLifecycleStatusEnum.SUSPENDED);
    }
    setLifecycleStatus(UserLifecycleStatusEnum.SUSPENDED);
  }

  /**
   * 恢复账号（SUSPENDED → ENABLED）。
   *
   * <p>将状态设为 {@link UserLifecycleStatusEnum#ENABLED}。仅当前状态为 {@link UserLifecycleStatusEnum#SUSPENDED} 时允许。
   *
   * @throws IllegalStateException 当前状态不允许恢复时抛出
   */
  public void resume() {
    UserLifecycleStatusEnum current = getLifecycleStatus();
    if (current != null) {
      current.requireTransitTo(UserLifecycleStatusEnum.ENABLED);
    }
    setLifecycleStatus(UserLifecycleStatusEnum.ENABLED);
    this.lockedUntil = null;
    this.loginFailCount = 0;
  }

  /**
   * 离职处理（→ RESIGNED）。
   *
   * <p>将状态设为 {@link UserLifecycleStatusEnum#RESIGNED}（终态）。从 ENABLED 或 SUSPENDED 状态均可流转。
   *
   * @throws IllegalStateException 当前状态不允许离职时抛出
   */
  public void resign() {
    UserLifecycleStatusEnum current = getLifecycleStatus();
    if (current != null) {
      current.requireTransitTo(UserLifecycleStatusEnum.RESIGNED);
    }
    setLifecycleStatus(UserLifecycleStatusEnum.RESIGNED);
  }

  /**
   * 启用账号。
   *
   * <p>将状态设为 {@link EnableStatusEnum#ENABLED}，清除锁定信息。
   *
   * @deprecated 使用 {@link #activate()} 替代，提供更严格的状态流转校验
   */
  @Deprecated
  public void enable() {
    setStatusEnum(EnableStatusEnum.ENABLED);
    this.lockedUntil = null;
    this.loginFailCount = 0;
  }

  /**
   * 禁用账号（→ DISABLED）。
   *
   * <p>将状态设为 {@link EnableStatusEnum#DISABLED} 或 {@link UserLifecycleStatusEnum#DISABLED}。
   * 支持从 ENABLED 或 SUSPENDED 状态流转。
   *
   * @throws IllegalStateException 当前状态不允许禁用时抛出
   */
  public void disable() {
    UserLifecycleStatusEnum current = getLifecycleStatus();
    if (current != null) {
      current.requireTransitTo(UserLifecycleStatusEnum.DISABLED);
    }
    setLifecycleStatus(UserLifecycleStatusEnum.DISABLED);
  }

  /**
   * 解锁账号。
   *
   * <p>清除锁定截止时间、重置登录失败计数。仅当账号已锁定时调用（由 Service 层判断）。
   */
  public void unlock() {
    this.lockedUntil = null;
    this.loginFailCount = 0;
  }

  /**
   * 判断当前是否处于锁定状态。
   *
   * <p>当 {@link #lockedUntil} 非空且晚于当前时间时，账号处于锁定状态。锁定过期后自动解除（无需显式调用 {@link #unlock()}）。
   *
   * @return true 表示当前被锁定
   */
  public boolean isLocked() {
    return this.lockedUntil != null && this.lockedUntil.isAfter(LocalDateTime.now());
  }

  /**
   * 获取生命周期状态枚举。
   *
   * <p>兼容 "0"/"1"（DB 存储）和 "ENABLED"/"DISABLED"/"PENDING"/"SUSPENDED"/"RESIGNED" 格式。
   *
   * @return 生命周期状态枚举，无法解析时返回 null
   */
  public UserLifecycleStatusEnum getLifecycleStatus() {
    return UserLifecycleStatusEnum.parse(this.status);
  }

  /**
   * 设置生命周期状态。
   *
   * @param status 生命周期状态枚举，为 null 时清除状态
   */
  public void setLifecycleStatus(UserLifecycleStatusEnum status) {
    if (status == null) {
      this.status = null;
      return;
    }
    switch (status) {
      case ENABLED -> this.status = "1";
      case DISABLED -> this.status = "0";
      default -> this.status = status.name();
    }
  }

  /**
   * 检查是否允许登录。
   *
   * <p>仅 {@link UserLifecycleStatusEnum#ENABLED} 状态且未锁定时允许登录。
   *
   * @return true 表示允许登录
   */
  public boolean canLogin() {
    return getLifecycleStatus() == UserLifecycleStatusEnum.ENABLED && !isLocked();
  }

  /**
   * 判断当前是否允许认证（登录）。
   *
   * <p>前置条件：账号存在、已启用、未锁定。
   *
   * <p><b>已废弃：</b>使用 {@link #canLogin()} 替代，语义更清晰。
   *
   * @return true 表示允许尝试认证
   */
  @Deprecated
  public boolean canAuthenticate() {
    return getStatusEnum() == EnableStatusEnum.ENABLED && !isLocked();
  }

  /**
   * 记录一次登录失败。
   *
   * <p>递增登录失败计数；若达到 {@code maxFailCount} 阈值，自动设置锁定时间戳（当前时间 + {@code lockDurationMinutes} 分钟）。
   *
   * @param maxLoginFailCount 触发锁定的最大失败次数（正整数）
   * @param lockDurationMinutes 锁定时长（分钟，正整数）
   */
  public void recordLoginFailure(int maxLoginFailCount, int lockDurationMinutes) {
    int current = this.loginFailCount != null ? this.loginFailCount : 0;
    this.loginFailCount = current + 1;
    if (this.loginFailCount >= maxLoginFailCount) {
      this.lockedUntil = LocalDateTime.now().plusMinutes(lockDurationMinutes);
    }
  }

  /**
   * 记录一次登录成功。
   *
   * <p>重置登录失败计数、清除锁定截止时间、更新最近登录 IP。由 Service 层在认证通过后调用。
   *
   * @param loginIp 登录来源 IP
   */
  public void recordLoginSuccess(String loginIp) {
    this.loginFailCount = 0;
    this.lockedUntil = null;
    this.lastLoginAt = LocalDateTime.now();
    this.lastLoginIp = loginIp;
  }
}
