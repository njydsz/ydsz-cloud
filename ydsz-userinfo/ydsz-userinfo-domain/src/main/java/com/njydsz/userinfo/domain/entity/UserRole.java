package com.njydsz.userinfo.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 用户-角色关联实体
 *
 * <p>对应数据库表 {@code ydsz_user_role}，是 RBAC 模型中连接用户与角色的多对多中间表。 一个用户可拥有多个角色（叠加权限），一个角色可被分配给多个用户。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>采用「关系实体」模式（带审计字段），而非纯连接表，便于追溯授权历史
 *   <li>支持角色叠加：用户的多角色权限取并集（前提是任一角色未被禁用）
 *   <li>由 {@link com.njydsz.userinfo.web.controller.UserAccountController#assignRoles} 维护
 * </ul>
 *
 * <p><b>典型使用：</b>
 *
 * <pre>{@code
 * // 查询用户所有角色 ID
 * List<String> roleIds = userRoleMapper.selectList(
 *     new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId)
 * ).stream().map(UserRole::getRoleId).collect(Collectors.toList());
 * }</pre>
 *
 * <p><b>索引设计：</b>普通索引 {@code idx_user_id}（{@code user_id}）、 {@code idx_role_id}（{@code
 * role_id}）。如需强唯一性（用户-角色一一对应）， 可加唯一索引 {@code uk_user_role}（{@code user_id, role_id}）。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see UserAccount 用户实体
 * @see Role 角色实体
 * @see com.njydsz.userinfo.web.controller.UserAccountController 用户 Controller（含 {@code assignRoles}
 *     接口）
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_user_role")
public class UserRole extends MpBaseEntity<String> {

  /** 用户 ID，关联 {@code UserAccount.id} */
  private String userId;

  /** 角色 ID，关联 {@code Role.id} */
  private String roleId;
}
