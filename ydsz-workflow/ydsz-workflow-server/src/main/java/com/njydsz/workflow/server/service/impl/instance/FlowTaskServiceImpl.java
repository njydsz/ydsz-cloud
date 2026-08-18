package com.njydsz.workflow.server.service.impl.instance;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.lock.annotation.YdszDistributedLock;
import com.njydsz.workflow.domain.dto.FlowInstanceViewDTO;
import com.njydsz.workflow.domain.dto.FlowTaskOperateDTO;
import com.njydsz.workflow.infra.entity.FlowNodeDO;
import com.njydsz.workflow.infra.entity.FlowRunTaskDO;
import com.njydsz.workflow.server.service.FlowTaskService;

/**
 * 待办任务 Service 门面（Facade）
 *
 * <p>对 {@link FlowTaskService} 接口的<b>委托门面实现</b>，原单体实现（1847 行 / 87KB） 已按职责拆分为 4 个子 Service + 1
 * 个共享辅助，本类仅作透明委托。
 *
 * <p><b>子 Service 拆分：</b>
 *
 * <ul>
 *   <li>{@link FlowTaskQueryServiceImpl} — 查询类（待办 / 已办 / 详情 / 统计 / 视图）
 *   <li>{@link FlowTaskCompleteServiceImpl} — 完成类（创建 / 签收 / 通过 / 驳回 / 转办 / 委派 / 跳转 / 超时 / 取消 / 催办）
 *   <li>{@link FlowTaskSignServiceImpl} — 加签减签类（前 / 后加签、减签、追加处理人、已阅、沟通、暂存）
 *   <li>{@link FlowTaskBatchServiceImpl} — 批量操作（批量审批）
 *   <li>{@link com.njydsz.workflow.server.service.impl.instance.FlowTaskSupport} — 跨子 Service 共享的校验
 *       / 审计 / 事件辅助
 * </ul>
 *
 * <p><b>设计动机：</b>
 *
 * <ul>
 *   <li>原文件超过 Checkstyle 2000 行限制，需拆分
 *   <li>原构造函数注入 18 个依赖，违反单一职责原则
 *   <li>拆分子 Service 后各 Service 自行注入所需依赖，构造参数清晰可读
 *   <li>跨 Bean 调用可正确触发 Spring 事务代理（相比原内部自调用语义更明确）
 * </ul>
 *
 * <p><b>事务边界：</b>本门面本身<b>不开启事务</b>，事务由各子 Service 的 {@code @Transactional} 声明。 仅 {@link #claim} /
 * {@link #pass} / {@link #reject} 等关键方法通过 {@link YdszDistributedLock} 注解加分布式锁（{@code
 * ydsz:flow:task:claim:{taskId}} / {@code ydsz:flow:task:operate:{taskId}}）， 防止并发审批导致状态不一致。
 *
 * <p><b>性能优化：</b>门面本身不直接访问 DB，所有数据库操作下沉到子 Service 各自的 {@code @Transactional(readOnly = true)} /
 * {@code @Transactional(rollbackFor = Exception.class)} 上下文，便于只读副本路由和事务粒度控制。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see FlowTaskService 接口定义
 * @see FlowRunTaskDO 待办任务实体
 * @see FlowTaskQueryServiceImpl 查询子服务
 * @see FlowTaskCompleteServiceImpl 完成子服务
 * @see FlowTaskSignServiceImpl 加签减签子服务
 * @see FlowTaskBatchServiceImpl 批量操作子服务
 */
@Service
@RequiredArgsConstructor
public class FlowTaskServiceImpl implements FlowTaskService {

  /** 查询子服务，处理待办/已办/详情/统计等只读查询 */
  private final FlowTaskQueryServiceImpl queryService;

  /** 完成子服务门面，协调创建/签收/通过/驳回/转办/委派等写操作 */
  private final FlowTaskCompleteServiceImpl completeService;

  /** 加签减签子服务，处理前/后加签、减签、追加处理人等 */
  private final FlowTaskSignServiceImpl signService;

  /** 批量操作子服务，处理批量审批 */
  private final FlowTaskBatchServiceImpl batchService;

  // ============================== 创建任务 ==============================

  @Override
  public String createTask(String instanceId, FlowNodeDO node, Map<String, Object> variables) {
    return completeService.createTask(instanceId, node, variables);
  }

  // ============================== 详情查询 ==============================

  @Override
  public FlowRunTaskDO getById(String taskId) {
    return queryService.getById(taskId);
  }

  // ============================== 签收 ==============================

  /** P0-1: 任务签收加分布式锁，防止多人同时签收同一任务 */
  @Override
  @YdszDistributedLock(key = "'flow:task:claim:' + #taskId", waitTime = 3, leaseTime = 30)
  public void claim(String taskId, String userId) {
    completeService.claim(taskId, userId);
  }

  // ============================== 通过 / 驳回 / 转办 / 委派 ==============================

  /** P0-1: 任务通过加分布式锁，防止并发审批导致状态不一致 */
  @Override
  @YdszDistributedLock(key = "'flow:task:op:' + #dto.taskId", waitTime = 3, leaseTime = 30)
  public void pass(FlowTaskOperateDTO dto) {
    completeService.pass(dto);
  }

  /** P0-1: 任务驳回加分布式锁 */
  @Override
  @YdszDistributedLock(key = "'flow:task:op:' + #dto.taskId", waitTime = 3, leaseTime = 30)
  public void reject(FlowTaskOperateDTO dto) {
    completeService.reject(dto);
  }

  /** P0-1: 任务转办加分布式锁 */
  @Override
  @YdszDistributedLock(key = "'flow:task:op:' + #dto.taskId", waitTime = 3, leaseTime = 30)
  public void transfer(FlowTaskOperateDTO dto) {
    completeService.transfer(dto);
  }

  /** P0-1: 任务委派加分布式锁 */
  @Override
  @YdszDistributedLock(key = "'flow:task:op:' + #dto.taskId", waitTime = 3, leaseTime = 30)
  public void delegate(FlowTaskOperateDTO dto) {
    completeService.delegate(dto);
  }

  // ============================== 取消 / 催办 / 跳转 / 超时 ==============================

  @Override
  public void cancelByInstance(String instanceId, String taskStatus) {
    completeService.cancelByInstance(instanceId, taskStatus);
  }

  @Override
  public List<String> urge(String instanceId, String operatorId, String comment) {
    return completeService.urge(instanceId, operatorId, comment);
  }

  /** P2-3 (GAP-13): 节点级催办 */
  @Override
  public List<String> urgeByNode(
      String instanceId, String nodeCode, String operatorId, String comment) {
    return completeService.urgeByNode(instanceId, nodeCode, operatorId, comment);
  }

  /** P0-1: 自由跳转加分布式锁 */
  @Override
  @YdszDistributedLock(key = "'flow:task:op:' + #dto.taskId", waitTime = 3, leaseTime = 30)
  public void jump(FlowTaskOperateDTO dto) {
    completeService.jump(dto);
  }

  @Override
  public void timeoutTask(String taskId, String reason) {
    completeService.timeoutTask(taskId, reason);
  }

  // ============================== 待办 / 已办 / 实例列表 ==============================

  @Override
  public List<FlowRunTaskDO> listPendingByInstance(String instanceId) {
    return queryService.listPendingByInstance(instanceId);
  }

  @Override
  public List<FlowRunTaskDO> listTodoByAssignee(String assigneeId, String tenantId) {
    return queryService.listTodoByAssignee(assigneeId, tenantId);
  }

  @Override
  public PageResponse<List<FlowRunTaskDO>> listTodoByAssigneePage(
      String assigneeId, String tenantId, int page, int size) {
    return queryService.listTodoByAssigneePage(assigneeId, tenantId, page, size);
  }

  @Override
  public List<FlowRunTaskDO> listDoneByAssignee(String assigneeId, String tenantId) {
    return queryService.listDoneByAssignee(assigneeId, tenantId);
  }

  @Override
  public PageResponse<List<FlowRunTaskDO>> listDoneByAssigneePage(
      String assigneeId, String tenantId, int page, int size) {
    return queryService.listDoneByAssigneePage(assigneeId, tenantId, page, size);
  }

  @Override
  public List<FlowRunTaskDO> listTodoByUser(
      String userId, List<String> roleCodes, List<String> deptIds, String tenantId) {
    return queryService.listTodoByUser(userId, roleCodes, deptIds, tenantId);
  }

  // ============================== 加签 / 减签 / 追加处理人 ==============================

  /** P0-1: 前加签加分布式锁 */
  @Override
  @YdszDistributedLock(key = "'flow:task:op:' + #dto.taskId", waitTime = 3, leaseTime = 30)
  public void countersignBefore(FlowTaskOperateDTO dto) {
    signService.countersignBefore(dto);
  }

  /** P0-1: 后加签加分布式锁 */
  @Override
  @YdszDistributedLock(key = "'flow:task:op:' + #dto.taskId", waitTime = 3, leaseTime = 30)
  public void countersignAfter(FlowTaskOperateDTO dto) {
    signService.countersignAfter(dto);
  }

  /** GAP-P0-3: 并加签 — 委托给 signService */
  @Override
  @YdszDistributedLock(key = "'flow:task:op:' + #dto.taskId", waitTime = 3, leaseTime = 30)
  public void countersignParallel(FlowTaskOperateDTO dto) {
    signService.countersignParallel(dto);
  }

  /** P0-1: 减签加分布式锁 */
  @Override
  @YdszDistributedLock(key = "'flow:task:op:' + #dto.taskId", waitTime = 3, leaseTime = 30)
  public void countersignRemove(FlowTaskOperateDTO dto) {
    signService.countersignRemove(dto);
  }

  /** P0-1: 追加处理人加分布式锁 */
  @Override
  @YdszDistributedLock(key = "'flow:task:op:' + #dto.taskId", waitTime = 3, leaseTime = 30)
  public void addApprover(FlowTaskOperateDTO dto) {
    signService.addApprover(dto);
  }

  /** P1-3: 取回审批 — 加分布式锁防止并发 */
  @Override
  @YdszDistributedLock(key = "'flow:task:retract:' + #hisTaskId", waitTime = 3, leaseTime = 30)
  public String retract(String hisTaskId, String operatorId, String comment) {
    return completeService.retract(hisTaskId, operatorId, comment);
  }

  /** P2-1: 任务级挂起 — 加分布式锁防止并发 */
  @Override
  @YdszDistributedLock(key = "'flow:task:op:' + #taskId", waitTime = 3, leaseTime = 30)
  public void suspendTask(String taskId, String operatorId, String reason) {
    completeService.suspendTask(taskId, operatorId, reason);
  }

  /** P2-1: 任务级激活 — 加分布式锁防止并发 */
  @Override
  @YdszDistributedLock(key = "'flow:task:op:' + #taskId", waitTime = 3, leaseTime = 30)
  public void activateTask(String taskId, String operatorId) {
    completeService.activateTask(taskId, operatorId);
  }

  // ============================== 已阅 / 沟通 / 暂存 ==============================

  @Override
  public void markRead(String taskId, String userId) {
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
  public void batchPass(List<String> taskIds, String userId, String comment) {
    batchService.batchPass(taskIds, userId, comment);
  }

  /** P1-4: 批量驳回 */
  @Override
  public void batchReject(
      List<String> taskIds, String userId, String comment, String targetNodeCode) {
    batchService.batchReject(taskIds, userId, comment, targetNodeCode);
  }

  /** P1-4: 批量转办 */
  @Override
  public void batchTransfer(
      List<String> taskIds,
      String userId,
      String comment,
      String targetUserId,
      String targetUserName) {
    batchService.batchTransfer(taskIds, userId, comment, targetUserId, targetUserName);
  }

  /** P1-4: 批量催办 */
  @Override
  public int batchUrge(List<String> instanceIds, String operatorId, String comment) {
    return batchService.batchUrge(instanceIds, operatorId, comment);
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
  public List<FlowRunTaskDO> listOverdue(String assigneeId, String tenantId, int limit) {
    return queryService.listOverdue(assigneeId, tenantId, limit);
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
  public PageResponse<List<FlowRunTaskDO>> listDoneByAssigneePageMulti(
      String assigneeId,
      String businessType,
      String flowCode,
      LocalDateTime startTime,
      LocalDateTime endTime,
      String tenantId,
      int page,
      int size) {
    return queryService.listDoneByAssigneePageMulti(
        assigneeId, businessType, flowCode, startTime, endTime, tenantId, page, size);
  }
}
