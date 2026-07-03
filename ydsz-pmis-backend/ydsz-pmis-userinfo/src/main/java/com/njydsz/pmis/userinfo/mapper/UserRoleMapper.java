package com.njydsz.pmis.userinfo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.userinfo.entity.UserRoleDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户-角色关联 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface UserRoleMapper extends BaseMapper<UserRoleDO> {

    /**
     * 查询用户拥有的角色 ID 列表
     *
     * @param userId 用户 ID
     * @return 角色 ID 列表
     */
    @Select("SELECT role_id FROM pmis_user_role WHERE user_id = #{userId} AND deleted = 0")
    List<Long> selectRoleIdsByUserId(@Param("userId") Long userId);

    /**
     * 查询角色下的用户 ID 列表
     *
     * @param roleId 角色 ID
     * @return 用户 ID 列表
     */
    @Select("SELECT user_id FROM pmis_user_role WHERE role_id = #{roleId} AND deleted = 0")
    List<Long> selectUserIdsByRoleId(@Param("roleId") Long roleId);
}
