package com.njydsz.pmis.workflow.service.impl;

import com.njydsz.pmis.common.api.PageResult;
import com.njydsz.pmis.workflow.dto.FlowInstanceViewDTO;
import com.njydsz.pmis.workflow.dto.FlowTaskOperateDTO;
import com.njydsz.pmis.workflow.entity.FlowNodeDO;
import com.njydsz.pmis.workflow.entity.FlowRunTaskDO;
import com.njydsz.pmis.workflow.service.FlowTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 待办任务 Service 门面（Facade）
 *
 * <p>原 {@code FlowTaskServiceImpl} 单体实现已按职责拆分为 4 个子 Service + 1 个共享辅助：
 * <ul>
 *   <li>{@link FlowTaskQueryServiceImpl} — 查询类（待办/已办/详情/统计/视图）</li>
 *   <li>{@link FlowTaskCompleteServiceImpl} — 完成类（创建/签收/通过/驳回/转办/委派/跳转/超时/取消/催办）</li>
 *   <li>{@link FlowTaskSignServiceImpl} — 加签减签类（前/后加签、减签、追加处理人、已阅、沟通、暂存）</li>
 *   <li>{@link FlowTaskBatchServiceImpl} — 批量操作（批量审批）</li>
 *   <li>{@link FlowTaskSupport} — 跨子 Service 共享的任务校验/审计/事件辅助</li>
 * </ul>
 *
 * <p>本类仅作委托门面：实现 {@link FlowTaskService} 接口，所有方法转发到对应子 Service，
 * 保持对外接口与行为完全不变。事务边界由各子 Service 的 {@code @Transactional} 声明，
 * 跨 Bean 调用可正确触发 Spring 事务代理（相比原内部自调用语义更明确）。
 *
 * <p>拆分背景：原文件 1847 行 / 87KB，远超 Checkstyle 2000 行限制，且构造函数注入 18 个依赖。
 * 拆分后本门面仅持有 4 个子 Service 引用，各子 Service 各自注入所需依赖。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Service
@RequiredArgsConstructor
public class FlowTaskServiceImpl implements FlowTaskService {

    private final FlowTaskQueryServiceImpl queryService;
    private final FlowTaskCompleteServiceImpl completeService;
    private final FlowTaskSignServiceImpl signService;
    private final FlowTaskBatchServiceImpl batchService;

    // ============================== 创建任务 ==============================

    @Override
    public Long createTask(Long instanceId, FlowNodeDO node, Map<String, Object> variables) {
        return completeService.createTask(instanceId, node, variables);
    }

    // ============================== 详情查询 ==============================

    @Override
    public FlowRunTaskDO getById(Long taskId) {
        return queryService.getById(taskId);
    }

    // ============================== 签收 ==============================

    @Override
    public void claim(Long taskId, Long userId) {
        completeService.claim(taskId, userId);
    }

    // ============================== 通过 / 驳回 / 转办 / 委派 ==============================

    @Override
    public void pass(FlowTaskOperateDTO dto) {
        completeService.pass(dto);
    }

    @Override
    public void reject(FlowTaskOperateDTO dto) {
        completeService.reject(dto);
    }

    @Override
    public void transfer(FlowTaskOperateDTO dto) {
        completeService.transfer(dto);
    }

    @Override
    public void delegate(FlowTaskOperateDTO dto) {
        completeService.delegate(dto);
    }

    // ============================== 取消 / 催办 / 跳转 / 超时 ==============================

    @Override
    public void cancelByInstance(Long instanceId, String taskStatus) {
        completeService.cancelByInstance(instanceId, taskStatus);
    }

    @Override
    public List<String> urge(Long instanceId, Long operatorId, String comment) {
        return completeService.urge(instanceId, operatorId, comment);
    }

    @Override
    public void jump(FlowTaskOperateDTO dto) {
        completeService.jump(dto);
    }

    @Override
    public void timeoutTask(Long taskId, String reason) {
        completeService.timeoutTask(taskId, reason);
    }

    // ============================== 待办 / 已办 / 实例列表 ==============================

    @Override
    public List<FlowRunTaskDO> listPendingByInstance(Long instanceId) {
        return queryService.listPendingByInstance(instanceId);
    }

    @Override
    public List<FlowRunTaskDO> listTodoByAssignee(String assigneeId, String tenantId) {
        return queryService.listTodoByAssignee(assigneeId, tenantId);
    }

    @Override
    public PageResult<FlowRunTaskDO> listTodoByAssigneePage(String assigneeId, String tenantId,
                                                          int page, int size) {
        return queryService.listTodoByAssigneePage(assigneeId, tenantId, page, size);
    }

    @Override
    public List<FlowRunTaskDO> listDoneByAssignee(String assigneeId, String tenantId) {
        return queryService.listDoneByAssignee(assigneeId, tenantId);
    }

    @Override
    public PageResult<FlowRunTaskDO> listDoneByAssigneePage(String assigneeId, String tenantId,
                                                          int page, int size) {
        return queryService.listDoneByAssigneePage(assigneeId, tenantId, page, size);
    }

    @Override
    public List<FlowRunTaskDO> listTodoByUser(Long userId, List<String> roleCodes,
                                            List<String> deptIds, String tenantId) {
        return queryService.listTodoByUser(userId, roleCodes, deptIds, tenantId);
    }

    // ============================== 加签 / 减签 / 追加处理人 ==============================

    @Override
    public void countersignBefore(FlowTaskOperateDTO dto) {
        signService.countersignBefore(dto);
    }

    @Override
    public void countersignAfter(FlowTaskOperateDTO dto) {
        signService.countersignAfter(dto);
    }

    /** GAP-P0-3: 并加签 — 委托给 signService */
    @Override
    public void countersignParallel(FlowTaskOperateDTO dto) {
        signService.countersignParallel(dto);
    }

    @Override
    public void countersignRemove(FlowTaskOperateDTO dto) {
        signService.countersignRemove(dto);
    }

    @Override
    public void addApprover(FlowTaskOperateDTO dto) {
        signService.addApprover(dto);
    }

    // ============================== 已阅 / 沟通 / 暂存 ==============================

    @Override
    public void markRead(Long taskId, Long userId) {
        signService.markRead(taskId, userId);
    }

    @Override
    public void communicate(FlowTaskOperateDTO dto) {
        signService.communicate(dto);
    }

    @Override
    public void saveDraft(FlowTaskOperateDTO dto) {
        signService.saveDraft(dto);
    }

    // ============================== 批量审批 ==============================

    @Override
    public void batchPass(List<Long> taskIds, Long userId, String comment) {
        batchService.batchPass(taskIds, userId, comment);
    }

    // ============================== 视图转换 / 统计 ==============================

    @Override
    public FlowInstanceViewDTO.FlowTaskViewDTO toView(FlowRunTaskDO task) {
        return queryService.toView(task);
    }

    @Override
    public List<Map<String, Object>> nodeDurationStats(String flowCode, String tenantId) {
        return queryService.nodeDurationStats(flowCode, tenantId);
    }

    @Override
    public List<FlowRunTaskDO> listOverdue(String assigneeId, String tenantId) {
        return queryService.listOverdue(assigneeId, tenantId);
    }

    @Override
    public long countOverdue(String assigneeId, String tenantId) {
        return queryService.countOverdue(assigneeId, tenantId);
    }

    @Override
    public long countPending(String tenantId) {
        return queryService.countPending(tenantId);
    }

    @Override
    public PageResult<FlowRunTaskDO> listDoneByAssigneePageMulti(String assigneeId, String businessType,
                                                               String flowCode, LocalDateTime startTime,
                                                               LocalDateTime endTime, String tenantId,
                                                               int page, int size) {
        return queryService.listDoneByAssigneePageMulti(assigneeId, businessType, flowCode,
                startTime, endTime, tenantId, page, size);
    }
}
