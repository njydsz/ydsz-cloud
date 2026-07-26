package com.njydsz.userinfo.server.service;

import java.util.List;

import com.njydsz.userinfo.domain.dto.RolePageQueryDTO;
import com.njydsz.userinfo.domain.dto.RoleSaveDTO;
import com.njydsz.userinfo.domain.vo.RoleVO;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 角色 Service 接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface RoleService {

    RoleVO getById(String id);
    Page<RoleVO> page(RolePageQueryDTO query);
    List<RoleVO> list();
    String create(RoleSaveDTO dto);
    boolean update(RoleSaveDTO dto);
    boolean removeById(String id);
    boolean assignPermissions(String roleId, List<String> permissionIds);
    List<String> getRolePermissionIds(String roleId);
}
