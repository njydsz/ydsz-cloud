package com.njydsz.userinfo.domain.vo;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 用户账号 VO，用于 Controller 返回，不包含密码、盐值等敏感字段。
 *
 * <p>由 {@code UserInfoConverter.entityToVO()} 从 {@code UserAccount} 实体转换而来，
 * 供前端展示和跨模块查询使用。
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
    private String realName;
    /** 手机号码 */
    private String phone;
    /** 邮箱地址 */
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
}
