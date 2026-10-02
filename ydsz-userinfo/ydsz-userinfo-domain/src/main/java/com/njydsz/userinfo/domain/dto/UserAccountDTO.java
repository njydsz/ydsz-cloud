package com.njydsz.userinfo.domain.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import com.njydsz.userinfo.domain.enums.UserLifecycleStatusEnum;


/**
 * 用户账号统一 DTO（P1-1 CUD 入参）。
 *
 * <p>同时用于创建和更新场景：创建时 {@code username}/{@code password} 必填，更新时 {@code id} 必填。
 *
 * <p><b>不可更新字段：</b>{@code username}（登录名创建后不可修改）、{@code password}（请使用专用修改密码接口）。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Data
public class UserAccountDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 用户 ID（更新时必填，指定更新的目标用户） */
  @NotBlank(message = "{userinfo.param.required}")
  private String id;

  /** 登录用户名（全局唯一，创建时必填，创建后不可修改） */
  @NotBlank(message = "{userinfo.param.required}")
  @Size(max = 64, message = "{userinfo.error.max_length}")
  private String username;

  /** 用户名（SCIM 兼容字段，与 username 同义） */
  private String userName;

  /** 外部系统标识（SCIM externalId，用于与 HR 系统关联） */
  private String externalId;

  /** 登录密码（明文传入，服务端 BCrypt 加密存储，创建时必填） */
  @NotBlank(message = "{userinfo.param.required}")
  @Size(min = 8, max = 64, message = "{userinfo.error.length_range}")
  private String password;

  /** 真实姓名（用于展示和审批人显示） */
  @NotBlank(message = "{userinfo.param.required}")
  @Size(max = 64, message = "{userinfo.error.max_length}")
  private String realName;

  /** 手机号（用于短信验证/找回密码） */
  @Size(max = 20, message = "{userinfo.error.max_length}")
  private String phone;

  /** 邮箱（用于邮件通知/找回密码） */
  @Size(max = 128, message = "{userinfo.error.max_length}")
  private String email;

  /** 头像 URL */
  @Size(max = 255, message = "{userinfo.error.max_length}")
  private String avatar;

  /** 账号状态（{@link UserLifecycleStatusEnum#ENABLED}=启用 / {@link UserLifecycleStatusEnum#DISABLED}=禁用） */
  private UserLifecycleStatusEnum status;

  /** 用户类型（{@code PLATFORM}=平台用户 / {@code TENANT_ADMIN}=租户管理员 / {@code REGULAR}=普通用户） */
  private String userType;

  /** 所属公司 ID（关联 {@code ydsz_idm_org_company.id}） */
  private String companyId;

  /** 所属部门 ID（关联 {@code ydsz_idm_org_department.id}，支持审批人展开） */
  private String deptId;

  /** 直属上级用户 ID（关联 {@code ydsz_idm_account_user.id}，支持 leader: 审批人展开） */
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
