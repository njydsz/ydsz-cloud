package com.njydsz.pmis.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.user.entity.PermissionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PermissionMapper extends BaseMapper<PermissionDO> {

    @Select("SELECT * FROM pmis_permission WHERE perm_code = #{code} AND deleted = 0 LIMIT 1")
    PermissionDO selectByCode(@Param("code") String code);

    /**
     * 查询用户拥有的所有权限编码
     */
    @Select("""
            SELECT DISTINCT p.perm_code FROM pmis_permission p
            INNER JOIN pmis_role_permission rp ON rp.permission_id = p.id AND rp.deleted = 0
            INNER JOIN pmis_user_role ur ON ur.role_id = rp.role_id AND ur.deleted = 0
            WHERE ur.user_id = #{userId} AND p.deleted = 0
            """)
    List<String> selectPermCodesByUserId(@Param("userId") Long userId);

    /**
     * 查询角色拥有的权限
     */
    @Select("""
            SELECT p.* FROM pmis_permission p
            INNER JOIN pmis_role_permission rp ON rp.permission_id = p.id AND rp.deleted = 0
            WHERE rp.role_id = #{roleId} AND p.deleted = 0
            ORDER BY p.sort_order, p.id
            """)
    List<PermissionDO> selectByRoleId(@Param("roleId") Long roleId);

    /**
     * 查询用户拥有的全部权限 (含完整属性,用于菜单树构建)
     */
    @Select("""
            SELECT DISTINCT p.* FROM pmis_permission p
            INNER JOIN pmis_role_permission rp ON rp.permission_id = p.id AND rp.deleted = 0
            INNER JOIN pmis_user_role ur ON ur.role_id = rp.role_id AND ur.deleted = 0
            WHERE ur.user_id = #{userId} AND p.deleted = 0
            ORDER BY p.sort_order, p.id
            """)
    List<PermissionDO> selectByUserId(@Param("userId") Long userId);
}
