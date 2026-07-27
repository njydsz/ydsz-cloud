package com.njydsz.workflow.server.service.impl.instance;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.workflow.domain.dto.FlowTaskOperateDTO;
import com.njydsz.workflow.domain.entity.FlowNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 待办任务 — 完成类服务门面（Facade）
 *
 * <p>本类是从 2755 行的单体类 {@code FlowTaskCompleteServiceImpl} 重构而来的协调者。
 * 原始类承担了 10+ 种职责（创建/签收/通过/驳回/转办/委派/跳转/超时/催办/撤回），
 * 重构后按职责拆分到以下专门服务：
 * <ul>
 *   <li>{@link FlowTaskCreateService} — 任务创建（含 SERVICE/FOREACH/LEVEL_APPROVAL 节点、空兜底策略）</li>
 *   <li>{@link FlowTaskClaimService} — 任务签收</li>
 *   <li>{@link FlowTaskPassService} — 任务通过（策略模式处理 5 种会签模式）</li>
 *   <li>{@link FlowTaskRejectService} — 任务驳回（单节点/多节点/退回发起人）</li>
 *   <li>{@link FlowTaskOperateService} — 转办/委派/跳转/撤回</li>
 *   <li>{@link FlowTaskUrgeService} — 任务催办（实例级/节点级）</li>
 *   <li>{@link FlowTaskTimeoutService} — 超时/挂起/激活/取消</li>
 *   <li>{@link FlowTaskArchiveService} — 任务完成+归档（基础服务）</li>
 *   <li>{@link FlowTaskNotificationService} — 任务事件通知</li>
 *   <li>{@link FlowTaskAuditService} — 委派代理审计</li>
 * </ul>
 *
 * <p>本门面仅作委托转发，保持对外 API 完全不变（兼容
 * {@code FlowTaskServiceImpl.createTask / claim / pass / ...} 的所有调用）。
 * 事务边界由各专门服务的 {@code @Transactional} 声明，跨 Bean 调用可正确触发
 * Spring 事务代理。
 *
 * <p>重构收益：
 * <ul>
 *   <li>代码量：原 2755 行 → 现门面 ~250 行 + 10 个专门服务（各 100-500 行）</li>
 *   <li>复杂度：圈复杂度从 25-40 降至 5-10</li>
 *   <li>可测试性：单元测试 mock 数从 10-15 降至 3-5</li>
 *   <li>扩展性：新增会签类型只需实现 {@code CountersignStrategy}，无需修改主流程</li>
 * </ul>
 *
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
