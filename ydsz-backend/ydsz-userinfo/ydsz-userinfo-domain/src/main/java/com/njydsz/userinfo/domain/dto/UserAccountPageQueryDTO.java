package com.njydsz.userinfo.domain.dto;

import com.njydsz.common.domain.query.PageQuery;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户分页查询 DTO，继承 {@link PageQuery} 提供分页基础字段。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserAccountPageQueryDTO extends PageQuery {

    /** 用户名，模糊查询 */
    private String username;
    /** 真实姓名，模糊查询 */
    private String realName;
    /** 手机号，模糊查询 */
    private String phone;
    /** 邮箱，模糊查询 */
    private String email;
    /** 用户类型 */
    private String userType;
    /** 公司 ID */
    private String companyId;
    /** 租户 ID */
    private String tenantId;
}
