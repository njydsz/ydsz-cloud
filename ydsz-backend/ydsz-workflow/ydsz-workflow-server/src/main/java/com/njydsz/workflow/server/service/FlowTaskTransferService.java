package com.njydsz.workflow.server.service;

/**
 * 流程任务转交服务 — 跨模块事件驱动的任务转交能力。
 *
 * <p>当用户禁用、组织架构变更等事件发生时，由 {@code CrossModuleEventListener}
 * 调用本服务，将该用户名下的待办任务转交给代理人或上级，确保审批流程不中断。
 *
 * <p>核心场景：
 * <ul>
 *   <li>用户禁用 → 转交该用户所有待办任务</li>
 *   <li>组织架构变更 → 批量调整涉及部门下的审批人</li>
 *   <li>项目立项创建 → 自动创建审批流程实例</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface FlowTaskTransferService {

    /**
     * 用户禁用时转交待办任务。
     *
     * <p>将该用户名下所有 PENDING 状态的待办任务转交给指定代理人。
     * 如果代理人为空，则尝试查找该用户的上级作为默认转交目标。
     *
     * @param disabledUserId  被禁用的用户 ID
     * @param transferToUserId 转交目标用户 ID（可为空，空时自动查找上级）
     */
    void transferTasksByUserDisable(String disabledUserId, String transferToUserId);

    /**
     * 组织架构变更时批量调整审批人。
     *
     * <p>当部门合并/拆分/撤销时，批量调整该部门下所有在途流程的审批人配置，
     * 确保审批人指向有效的组织节点。
     *
     * @param deptId     发生变更的部门 ID
     * @param changeType 变更类型（MERGE/SPLIT/DISBAND/RENAME）
     */
    void adjustApproversByOrgChange(String deptId, String changeType);

    /**
     * 项目立项创建时自动创建审批流程实例。
     *
     * <p>根据项目类型匹配对应的流程模板，自动发起审批流程。
     *
     * @param projectId   项目编号
     * @param projectName 项目名称
     * @param managerId   项目经理 ID
     */
    void createInitiationApprovalFlow(String projectId, String projectName, String managerId);
}
