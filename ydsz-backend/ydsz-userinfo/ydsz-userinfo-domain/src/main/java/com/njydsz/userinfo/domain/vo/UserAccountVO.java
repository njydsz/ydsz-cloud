package com.njydsz.userinfo.domain.vo;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 用户账号 VO（不含密码等敏感字段）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class UserAccountVO {

    private String id;
    private String username;
    private String realName;
    private String phone;
    private String email;
    private String avatar;
    private Integer status;
    private String userType;
    private String companyId;
    private String tenantId;
    private LocalDateTime lastLoginAt;
    private String lastLoginIp;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
