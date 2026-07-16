package com.njydsz.userinfo.infra.mapper.permission;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.userinfo.domain.entity.permission.RolePermissionDO;

/**
 * 角色-权限关联 Mapper
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface RolePermissionMapper extends BaseMapper<RolePermissionDO> {

    /**
     * 查询角色拥有的权限 ID 列表
     *
     * @param roleId 角色 ID
     * @return 权限 ID 列表
     */
    @Select("SELECT permission_id FROM ydsz_role_permission WHERE role_id = #{roleId} AND deleted = 0")
    List<String> selectPermissionIdsByRoleId(@Param("roleId") String roleId);
}
