package com.njydsz.userinfo.domain.dto;

import com.njydsz.common.domain.query.PageQuery;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 角色分页查询参数 DTO。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class RolePageQueryDTO extends PageQuery {

    private String roleCode;
    private String roleName;
    private String status;
}
