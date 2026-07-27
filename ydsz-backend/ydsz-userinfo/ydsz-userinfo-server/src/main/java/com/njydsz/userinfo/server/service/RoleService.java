package com.njydsz.userinfo.server.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.njydsz.common.domain.service.BaseCrudService;
import com.njydsz.userinfo.domain.dto.RolePageQueryDTO;
import com.njydsz.userinfo.domain.dto.RoleSaveDTO;
import com.njydsz.userinfo.domain.entity.Role;
import com.njydsz.userinfo.domain.vo.RoleVO;

/**
 * 角色 Service 接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface RoleService extends BaseCrudService<Role, RoleSaveDTO, RoleVO, RolePageQueryDTO, String> {

    /**
     * 查询全部角色列表（无分页，按 sortOrder 升序）。
     *
     * @return 角色 VO 列表
     */
    List<RoleVO> list();

    /**
     * 为角色分配权限（全量覆盖模式）。
     *
     * @param roleId        角色 ID
     * @param permissionIds 权限 ID 列表
     * @return 是否成功
     */
    boolean assignPermissions(String roleId, List<String> permissionIds);

    /**
     * 查询角色的权限 ID 列表。
     *
     * @param roleId 角色 ID
     * @return 权限 ID 列表
     */
    List<String> getRolePermissionIds(String roleId);

    /**
     * 批量查询角色 ID → 角色名映射（供 NameAssembler 跨服务富化 roleName 字段）。
     *
     * <p>实现：单条 SQL {@code SELECT id, role_name FROM ydsz_role WHERE id IN (...)}，
     * 一次往返拿到全部结果。已逻辑删除的角色不会出现在结果中。
     *
     * @param roleIds 角色 ID 集合（允许 null / 空，返回空 Map）
     * @return roleId → roleName 映射；未命中的 roleId 不出现在 Map 中
     */
    Map<String, String> batchNamesByIds(Collection<String> roleIds);
}