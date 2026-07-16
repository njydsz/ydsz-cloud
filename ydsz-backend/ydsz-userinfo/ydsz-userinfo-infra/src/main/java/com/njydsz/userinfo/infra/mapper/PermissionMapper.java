package com.njydsz.userinfo.infra.mapper.permission;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.userinfo.domain.entity.permission.PermissionDO;

/**
 * 权限 Mapper
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface PermissionMapper extends BaseMapper<PermissionDO> {

    /**
     * 根据权限编码查询权限
     *
     * @param code 权限编码
     * @return 权限对象，未找到返回 null
     */
    @Select("SELECT * FROM ydsz_permission WHERE perm_code = #{code} AND deleted = 0 LIMIT 1")
    PermissionDO selectByCode(@Param("code") String code);

    /**
     * 查询用户拥有的所有权限编码
     *
     * @param userId 用户 ID
     * @return 权限编码列表
     */
    @Select("""
            SELECT DISTINCT p.perm_code FROM ydsz_permission p
            INNER JOIN ydsz_role_permission rp ON rp.permission_id = p.id AND rp.deleted = 0
            INNER JOIN ydsz_user_role ur ON ur.role_id = rp.role_id AND ur.deleted = 0
            WHERE ur.user_id = #{userId} AND p.deleted = 0
            """)
    List<String> selectPermCodesByUserId(@Param("userId") String userId);

    /**
     * 查询角色拥有的权限
     *
     * @param roleId 角色 ID
     * @return 权限列表
     */
    @Select("""
            SELECT p.* FROM ydsz_permission p
            INNER JOIN ydsz_role_permission rp ON rp.permission_id = p.id AND rp.deleted = 0
            WHERE rp.role_id = #{roleId} AND p.deleted = 0
            ORDER BY p.sort_order, p.id
            """)
    List<PermissionDO> selectByRoleId(@Param("roleId") String roleId);

    /**
     * 查询用户拥有的全部权限 (含完整属性,用于菜单树构建)
     *
     * @param userId 用户 ID
     * @return 权限列表
     */
    @Select("""
            SELECT DISTINCT p.* FROM ydsz_permission p
            INNER JOIN ydsz_role_permission rp ON rp.permission_id = p.id AND rp.deleted = 0
            INNER JOIN ydsz_user_role ur ON ur.role_id = rp.role_id AND ur.deleted = 0
            WHERE ur.user_id = #{userId} AND p.deleted = 0
            ORDER BY p.sort_order, p.id
            """)
    List<PermissionDO> selectByUserId(@Param("userId") String userId);
}
