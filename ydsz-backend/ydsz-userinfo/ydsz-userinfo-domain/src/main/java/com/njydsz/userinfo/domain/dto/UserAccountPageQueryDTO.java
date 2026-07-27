package com.njydsz.userinfo.domain.dto;

import com.njydsz.common.domain.query.PageQuery;

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
public class UserAccountPageQueryDTO extends PageQuery {

    private String username;
    private String realName;
    private String phone;
    private String email;
    private String userType;
    private String companyId;
    private String tenantId;
}
