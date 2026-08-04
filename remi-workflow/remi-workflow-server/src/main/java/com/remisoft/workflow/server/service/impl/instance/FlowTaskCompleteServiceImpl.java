package com.remisoft.workflow.server.service.impl.instance;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.remisoft.workflow.domain.dto.FlowTaskOperateDTO;
import com.remisoft.workflow.domain.entity.FlowNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 流程任务完成服务实现。
 *
 * <p>封装任务「同意/拒绝/驳回/转交/委派/加签/减签」等完成动作的领域逻辑：
 *
 * <p>状态机校验、下一节点路由、SLA 重置、催办任务调度、审批意见留痕。
 *
 * @author remi-team
 * @since 1.0.0
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class FlowTaskCompleteServiceImpl {

    /** 任务创建子服务，处理 SERVICE/FOREACH/LEVEL_APPROVAL 节点任务生成 */
    private final FlowTaskCreateService createService;
    /** 任务签收子服务，处理候选任务认领 */
    private final FlowTaskClaimService claimService;
    /** 任务通过子服务，策略模式处理 5 种会签模式 */
    private final FlowTaskPassService passService;
    /** 任务驳回子服务，处理单节点/多节点/退回发起人 */
    private final FlowTaskRejectService rejectService;
    /** 任务操作子服务，处理转办/委派/跳转/撤回 */
    private final FlowTaskOperateService operateService;
    /** 任务催办子服务，处理实例级/节点级催办 */
    private final FlowTaskUrgeService urgeService;
    /** 超时/挂起/激活/取消子服务 */
    private final FlowTaskTimeoutService timeoutService;

    // ============================== 创建任务 ==============================

    /**
     * 创建任务（向后兼容重载）
     */
    @Transactional(rollbackFor = Exception.class)
    public String createTask(String instanceId, FlowNode node, Map<String, Object> variables) {
        return createService.createTask(instanceId, node, variables);
    }

    /**
     * 创建任务（支持显式指定办理人）
     */
    @Transactional(rollbackFor = Exception.class)
    public String createTask(String instanceId, FlowNode node, Map<String, Object> variables,
                             List<String> explicitAssignees) {
        return createService.createTask(instanceId, node, variables, explicitAssignees);
    }

    // ============================== 签收 ==============================

    /**
     * 签收
     */
    @Transactional(rollbackFor = Exception.class)
    public void claim(String taskId, String userId) {
        claimService.claim(taskId, userId);
    }

    // ============================== 通过 ==============================

    /**
     * 通过
     */
    @Transactional(rollbackFor = Exception.class)
    public void pass(FlowTaskOperateDTO dto) {
        passService.pass(dto);
    }

    // ============================== 驳回 ==============================

    /**
     * 驳回
     */
    @Transactional(rollbackFor = Exception.class)
    public void reject(FlowTaskOperateDTO dto) {
        rejectService.reject(dto);
    }

    // ============================== 转办 / 委派 / 跳转 / 撤回 ==============================

    /**
     * 转办
     */
    @Transactional(rollbackFor = Exception.class)
    public void transfer(FlowTaskOperateDTO dto) {
        operateService.transfer(dto);
    }

    /**
     * 委派
     */
    @Transactional(rollbackFor = Exception.class)
    public void delegate(FlowTaskOperateDTO dto) {
        operateService.delegate(dto);
    }

    /**
     * 自由跳转
     */
    @Transactional(rollbackFor = Exception.class)
    public void jump(FlowTaskOperateDTO dto) {
        operateService.jump(dto);
    }

    /**
     * 取回（已审批后取回）
     */
    @Transactional(rollbackFor = Exception.class)
    public String retract(String hisTaskId, String operatorId, String comment) {
        return operateService.retract(hisTaskId, operatorId, comment);
    }

    // ============================== 催办 ==============================

    /**
     * 实例级催办
     */
    public List<String> urge(String instanceId, String operatorId, String comment) {
        return urgeService.urge(instanceId, operatorId, comment);
    }

    /**
     * 节点级催办
     */
    public List<String> urgeByNode(String instanceId, String nodeCode, String operatorId, String comment) {
        return urgeService.urgeByNode(instanceId, nodeCode, operatorId, comment);
    }

    // ============================== 超时 / 挂起 / 激活 / 取消 ==============================

    /**
     * 标记任务超时
     */
    @Transactional(rollbackFor = Exception.class)
    public void timeoutTask(String taskId, String reason) {
        timeoutService.timeoutTask(taskId, reason);
    }

    /**
     * 任务级挂起
     */
    @Transactional(rollbackFor = Exception.class)
    public void suspendTask(String taskId, String operatorId, String reason) {
        timeoutService.suspendTask(taskId, operatorId, reason);
    }

    /**
     * 任务级激活
     */
    @Transactional(rollbackFor = Exception.class)
    public void activateTask(String taskId, String operatorId) {
        timeoutService.activateTask(taskId, operatorId);
    }

    /**
     * 取消某实例全部 PENDING 任务
     */
    public void cancelByInstance(String instanceId, String taskStatus) {
        timeoutService.cancelByInstance(instanceId, taskStatus);
    }
}
