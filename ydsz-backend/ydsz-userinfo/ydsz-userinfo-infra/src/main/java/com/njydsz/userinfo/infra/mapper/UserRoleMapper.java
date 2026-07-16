package com.njydsz.userinfo.infra.mapper.user;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.userinfo.domain.entity.user.UserRoleDO;

/**
 * 用户-角色关联 Mapper
 *
 * @author ydsz-team
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
    @Select("SELECT role_id FROM ydsz_user_role WHERE user_id = #{userId} AND deleted = 0")
    List<String> selectRoleIdsByUserId(@Param("userId") String userId);

    /**
     * 查询角色下的用户 ID 列表
     *
     * @param roleId 角色 ID
     * @return 用户 ID 列表
     */
    @Select("SELECT user_id FROM ydsz_user_role WHERE role_id = #{roleId} AND deleted = 0")
    List<String> selectUserIdsByRoleId(@Param("roleId") String roleId);
}
