package com.njydsz.pmis.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.iam.entity.RolePermissionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色-权限关联 Mapper
 *
 * @author ydsz-pmis-team
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
    @Select("SELECT permission_id FROM pmis_role_permission WHERE role_id = #{roleId} AND deleted = 0")
    List<Long> selectPermissionIdsByRoleId(@Param("roleId") Long roleId);
}
