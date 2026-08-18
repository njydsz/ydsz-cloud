package com.njydsz.userinfo.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import com.njydsz.userinfo.domain.enums.EnableStatusEnum;

/**
 * 用户更新请求 DTO。
 *
 * <p>用于 {@code PUT /api/v1/user} 接口，更新用户账号信息。 更新时 {@code id} 必填，其余字段按需填写，未传字段保持原值不变（动态更新）。
 *
 * <p><b>不可更新字段：</b>{@code username}（登录名创建后不可修改）、{@code password}（请使用专用修改密码接口）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class UserAccountUpdateDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 用户 ID（必填，指定更新的目标用户） */
  @NotBlank(message = "ID不能为空")
  private String id;

  /** 真实姓名（用于展示和审批人显示） */
  @Size(max = 64, message = "真实姓名长度不能超过 64 个字符")
  private String realName;

  /** 手机号（用于短信验证/找回密码） */
  @Size(max = 20, message = "手机号长度不能超过 20 个字符")
  private String phone;

  /** 邮箱（用于邮件通知/找回密码） */
  @Size(max = 128, message = "邮箱长度不能超过 128 个字符")
  private String email;

  /** 头像 URL */
  @Size(max = 255, message = "头像URL长度不能超过 255 个字符")
  private String avatar;

  /** 账号状态（{@link EnableStatusEnum#ENABLED}=启用 / {@link EnableStatusEnum#DISABLED}=禁用） */
  private EnableStatusEnum status;

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

  /**
   * 乐观锁版本号（P1-6）。
   *
   * <p>由前端在编辑页面携带（从查询响应获取），更新时用于乐观锁冲突检测。
   * 为 null 时保持原行为（由 Service 层填充当前版本）；携带后若与 DB 当前版本不一致，
   * 更新将被拒绝并提示"数据已被他人修改"。
   */
  private Integer revision;
}
