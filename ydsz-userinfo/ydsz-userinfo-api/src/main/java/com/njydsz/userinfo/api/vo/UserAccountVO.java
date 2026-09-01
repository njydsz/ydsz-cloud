package com.njydsz.userinfo.api.vo;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 用户账号 VO（API 契约层）。
 *
 * <p>定义 Feign 客户端接口的返回类型，供跨服务调用方引用。
 * 与 domain 层 {@code com.njydsz.userinfo.domain.vo.UserAccountVO} 字段一致，但不含脱敏注解。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class UserAccountVO {

  /** 用户唯一标识 */
  private String id;

  /** 登录用户名 */
  private String username;

  /** 真实姓名 */
  private String realName;

  /** 手机号码（脱敏后） */
  private String phone;

  /** 邮箱地址（脱敏后） */
  private String email;

  /** 头像 URL */
  private String avatar;

  /** 账号状态：1-启用、0-停用 */
  private Integer status;

  /** 用户类型，如 SYS（系统）、BIZ（业务） */
  private String userType;

  /** 所属公司 ID */
  private String companyId;

  /** 所属部门 ID */
  private String deptId;

  /** 直属上级用户 ID */
  private String leaderId;

  /** 岗位编码 */
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

  /** 乐观锁版本号 */
  private Integer revision;
}
