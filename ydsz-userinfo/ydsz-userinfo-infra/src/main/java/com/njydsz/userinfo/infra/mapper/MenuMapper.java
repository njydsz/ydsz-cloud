package com.njydsz.userinfo.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import com.njydsz.userinfo.infra.entity.MenuDO;

/**
 * 菜单/权限 Mapper 接口
 *
 * <p>对应数据表 {@code ydsz_menu}，存储 RBAC 模型中的菜单与权限点。 菜单（{@code ydsz_menu}）既可表示前端路由节点，也可表示后端接口权限码（如
 * {@code system:user:create}）。
 *
 * <p><b>本 Mapper 无自定义 SQL：</b>所有查询通过 Service 层使用 MyBatis-Plus 的 {@code LambdaQueryWrapper}
 * 构造，复杂的权限关联查询（按角色查菜单）走 {@code RolePermissionMapper} 中间表。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>{@code uk_menu_code} — 菜单编码唯一索引
 *   <li>{@code uk_permission_code} — 权限码唯一索引
 *   <li>{@code idx_parent_id} — 父级 ID 索引（树形查询）
 *   <li>{@code idx_sort_order} — 排序字段索引（按 sortOrder 升序）
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.userinfo.infra.entity.MenuDO 菜单实体（含 permCode 字段）
 * @see com.njydsz.userinfo.infra.mapper.RolePermissionMapper 角色-权限关联 Mapper
 */
@Mapper
public interface MenuMapper extends BaseMapper<MenuDO> {}
