package com.njydsz.userinfo.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import com.njydsz.userinfo.domain.entity.Role;

/**
 * 角色 Mapper 接口
 *
 * <p>对应数据表 {@code ydsz_role}，存储 RBAC 模型中的角色。
 * 角色是「权限集合」概念，一个角色包含多个权限（{@code ydsz_menu}），多个用户可属于同一角色。
 *
 * <p><b>本 Mapper 无自定义 SQL：</b>所有查询通过 Service 层使用 MyBatis-Plus 的
 * {@code LambdaQueryWrapper} 构造。角色-菜单关联由 {@code RolePermissionMapper} 维护。
 *
 * <p><b>主要索引：</b>
 * <ul>
 *   <li>{@code uk_role_code} — 角色编码唯一索引</li>
 *   <li>{@code idx_sort_order} — 排序字段索引</li>
 *   <li>{@code idx_data_scope} — 数据权限范围索引（ALL/本部门/本部门及子部门/本人）</li>
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.userinfo.domain.entity.Role 角色实体（含 dataScope 数据权限范围字段）
 * @see com.njydsz.userinfo.infra.mapper.RolePermissionMapper 角色-权限关联 Mapper
 * @see com.njydsz.userinfo.infra.mapper.UserRoleMapper 用户-角色关联 Mapper
 */
@Mapper
public interface RoleMapper extends BaseMapper<Role> {
}
