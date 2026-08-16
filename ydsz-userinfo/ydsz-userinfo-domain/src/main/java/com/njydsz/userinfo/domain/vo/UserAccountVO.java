package com.njydsz.userinfo.domain.vo;

import java.time.LocalDateTime;

import lombok.Data;

import com.njydsz.common.safe.sensitive.SensitiveData;
import com.njydsz.common.safe.sensitive.SensitiveType;
import com.njydsz.userinfo.domain.entity.UserAccount;

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

  /**
   * 从实体转换为 VO（用于导出等需手动转换的场景）
   *
   * @param entity 用户账号实体
   * @return 用户 VO
   */
  public static UserAccountVO fromEntity(UserAccount entity) {
    if (entity == null) {
      return null;
    }
    UserAccountVO vo = new UserAccountVO();
    vo.setId(entity.getId());
    vo.setUsername(entity.getUsername());
    vo.setRealName(entity.getRealName());
    vo.setPhone(entity.getPhone());
    vo.setEmail(entity.getEmail());
    vo.setAvatar(entity.getAvatar());
    // status 字段：DB 为字符串 "0"/"1"，VO 为 Integer
    vo.setStatus(entity.getStatus() != null ? Integer.parseInt(entity.getStatus()) : null);
    vo.setUserType(entity.getUserType());
    vo.setCompanyId(entity.getCompanyId());
    vo.setTenantId(entity.getTenantId());
    vo.setLastLoginAt(entity.getLastLoginAt());
    vo.setLastLoginIp(entity.getLastLoginIp());
    vo.setCreatedAt(entity.getCreatedAt());
    vo.setUpdatedAt(entity.getUpdatedAt());
    return vo;
  }
}
