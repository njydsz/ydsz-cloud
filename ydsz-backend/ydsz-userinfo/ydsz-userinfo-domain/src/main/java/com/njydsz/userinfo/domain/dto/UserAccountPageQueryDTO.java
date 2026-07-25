package com.njydsz.userinfo.domain.dto;

import com.njydsz.common.core.request.PageRequest;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户分页查询 DTO。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserAccountPageQueryDTO extends PageRequest {

    private String username;
    private String realName;
    private String phone;
    private String email;
    private Integer status;
    private String userType;
    private String companyId;
    private String tenantId;
}
