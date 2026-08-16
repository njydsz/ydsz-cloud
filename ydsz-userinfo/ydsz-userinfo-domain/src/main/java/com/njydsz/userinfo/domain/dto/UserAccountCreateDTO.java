package com.njydsz.userinfo.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import lombok.Data;

/**
 * 用户创建请求 DTO。
 *
 * <p>用于 {@code POST /api/v1/user} 接口，创建系统用户账号。 创建后账号默认状态为「启用」，密码经 BCrypt 加密后存储。
 *
 * <p><b>校验规则：</b>
 *
 * <ul>
 *   <li>{@code username} — 必填，全局唯一，长度 ≤ 64
 *   <li>{@code password} — 必填，长度 8-64，须符合密码策略（大小写+数字+特殊字符）
 *   <li>{@code realName} — 必填，长度 ≤ 64
 *   <li>{@code phone} / {@code email} — 可选，须符合格式校验
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class UserAccountCreateDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 登录用户名（全局唯一，建议格式：字母+数字组合） */
  @NotBlank(message = "用户名不能为空")
  @Size(max = 64, message = "用户名长度不能超过 64 个字符")
  private String username;

  /** 登录密码（明文传入，服务端 BCrypt 加密存储，须符合密码策略） */
  @NotBlank(message = "密码不能为空")
  @Size(min = 8, max = 64, message = "密码长度必须在 8-64 个字符之间")
  private String password;

  /** 真实姓名（用于展示和审批人显示） */
  @NotBlank(message = "真实姓名不能为空")
  @Size(max = 64, message = "真实姓名长度不能超过 64 个字符")
  private String realName;

  /** 手机号（用于短信验证/找回密码，可空） */
  @Size(max = 20, message = "手机号长度不能超过 20 个字符")
  private String phone;

  /** 邮箱（用于邮件通知/找回密码，可空） */
  @Size(max = 128, message = "邮箱长度不能超过 128 个字符")
  private String email;

  /** 头像 URL（可空，默认使用系统头像） */
  @Size(max = 255, message = "头像URL长度不能超过 255 个字符")
  private String avatar;

  /** 账号状态（{@code "1"}=启用 / {@code "0"}=禁用，默认启用） */
  private String status;

  /** 用户类型（{@code PLATFORM}=平台用户 / {@code TENANT_ADMIN}=租户管理员 / {@code REGULAR}=普通用户） */
  private String userType;

  /** 所属公司 ID（关联 {@code ydsz_company.id}） */
  private String companyId;

  /** 所属部门 ID（关联 {@code ydsz_department.id}，支持审批人展开） */
  private String deptId;

  /** 直属上级用户 ID（关联 {@code ydsz_user_account.id}，支持 leader: 审批人展开） */
  private String leaderId;

  /** 岗位编码（如 PM/DEV/QA/SA，支持 position: 审批人展开） */
  private String positionCode;

  /** 角色 ID 列表（创建时一次性分配角色，可空表示暂不分配） */
  private List<String> roleIds;

  /** 租户 ID（多租户场景下指定归属租户，通常由系统自动填充） */
  private String tenantId;
}
