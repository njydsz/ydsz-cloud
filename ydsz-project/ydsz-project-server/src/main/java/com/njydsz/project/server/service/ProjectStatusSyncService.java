package com.njydsz.project.server.service;

/**
 * 项目状态同步服务 — 跨模块事件驱动的项目状态管理。
 *
 * <p>当工作流审批结果、系统配置变更等事件发生时，由 {@code CrossModuleEventListener}
 * 调用本服务，同步更新项目/合同状态或刷新缓存。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface ProjectStatusSyncService {

    /**
     * 工作流审批通过 — 根据业务类型更新项目状态。
     *
     * @param businessType 业务类型（INITIATION/CHANGE/CLOSEOUT/CONTRACT）
     * @param businessId   业务 ID（项目编号/合同编号）
     */
    void onFlowApproved(String businessType, String businessId);

    /**
     * 工作流审批驳回 — 回滚项目状态。
     *
     * @param businessType 业务类型
     * @param businessId   业务 ID
     */
    void onFlowRejected(String businessType, String businessId);

    /**
     * 用户登录 — 预热项目经理的项目列表缓存。
     *
     * @param userId 用户 ID
     */
    void preheatProjectCache(String userId);

    /**
     * 系统配置变更 — 刷新项目参数缓存。
     *
     * @param configKey 变更的配置键
     */
    void refreshConfigCache(String configKey);
}
