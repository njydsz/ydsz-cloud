package com.njydsz.pmis.workflow.server.service;

import java.util.List;

/**
 * 流程管理员权限服务（P1-6）
 *
 * <p>对标钉钉/飞书审批的"管理员权限体系"能力，支持：
 * <ul>
 *   <li>角色体系：FLOW_ADMIN（流程管理员）/ FLOW_DESIGNER（流程设计者）/ FLOW_AUDITOR（流程审计员）</li>
 *   <li>数据权限：管理员可管理所有流程；设计者只能编辑自己创建的流程；审计员只读</li>
 *   <li>操作权限：部署/下线/迁移/终止/管理员转交 等敏感操作需要 ADMIN 角色</li>
 *   <li>委托授权：ADMIN 可将权限临时委托给其他用户</li>
 * </ul>
 *
 * <p>权限数据来源：{@code pmis_flow_admin_role} 表（用户-角色映射）。
 *
 * @since 1.9.0
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
