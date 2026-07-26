package com.njydsz.userinfo.infra.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.userinfo.domain.entity.RolePermissionDO;

/**
 * 角色-权限关联表 Mapper 接口。
 *
 * <p>对应数据表 ydsz_role_permission，
 * 继承 MyBatis-Plus {@code BaseMapper} 提供标准 CRUD 操作。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
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
