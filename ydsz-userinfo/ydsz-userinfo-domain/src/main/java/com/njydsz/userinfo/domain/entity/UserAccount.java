package com.njydsz.userinfo.domain.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;
import com.njydsz.common.safe.encrypt.EncryptField;
import com.njydsz.common.safe.encrypt.EncryptTypeHandler;
import com.njydsz.userinfo.domain.enums.BanType;
import com.njydsz.userinfo.domain.enums.UserLifeCycleEnum;
import com.njydsz.userinfo.domain.vo.BanInfoVO;

/**
 * 用户账号实体
 *
 * <p>对应数据库表 {@code ydsz_idm_account_user}，存储系统用户账号信息。是用户中心服务的核心实体，被各业务模块通过 Feign 远程查询。
 *
 * <p><b>安全敏感字段：</b>
 *
 * <ul>
 *   <li>{@code password}：BCrypt 加密（cost=10），禁止明文存储与返回</li>
 *   <li>{@code realName}：AES-256-GCM 字段级加密（{@code @EncryptField}），密文存储，明文仅在内存中出现</li>
 *   <li>{@code phone} / {@code email}：敏感信息，返回时脱敏</li>
 *   <li>{@code loginFailCount} / {@code lockedUntil}：登录失败保护，达到阈值自动锁定</li>
 * </ul>
 *
 * <p><b>生命周期状态字段说明（YDIZ-DDD-008 合规）</b>：
 *
 * <ul>
   *   <li>DB 新增列 {@code life_cycle VARCHAR(32)} 存储枚举字面量（ENABLED/DISABLED/PENDING/SUSPENDED/RESIGNED），通过 {@link #lifeCycle} 字段映射，无 TypeHandler</li>
 *   <li>平台基类 MpBaseEntity 继承的 {@code status} 字段在 UserAccount 中不再声明，避免覆盖</li>
 *   <li>旧列 {@code status} 保留在表中（标记 DEPRECATED），仅作为平台基类映射占位，业务代码不读写</li>
   *   <li>业务代码统一使用 {@link #getLifeCycle()} / {@link #setLifeCycle(UserLifeCycleEnum)} 操作枚举值</li>
 * </ul>
 *
 * <p><b>审批人展开支持：</b>
 *
 * <ul>
 *   <li>{@code deptId}：所属部门，支持 {@code dept:xxx} 审批人展开</li>
 *   <li>{@code leaderId}：直属上级用户 ID，支持 {@code leader:xxx} 展开</li>
 *   <li>{@code positionCode}：岗位编码（PM/DEV/QA/SA），支持 {@code position:xxx} 展开</li>
 * </ul>
 *
 * <p><b>索引设计：</b>唯一索引 {@code uk_username}（{@code username}），普通索引 {@code idx_phone}、{@code idx_dept_id}。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_idm_account_user")
// YDIZ-WARN-001 允许保留：Lombok @SuperBuilder 泛型擦除导致 unchecked 警告
@SuppressWarnings("unchecked")
public class UserAccount extends MpBaseEntity<String> {

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
   * <p><b>注意：</b>加密字段不可用于 WHERE/LIKE 条件查询（AES-GCM 随机 IV 导致明文相同密文不同），
   * 本字段仅用于 SELECT 展示，不参与条件检索。
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

  // ===== 生命周期（YDIZ-DDD-008：业务字段改名 lifeCycle，不覆盖平台基础字段）=====

  /**
   * 用户生命周期（映射 DB 列 {@code life_cycle}）。
   *
   * <p>取值：PENDING / ENABLED / SUSPENDED / DISABLED / RESIGNED。
   *
   * <p>旧列 {@code status}（INTEGER 0/1）已在 V26.10.02 DDL 迁移中通过 UPDATE 复制数据到 {@code life_cycle}，
   * 旧列保留在表中作为平台基类占位（标记 DEPRECATED），业务代码不再读写。
   *
   * <p>如需与历史 0/1 值兼容，调用 {@link UserLifeCycleEnum#parse(String)} 方法。
   */
  @TableField("life_cycle")
  private String lifeCycle;

  /** 用户类型（PLATFORM/ISV/TENANT_ADMIN/REGULAR 等） */
  private String userType;

  /** 所属公司 ID（关联 {@code ydsz_idm_org_company.id}） */
  private String companyId;

  /** 最近登录时间 */
  private LocalDateTime lastLoginAt;

  /** 最近登录 IP */
  private String lastLoginIp;

  /** 连续登录失败次数（达到阈值触发账号锁定） */
  private Integer loginFailCount;

  /** 账号锁定截止时间（解锁后自动清零 loginFailCount） */
  private LocalDateTime lockedUntil;

  /** 所属部门 ID（关联 ydsz_idm_org_department.id，支持 dept: 审批人展开） */
  private String deptId;

  /** 直属上级用户 ID（关联 ydsz_idm_account_user.id，支持 leader: 审批人展开） */
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

  // ==================== 状态枚举访问器 ====================

  /**
   * 获取生命周期状态枚举。
   *
   * <p>兼容遗留 "0"/"1" 格式（历史数据兼容）和枚举字面量格式。
   *
   * @return 生命周期状态枚举，无法解析时返回 null
   */
  public UserLifeCycleEnum getLifeCycle() {
    return UserLifeCycleEnum.parse(this.lifeCycle);
  }

  /**
   * 设置生命周期。
   *
   * @param lifeCycle 生命周期枚举，为 null 时清除
   */
  public void setLifeCycle(UserLifeCycleEnum lifeCycle) {
    this.lifeCycle = lifeCycle == null ? null : lifeCycle.name();
  }

  /**
   * {@link #getLifeCycle()} 的别名，兼容旧版 API。
   *
   * @return 生命周期枚举
   */
  public UserLifeCycleEnum getStatusEnum() {
    return getLifeCycle();
  }

  /**
   * {@link #setLifeCycle(UserLifeCycleEnum)} 的别名，兼容旧版 API。
   *
   * @param lifeCycleEnum 生命周期枚举
   */
  public void setStatusEnum(UserLifeCycleEnum lifeCycleEnum) {
    setLifeCycle(lifeCycleEnum);
  }

  // ==================== 领域行为（Domain Behavior）====================

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
   * <p>将状态设为 {@link UserLifeCycleEnum#ENABLED}，清除锁定信息。
   *
   * @throws IllegalStateException 当前状态不允许激活时抛出
   */
  public void activate() {
    UserLifeCycleEnum current = getLifeCycle();
    if (current != null) {
      current.requireTransitTo(UserLifeCycleEnum.ENABLED);
    }
    setLifeCycle(UserLifeCycleEnum.ENABLED);
    this.lockedUntil = null;
    this.loginFailCount = 0;
  }

  /**
   * 暂停账号（ENABLED → SUSPENDED）。
   *
   * <p>将状态设为 {@link UserLifeCycleEnum#SUSPENDED}。
   *
   * @throws IllegalStateException 当前状态不允许暂停时抛出
   */
  public void suspend() {
    UserLifeCycleEnum current = getLifeCycle();
    if (current != null) {
      current.requireTransitTo(UserLifeCycleEnum.SUSPENDED);
    }
    setLifeCycle(UserLifeCycleEnum.SUSPENDED);
  }

  /**
   * 恢复账号（SUSPENDED → ENABLED）。
   *
   * <p>将状态设为 {@link UserLifeCycleEnum#ENABLED}。
   *
   * @throws IllegalStateException 当前状态不允许恢复时抛出
   */
  public void resume() {
    UserLifeCycleEnum current = getLifeCycle();
    if (current != null) {
      current.requireTransitTo(UserLifeCycleEnum.ENABLED);
    }
    setLifeCycle(UserLifeCycleEnum.ENABLED);
    this.lockedUntil = null;
    this.loginFailCount = 0;
  }

  /**
   * 离职处理（→ RESIGNED）。
   *
   * <p>将状态设为 {@link UserLifeCycleEnum#RESIGNED}（终态）。
   *
   * @throws IllegalStateException 当前状态不允许离职时抛出
   */
  public void resign() {
    UserLifeCycleEnum current = getLifeCycle();
    if (current != null) {
      current.requireTransitTo(UserLifeCycleEnum.RESIGNED);
    }
    setLifeCycle(UserLifeCycleEnum.RESIGNED);
  }

  /**
   * 禁用账号（→ DISABLED）。
   *
   * <p>将状态设为 {@link UserLifeCycleEnum#DISABLED}。
   *
   * @throws IllegalStateException 当前状态不允许禁用时抛出
   */
  public void disable() {
    UserLifeCycleEnum current = getLifeCycle();
    if (current != null) {
      current.requireTransitTo(UserLifeCycleEnum.DISABLED);
    }
    setLifeCycle(UserLifeCycleEnum.DISABLED);
  }

  /**
   * 解锁账号。
   *
   * <p>清除锁定截止时间、重置登录失败计数。
   */
  public void unlock() {
    this.lockedUntil = null;
    this.loginFailCount = 0;
  }

  /**
   * 判断当前是否处于锁定状态。
   *
   * @return true 表示当前被锁定
   */
  public boolean isLocked() {
    return this.lockedUntil != null && this.lockedUntil.isAfter(LocalDateTime.now());
  }

  /**
   * 检查是否允许登录。
   *
   * <p>仅 {@link UserLifeCycleEnum#ENABLED} 状态且未锁定时允许登录。
   *
   * @return true 表示允许登录
   */
  public boolean canLogin() {
    return getLifeCycle() == UserLifeCycleEnum.ENABLED && !isLocked();
  }

  /**
   * 记录一次登录失败。
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
   * @param loginIp 登录来源 IP
   */
  public void recordLoginSuccess(String loginIp) {
    this.loginFailCount = 0;
    this.lockedUntil = null;
    this.lastLoginAt = LocalDateTime.now();
    this.lastLoginIp = loginIp;
  }
}
