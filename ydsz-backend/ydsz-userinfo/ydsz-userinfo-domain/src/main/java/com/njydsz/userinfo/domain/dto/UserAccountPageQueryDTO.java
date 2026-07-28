package com.njydsz.userinfo.domain.dto;

import com.njydsz.common.domain.query.PageQuery;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 用户分页查询参数 DTO。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UserAccountPageQueryDTO extends PageQuery {

    private String username;
    private String realName;
    private String phone;
    private String email;
    private String status;
    private String userType;
    private String companyId;
    private String deptId;
}
