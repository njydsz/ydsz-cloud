package com.njydsz.userinfo.domain.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.njydsz.common.domain.query.PageQuery;

/**
 * 角色分页查询参数 DTO。
 *
 * <p>用于 {@code GET /api/v1/role/page} 接口，支持多条件组合筛选角色列表。
 * 继承 {@link PageQuery} 获取分页参数（{@code pageNum} / {@code pageSize}）。
 *
 * <p><b>筛选条件：</b>所有字段均为可选，未传则不作为筛选条件。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class RolePageQueryDTO extends PageQuery {

    /** 角色编码（模糊查询） */
    private String roleCode;

    /** 角色名称（模糊查询） */
    private String roleName;

    /** 角色状态（{@code "ENABLED"}=启用 / {@code "DISABLED"}=禁用，精确匹配） */
    private String status;
}
