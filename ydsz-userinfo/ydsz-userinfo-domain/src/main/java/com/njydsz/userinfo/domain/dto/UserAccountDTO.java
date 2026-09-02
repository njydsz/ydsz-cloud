package com.njydsz.userinfo.domain.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import com.njydsz.userinfo.domain.enums.EnableStatusEnum;

/**
 * 用户账号统一 DTO（P1-1 CUD 入参）。
 *
 * <p>同时用于创建和更新场景：创建时 {@code username}/{@code password} 必填，更新时 {@code id} 必填。
 *
 * <p><b>不可更新字段：</b>{@code username}（登录名创建后不可修改）、{@code password}（请使用专用修改密码接口）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class UserAccountDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 用户 ID（更新时必填，指定更新的目标用户） */
  @NotBlank(message = "ID不能为空")
  private String id;

  /** 登录用户名（全局唯一，创建时必填，创建后不可修改） */
  @NotBlank(message = "用户名不能为空")
  @Size(max = 64, message = "用户名长度不能超过 64 个字符")
  private String username;

  /** 用户名（SCIM 兼容字段，与 username 同义） */
  private String userName;

  /** 外部系统标识（SCIM externalId，用于与 HR 系统关联） */
  private String externalId;

  /** 登录密码（明文传入，服务端 BCrypt 加密存储，创建时必填） */
  @NotBlank(message = "密码不能为空")
  @Size(min = 8, max = 64, message = "密码长度必须在 8-64 个字符之间")
  private String password;

  /** 真实姓名（用于展示和审批人显示） */
  @NotBlank(message = "真实姓名不能为空")
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

  /** 所属公司 ID（关联 {@code ydsz_org_company.id}） */
  private String companyId;

  /** 所属部门 ID（关联 {@code ydsz_org_department.id}，支持审批人展开） */
  private String deptId;

  /** 直属上级用户 ID（关联 {@code ydsz_acct_user.id}，支持 leader: 审批人展开） */
  private String leaderId;

  /** 岗位编码（如 PM/DEV/QA/SA，支持 position: 审批人展开） */
  private String positionCode;

  /** 角色 ID 列表（创建时一次性分配角色，可空表示暂不分配） */
  private List<String> roleIds;

  /** 租户 ID（多租户场景下指定归属租户，通常由系统自动填充） */
  private String tenantId;

  /**
   * 乐观锁版本号（P1-6）。
   *
   * <p>由前端在编辑页面携带（从查询响应获取），更新时用于乐观锁冲突检测。
   * 为 null 时保持原行为（由 Service 层填充当前版本）；携带后若与 DB 当前版本不一致，
   * 更新将被拒绝并提示"数据已被他人修改"。
   */
  private Integer revision;
}
