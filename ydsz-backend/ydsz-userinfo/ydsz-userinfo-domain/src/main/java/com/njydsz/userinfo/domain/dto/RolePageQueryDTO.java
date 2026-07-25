package com.njydsz.userinfo.domain.dto;

import com.njydsz.common.core.request.PageRequest;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色分页查询 DTO。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RolePageQueryDTO extends PageRequest {

    private String roleCode;
    private String roleName;
    private String status;
    private String tenantId;
}
