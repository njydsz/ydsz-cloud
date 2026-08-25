package com.njydsz.workflow.infra.entity;

import java.io.Serial;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 流程管理员角色映射实体（P1-6）
 *
 * <p>对应数据库表 {@code ydsz_flow_admin_role}，存储用户与流程管理员角色的映射关系。 一个用户可拥有多个角色，一个角色可分配给多个用户（多对多）。
 *
 * <p><b>角色编码（{@code roleCode}）：</b>
 *
 * <ul>
 *   <li>{@code FLOW_ADMIN}：流程管理员，可管理所有流程（部署/下线/迁移/终止/管理员转交）
 *   <li>{@code FLOW_DESIGNER}：流程设计者，可设计/编辑流程定义
 *   <li>{@code FLOW_AUDITOR}：流程审计员，可查看所有流程实例和审计日志（只读）
 * </ul>
 *
 * <p><b>权限校验时机：</b>
 *
 * <ul>
 *   <li>{@code @PreAuthorize("hasRole('FLOW_ADMIN')")} — Spring Security 注解
 *   <li>{@code FlowAdminGuard.check(userId, roleCode)} — 业务代码内显式校验
 * </ul>
 *
 * <p><b>授权时效：</b>{@code expireAt} 支持临时授权（如「代班 7 天」）， 校验逻辑会同时检查 {@code enabled=true} 与 {@code
 * expireAt > now()}。
 *
 * <p><b>索引设计：</b>
 *
 * <ul>
 *   <li>唯一索引 {@code uk_user_role}（{@code user_id}, {@code role_code}）
 *   <li>普通索引 {@code idx_role}（{@code role_code}）：按角色查询用户
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.workflow.server.guard.FlowAdminGuard 流程管理员权限校验
 * @see com.njydsz.workflow.domain.enums.FlowRoleCode 流程角色编码枚举
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_flow_admin_role")
public class FlowAdminRole extends MpBaseEntity<String> {

  @Serial private static final long serialVersionUID = 1L;

  /** 用户 ID */
  private String userId;

  /** 角色编码（{@code FLOW_ADMIN} / {@code FLOW_DESIGNER} / {@code FLOW_AUDITOR}） */
  private String roleCode;

  /** 是否启用（{@code false} 表示撤销授权，但保留历史记录） */
  private Boolean enabled;

  /** 授权人 ID（{@code null} 表示系统预置角色） */
  private String grantedBy;

  /** 授权时间 */
  private LocalDateTime grantedAt;

  /** 过期时间（{@code null} 表示永不过期） */
  private LocalDateTime expireAt;
}
