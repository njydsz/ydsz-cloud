package com.njydsz.userinfo.infra.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.userinfo.domain.entity.UserRoleDO;

@Mapper
public interface UserRoleMapper extends BaseMapper<UserRoleDO> {

    /**
     * 批量插入用户-角色关联。
     *
     * @param list 关联列表
     * @return 插入行数
     */
    @Insert("<script>"
            + "INSERT INTO ydsz_user_role (id, user_id, role_id, tenant_id, deleted) VALUES "
            + "<foreach collection='list' item='item' separator=','>"
            + "(#{item.id}, #{item.userId}, #{item.roleId}, #{item.tenantId}, 0)"
            + "</foreach>"
            + "</script>")
    int batchInsert(@Param("list") List<UserRoleDO> list);
}
