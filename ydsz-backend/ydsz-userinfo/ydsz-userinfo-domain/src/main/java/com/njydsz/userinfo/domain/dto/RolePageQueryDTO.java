package com.njydsz.userinfo.domain.dto;

import com.njydsz.common.domain.query.PageQuery;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色分页查询 DTO，继承 {@link PageQuery}。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RolePageQueryDTO extends PageQuery {

    /** 角色编码，模糊查询 */
    private String roleCode;
    /** 角色名称，模糊查询 */
    private String roleName;
    /** 状态过滤 */
    private String status;
    /** 租户 ID */
    private String tenantId;
}