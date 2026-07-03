package com.njydsz.pmis.userinfo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.userinfo.entity.RoleDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface RoleMapper extends BaseMapper<RoleDO> {

    /**
     * 根据角色编码查询角色
     *
     * @param code 角色编码
     * @return 角色对象，未找到返回 null
     */
    @Select("SELECT * FROM pmis_role WHERE role_code = #{code} AND deleted = 0 LIMIT 1")
    RoleDO selectByCode(@Param("code") String code);

    /**
     * 查询用户拥有的所有角色
     *
     * @param userId 用户 ID
     * @return 角色列表
     */
    @Select("""
            SELECT r.* FROM pmis_role r
            INNER JOIN pmis_user_role ur ON ur.role_id = r.id AND ur.deleted = 0
            WHERE ur.user_id = #{userId} AND r.deleted = 0
            ORDER BY r.sort_order, r.id
            """)
    List<RoleDO> selectByUserId(@Param("userId") Long userId);
}
