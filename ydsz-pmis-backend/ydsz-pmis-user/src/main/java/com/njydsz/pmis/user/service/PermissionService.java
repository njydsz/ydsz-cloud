package com.njydsz.pmis.user.service;

import com.njydsz.pmis.user.dto.PermissionFormDTO;
import com.njydsz.pmis.user.entity.PermissionDO;

import java.util.List;

/**
 * 权限服务
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface PermissionService {

    /**
     * 查询所有启用的权限（构建树）
     */
    List<PermissionDO> listAllEnabled();

    /**
     * 查询用户拥有的权限编码
     */
    List<String> listPermCodesByUserId(Long userId);

    /**
     * 查询角色拥有的权限
     */
    List<PermissionDO> listByRoleId(Long roleId);

    PermissionDO getById(Long id);

    Long create(PermissionFormDTO dto);

    void update(PermissionFormDTO dto);

    void delete(Long id);
}
