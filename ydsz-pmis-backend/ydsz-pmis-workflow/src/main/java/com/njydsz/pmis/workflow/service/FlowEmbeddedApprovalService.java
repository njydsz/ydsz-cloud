package com.njydsz.pmis.workflow.service;

import com.njydsz.pmis.workflow.dto.EmbeddedApprovalActionDTO;
import com.njydsz.pmis.workflow.dto.EmbeddedApprovalViewDTO;

/**
 * P2-2 嵌入式审批服务
 *
 * <p>业务页（项目立项/合同/工时/采购等）通过本服务拉取嵌入式审批面板数据，
 * 一次性获得流程实例/流程图/当前待办/历史轨迹，并支持快捷操作。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface FlowEmbeddedApprovalService {

    /**
     * 加载嵌入式审批面板（聚合查询）
     *
     * <p>业务页只需要传入 businessType+businessId+currentUserId，
     * 不需要先查 taskId、不需要单独拉流程图/历史轨迹。
     *
     * @param businessType 业务类型
     * @param businessId   业务 ID
     * @param userId       当前用户 ID（用于判定 myRole / mine / actions）
     * @return 嵌入式审批面板视图（流程未启动时仍返回 DTO，instance 为空）
     */
    EmbeddedApprovalViewDTO loadPanel(String businessType, String businessId, String userId);

    /**
     * 嵌入式快捷操作（业务页不需要关心 taskId）
     *
     * <p>根据 action 自动找到当前用户对应的待办任务并执行：
     * <ul>
     *   <li>PASS — 通过（找到当前用户 mine=true 的待办任务）</li>
     *   <li>REJECT — 驳回（找到 mine=true 的待办任务）</li>
     *   <li>TRANSFER — 转办（需 targetUserId）</li>
     *   <li>DELEGATE — 委派（需 targetUserId）</li>
     *   <li>URGE — 催办（无需 mine）</li>
     *   <li>WITHDRAW — 撤回（仅发起人可执行）</li>
     * </ul>
     *
     * @param dto 嵌入式快捷操作参数
     */
    void quickAction(EmbeddedApprovalActionDTO dto);
}
