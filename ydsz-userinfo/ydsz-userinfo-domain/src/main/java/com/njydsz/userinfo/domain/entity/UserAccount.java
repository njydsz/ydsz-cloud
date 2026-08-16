package com.njydsz.userinfo.domain.entity;

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
import com.njydsz.userinfo.domain.enums.EnableStatusEnum;

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
 * <p><b>状态字段说明：</b>DB 列使用整数（0=禁用, 1=启用，历史遗留），通过 {@link IntegerStringTypeHandler} 自动转换为 String。 业务代码通过 {@link #getStatusEnum()} / {@link #setStatusEnum(EnableStatusEnum)} 使用枚举类型， {@link EnableStatusEnum#parse(String)} 兼容两种格式。
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

  /**
   * 获取状态枚举（从 String "0"/"1" 解析）。
   *
   * <p>{@code "1"} → {@link EnableStatusEnum#ENABLED}，{@code "0"} → {@link EnableStatusEnum#DISABLED}。
   *
   * @return 状态枚举，无法解析时返回 null
   */
  public EnableStatusEnum getStatusEnum() {
    return EnableStatusEnum.parse(this.status);
  }

  /**
   * 从枚举设置状态（转为 String "0"/"1" 存储）。
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
}
