package com.njydsz.userinfo.infra.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.njydsz.userinfo.infra.entity.UserRole;

/**
 * 用户-角色关联表 Mapper
 *
 * <p>对应数据表 <code>ydsz_user_role</code>，存储用户与角色的多对多关联。
 *
 * <p>一个用户可拥有多个角色（叠加权限），角色由 {@code RoleMapper} 维护，权限由 {@code RolePermissionMapper} 维护。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_user_role — (userId+roleId) 唯一索引
 *   <li>idx_user_id — 用户维度查询索引（查用户的角色）
 *   <li>idx_role_id — 角色维度查询索引（查角色的用户）
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.userinfo.infra.entity.UserRole 用户-角色关联实体
 * @see com.njydsz.userinfo.server.service.UserRoleService 用户-角色 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface UserRoleMapper extends BaseMapper<UserRole> {

  /**
   * 批量插入用户-角色关联。
   *
   * @param list 关联列表
   * @return 插入行数
   */
  @Insert(
      "<script>"
          + "INSERT INTO ydsz_user_role (id, user_id, role_id, tenant_id, deleted) VALUES "
          + "<foreach collection='list' item='item' separator=','>"
          + "(#{item.id}, #{item.userId}, #{item.roleId}, #{item.tenantId}, 0)"
          + "</foreach>"
          + "</script>")
  int batchInsert(@Param("list") List<UserRole> list);
}
