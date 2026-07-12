package com.njydsz.pmis.userinfo.server.service.permission;

import com.njydsz.pmis.userinfo.domain.dto.permission.PermissionFormDTO;
import com.njydsz.pmis.userinfo.domain.entity.permission.PermissionDO;
import com.njydsz.pmis.userinfo.domain.vo.MenuTreeVO;

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
     *
     * @return 启用权限列表
     */
    List<PermissionDO> listAllEnabled();

    /**
     * 查询用户拥有的权限编码
     *
     * @param userId 用户 ID
     * @return 权限编码列表
     */
    List<String> listPermCodesByUserId(String userId);

    /**
     * 查询用户拥有的菜单树 (已过滤 permType=MENU/BUTTON 排序)
     *
     * @param userId 用户 ID
     * @return 菜单树
     */
    List<MenuTreeVO> listMenuTreeByUserId(String userId);

    /**
     * 查询全部菜单树 (管理端使用)
     *
     * @return 菜单树
     */
    List<MenuTreeVO> listAllMenuTree();

    /**
     * 查询角色拥有的权限
     *
     * @param roleId 角色 ID
     * @return 权限列表
     */
    List<PermissionDO> listByRoleId(String roleId);

    /**
     * 根据 ID 查询权限
     *
     * @param id 权限 ID
     * @return 权限实体，不存在时返回 null
     */
    PermissionDO getById(String id);

    /**
     * 创建权限
     *
     * @param dto 权限表单
     * @return 新建权限 ID
     */
    String create(PermissionFormDTO dto);

    /**
     * 更新权限
     *
     * @param dto 权限表单
     */
    void update(PermissionFormDTO dto);

    /**
     * 删除权限
     *
     * @param id 权限 ID
     */
    void delete(String id);
}
