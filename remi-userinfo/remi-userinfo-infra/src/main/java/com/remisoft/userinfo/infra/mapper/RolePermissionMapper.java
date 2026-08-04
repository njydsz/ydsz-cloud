package com.remisoft.userinfo.infra.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.remisoft.userinfo.domain.entity.RolePermission;

/**
 * 角色-权限关联表 Mapper
 *
 * <p>对应数据表 <code>remi_role_permission</code>，存储角色与权限（菜单）的多对多关联。</p>
 * <p>是 RBAC 模型的核心中间表，权限（{@code remi_menu}）既可表示菜单也可表示后端接口权限码。
 *
 * <p><b>主要索引：</b>
 * <ul>
 *   <li>uk_role_perm — (roleId+menuId) 唯一索引</li>
 *   <li>idx_role_id — 角色维度查询索引（角色的权限）</li>
 *   <li>idx_menu_id — 菜单维度查询索引（哪些角色拥有）</li>
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author remi-team
 * @since 1.0.0
 *
 * @see com.remisoft.userinfo.domain.entity.RolePermission 角色-权限关联实体
 * @see com.remisoft.userinfo.server.service.RolePermissionService 角色-权限 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface RolePermissionMapper extends BaseMapper<RolePermission> {

    /**
     * 批量插入角色-权限关联。
     *
     * @param list 关联列表
     * @return 插入行数
     */
    @Insert("<script>"
            + "INSERT INTO remi_role_permission (id, role_id, permission_id, tenant_id, deleted) VALUES "
            + "<foreach collection='list' item='item' separator=','>"
            + "(#{item.id}, #{item.roleId}, #{item.permissionId}, #{item.tenantId}, 0)"
            + "</foreach>"
            + "</script>")
    int batchInsert(@Param("list") List<RolePermission> list);
}
