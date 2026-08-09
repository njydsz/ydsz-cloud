package com.njydsz.workflow.server.service.impl;

import org.springframework.stereotype.Service;

import com.njydsz.workflow.server.service.FlowTaskTransferService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 流程任务转交服务骨架实现。
 *
 * <p>当前为骨架实现，后续按 P1 优先级逐步填充业务逻辑：
 * <ul>
 *   <li>transferTasksByUserDisable → 查询待办任务 + 调用 FlowTaskOperateService 转交</li>
 *   <li>adjustApproversByOrgChange → 批量查询涉及流程 + 更新审批人配置</li>
 *   <li>createInitiationApprovalFlow → 调用 WorkflowServiceClient + FlowInstanceService 启动流程</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowTaskTransferServiceImpl implements FlowTaskTransferService {

    @Override
    public void transferTasksByUserDisable(String disabledUserId, String transferToUserId) {
        log.info("[FlowTaskTransfer] 用户禁用任务转交: disabledUserId={}, transferToUserId={}",
                disabledUserId, transferToUserId);
        // TODO P1: 查询 disabledUserId 的所有 PENDING 待办任务，逐个调用 FlowTaskOperateService.transfer 转交
    }

    @Override
    public void adjustApproversByOrgChange(String deptId, String changeType) {
        log.info("[FlowTaskTransfer] 组织架构变更审批人调整: deptId={}, changeType={}",
                deptId, changeType);
        // TODO P1: 查询涉及 deptId 的所有在途流程，批量更新审批人配置
    }

    @Override
    public void createInitiationApprovalFlow(String projectId, String projectName, String managerId) {
        log.info("[FlowTaskTransfer] 项目立项自动创建审批流程: projectId={}, projectName={}, managerId={}",
                projectId, projectName, managerId);
        // TODO P1: 根据项目类型匹配流程模板，调用 FlowInstanceService.start 启动审批流程
    }
}
