package com.njydsz.pmis.user.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.user.dto.RoleFormDTO;
import com.njydsz.pmis.user.dto.RoleQueryDTO;
import com.njydsz.pmis.user.entity.RoleDO;

import java.util.List;

/**
 * 角色服务
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface RoleService {

    Page<RoleDO> page(RoleQueryDTO query);

    List<RoleDO> listAllEnabled();

    RoleDO getById(Long id);

    /**
     * 查询用户拥有的所有角色
     */
    List<RoleDO> listByUserId(Long userId);

    Long create(RoleFormDTO dto);

    void update(RoleFormDTO dto);

    void delete(Long id);

    /**
     * 为角色分配权限
     */
    void assignPermissions(Long roleId, List<Long> permissionIds);

    /**
     * 查询角色的权限 ID 列表
     */
    List<Long> listPermissionIds(Long roleId);
}
