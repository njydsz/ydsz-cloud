package com.njydsz.userinfo.infra.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.userinfo.domain.entity.RolePermissionDO;

@Mapper
public interface RolePermissionMapper extends BaseMapper<RolePermissionDO> {

    /**
     * 批量插入角色-权限关联。
     *
     * @param list 关联列表
     * @return 插入行数
     */
    @Insert("<script>"
            + "INSERT INTO ydsz_role_permission (id, role_id, permission_id, tenant_id, deleted) VALUES "
            + "<foreach collection='list' item='item' separator=','>"
            + "(#{item.id}, #{item.roleId}, #{item.permissionId}, #{item.tenantId}, 0)"
            + "</foreach>"
            + "</script>")
    int batchInsert(@Param("list") List<RolePermissionDO> list);
}
