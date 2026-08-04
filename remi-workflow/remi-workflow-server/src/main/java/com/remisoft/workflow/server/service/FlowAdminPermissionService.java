package com.remisoft.workflow.server.service;

import java.util.List;

/**
 * 流程管理员权限服务 — 基于角色的流程管理权限校验
 *
 * <p>对标钉钉/飞书「流程管理员」体系。通过角色编码（FLOW_ADMIN / FLOW_DESIGNER / FLOW_AUDITOR）
 * 实现细粒度的流程管理权限控制，区别于 RBAC 权限中心的全局权限，专注于流程引擎内部的角色判定。
 *
 * <p><b>角色定义：</b>
 * <ul>
 *   <li>{@code FLOW_ADMIN} — 流程管理员：可管理所有流程（终止/挂起/跳转/回滚）</li>
 *   <li>{@code FLOW_DESIGNER} — 流程设计器：可编辑/发布流程定义（部署/版本管理）</li>
 *   <li>{@code FLOW_AUDITOR} — 流程审计员：可查看所有实例和审计日志（只读）</li>
 * </ul>
 *
 * <p><b>权限叠加规则：</b>流程创建者默认拥有该流程的管理和设计权限（不需额外角色），
 * 管理员角色在创建者权限基础上生效。
 *
 * @author remi-team
 * @since 1.0.0
 */


public interface FlowAdminPermissionService {

    /**
     * 检查用户是否拥有指定角色。
     *
     * @param userId   用户 ID
     * @param roleCode 角色编码（FLOW_ADMIN / FLOW_DESIGNER / FLOW_AUDITOR）
     * @return true 如果用户拥有该角色
     */
    boolean hasRole(String userId, String roleCode);

    /**
     * 检查用户是否为流程管理员（拥有 FLOW_ADMIN 角色）。
     *
     * @param userId 用户 ID
     * @return true 如果是管理员
     */
    boolean isAdmin(String userId);

    /**
     * 检查用户是否可以管理指定流程（管理员或流程创建者）。
     *
     * @param userId     用户 ID
     * @param flowCode   流程编码
     * @return true 如果有管理权限
     */
    boolean canManageFlow(String userId, String flowCode);

    /**
     * 检查用户是否可以设计（编辑）指定流程。
     *
     * @param userId     用户 ID
     * @param flowCode   流程编码
     * @return true 如果有设计权限
     */
    boolean canDesignFlow(String userId, String flowCode);

    /**
     * 检查用户是否可以审计（查看所有实例和审计日志）。
     *
     * @param userId 用户 ID
     * @return true 如果有审计权限
     */
    boolean canAudit(String userId);

    /**
     * 获取用户拥有的角色列表。
     *
     * @param userId 用户 ID
     * @return 角色编码列表
     */
    List<String> listUserRoles(String userId);

    /**
     * 授予用户角色。
     *
     * @param userId   用户 ID
     * @param roleCode 角色编码
     * @param tenantId 租户 ID
     */
    void grantRole(String userId, String roleCode, String tenantId);

    /**
     * 撤销用户角色。
     *
     * @param userId   用户 ID
     * @param roleCode 角色编码
     */
    void revokeRole(String userId, String roleCode);
}
