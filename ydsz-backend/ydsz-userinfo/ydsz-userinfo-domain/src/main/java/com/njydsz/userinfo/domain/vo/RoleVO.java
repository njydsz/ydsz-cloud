package com.njydsz.userinfo.domain.vo;

import lombok.Data;

/**
 * 角色 VO（不含 deleted/createdBy 等内部字段）。
 *
 * @author ydsz-team
 * @since 1.1.0
 */
@Data
public class RoleVO {

    private String id;
    private String roleCode;
    private String roleName;
    private String description;
    private Integer sortOrder;
    private String status;
    private Boolean builtIn;
}
