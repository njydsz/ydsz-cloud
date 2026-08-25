package com.njydsz.workflow.server.service.impl.instance;

import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.workflow.domain.dto.FlowInstanceViewDTO;
import com.njydsz.workflow.domain.dto.FlowStartProcessDTO;
import com.njydsz.workflow.domain.query.FlowInstancePageQuery;
import com.njydsz.workflow.domain.vo.FlowInstanceVO;
import com.njydsz.workflow.domain.vo.FlowNodeVO;
import com.njydsz.workflow.server.service.FlowInstanceService;

/**
 * 流程实例 Service 实现（门面模式）
 *
 * <p>对 {@link FlowInstanceService} 接口的完整实现，是工作流引擎<b>运行时核心</b>。
 * 本类作为<b>门面（Facade）</b>，委托给以下 4 个管理器完成实际工作：
 *
 * <ul>
 *   <li>{@link FlowInstanceLifecycleManager} — 实例生命周期（启动/终止/挂起/激活/完成/撤回/回滚/重审，所有写操作）
 *   <li>{@link FlowInstanceBatchOperator} — 批量操作（批量启动/批量终止）
 *   <li>{@link FlowInstanceQueryService} — 查询能力（按ID/按业务/分页/我发起的/可撤回节点/表单渲染）
 *   <li>{@link FlowInstanceVariableManager} — 变量管理（读取/写入/解析）
 * </ul>
 *
 * <p>本类<b>不包含任何业务逻辑</b>，仅做方法转发，保持与 {@link FlowInstanceService} 接口的兼容性。
 *
 * <p><b>核心职责（委托汇总）：</b>
 *
 * <ul>
 *   <li><b>实例生命周期</b>：start / terminate / suspend / activate / complete / recall
 *   <li><b>批量操作</b>：batchStartInstances / batchTerminate
 *   <li><b>查询能力</b>：按 ID / 按业务关联 / 按发起人 / 按状态 / 分页
 *   <li><b>子流程级联</b>：父流程终止时自动终止全部子流程
 *   <li><b>事件总线</b>：异步广播 onInstanceStart / onError 等
 *   <li><b>幂等性</b>：start 基于 (businessType, businessId, tenantId) 复合唯一索引
 * </ul>
 *
 * <p><b>事务边界：</b>所有写方法的事务注解（{@code @Transactional}}）保留在 {@link FlowInstanceLifecycleManager}
 * 和 {@link FlowInstanceVariableManager} 的具体执行方法上，本门面方法不重复声明。
 *
 * <p><b>并发控制：</b>关键操作通过 {@code YdszDistributedLock} 注解保护（在 FlowInstanceLifecycleManager 中声明）。
 *
 * <p><b>多租户：</b>所有查询与写入均按 {@code tenantId} 隔离。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see FlowInstanceService 接口定义
 * @see FlowInstanceLifecycleManager 实例生命周期管理器
 * @see FlowInstanceBatchOperator 批量操作器
 * @see FlowInstanceQueryService 查询服务
 * @see FlowInstanceVariableManager 变量管理器
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowInstanceServiceImpl implements FlowInstanceService {

  /** 流程实例生命周期管理器：负责启动/终止/挂起/激活/完成/撤回/回滚/重审等写操作 */
  private final FlowInstanceLifecycleManager lifecycleManager;

  /** 流程实例批量操作器：负责批量启动/批量终止 */
  private final FlowInstanceBatchOperator batchOperator;

  /** 流程实例查询服务：负责所有只读查询 */
  private final FlowInstanceQueryService queryService;

  /** 流程变量管理器：负责变量读取/写入/解析 */
  private final FlowInstanceVariableManager variableManager;

  /**
   * 启动流程实例
   *
   * @param dto 启动参数 DTO
   * @return 流程实例 ID（新建或已存在）
   */
  @Override
  public String start(FlowStartProcessDTO dto) {
    return lifecycleManager.start(dto);
  }

  /**
   * P2-6: 批量发起流程实例。
   *
   * @param dtos 流程启动参数列表
   * @return Map 包含 successCount / failedCount / instanceIds / failedItems
   */
  @Override
  public Map<String, Object> batchStartInstances(List<FlowStartProcessDTO> dtos) {
    return batchOperator.batchStartInstances(dtos);
  }

  /**
   * 按 ID 查询流程实例
   *
   * @param id 实例 ID
   * @return 流程实例 VO，不存在返回 null
   */
  @Override
  public FlowInstanceVO getById(String id) {
    return queryService.getById(id);
  }

  /**
   * 业务关联查询（通过业务类型 + 业务 ID 查实例）
   *
   * @param businessType 业务类型
   * @param businessId 业务 ID
   * @return 流程实例 VO，未发起时返回 null
   */
  @Override
  public FlowInstanceVO getByBusiness(String businessType, String businessId) {
    return queryService.getByBusiness(businessType, businessId);
  }

  /**
   * 终止流程（管理员强制终止）
   *
   * @param instanceId 实例 ID
   * @param reason 终止原因
   */
  @Override
  public void terminate(String instanceId, String reason) {
    lifecycleManager.terminate(instanceId, reason);
  }

  /**
   * P1-8: 批量终止流程实例（含子流程级联终止）
   *
   * @param instanceIds 实例 ID 列表
   * @param reason 终止原因
   * @return 实际终止的实例数（含级联子流程）
   */
  @Override
  public int batchTerminate(List<String> instanceIds, String reason) {
    return batchOperator.batchTerminate(instanceIds, reason);
  }

  /**
   * 挂起流程实例
   *
   * @param instanceId 实例 ID
   */
  @Override
  public void suspend(String instanceId) {
    lifecycleManager.suspend(instanceId);
  }

  /**
   * 激活流程实例
   *
   * @param instanceId 实例 ID
   */
  @Override
  public void activate(String instanceId) {
    lifecycleManager.activate(instanceId);
  }

  /**
   * 强制完成（驳回到终态时由调用方使用）
   *
   * @param instanceId 实例 ID
   * @param endNodeCode 终止节点编码
   */
  @Override
  public void complete(String instanceId, String endNodeCode) {
    lifecycleManager.complete(instanceId, endNodeCode);
  }

  /**
   * 转化为视图对象（含当前节点任务列表）
   *
   * @param instance 流程实例 VO
   * @param currentTasks 当前节点的待办任务列表
   * @return 流程实例视图 VO
   */
  @Override
  public FlowInstanceViewDTO toView(
      FlowInstanceVO instance, List<FlowInstanceViewDTO.FlowTaskViewDTO> currentTasks) {
    return queryService.toView(instance, currentTasks);
  }

  /**
   * 发起人维度查询（我的发起）
   *
   * @param initiatorId 发起人 ID
   * @param flowStatus 流程状态过滤
   * @return 该发起人指定状态的实例列表
   */
  @Override
  public List<FlowInstanceVO> listByInitiator(String initiatorId, String flowStatus) {
    return queryService.listByInitiator(initiatorId, flowStatus);
  }

  /**
   * P1-8: 撤回流程（仅发起人可撤回，仅运行中可撤回，下一节点未被处理才可撤回）
   *
   * @param instanceId 实例 ID
   * @param initiatorId 发起人 ID
   * @return 是否撤回成功
   */
  @Override
  public boolean recall(String instanceId, String initiatorId) {
    return lifecycleManager.recall(instanceId, initiatorId);
  }

  /**
   * P1-1: 撤回到指定历史节点（对标钉钉/飞书"撤回到指定节点"）。
   *
   * @param instanceId 实例 ID
   * @param initiatorId 发起人 ID
   * @param targetNodeCode 目标节点编码
   * @return 是否撤回成功
   */
  @Override
  public boolean recall(String instanceId, String initiatorId, String targetNodeCode) {
    return lifecycleManager.recall(instanceId, initiatorId, targetNodeCode);
  }

  /**
   * P1-3: 动态追加节点（对标 flowlong executeAppendNodeModel）。
   *
   * @param instanceId     流程实例 ID
   * @param currentNodeCode 当前节点编码
   * @param nodeName       新节点名称
   * @param assigneeType   办理人类型
   * @param assigneeId     办理人 ID
   * @param operatorId     操作人 ID
   * @param comment        追加原因
   * @return 新创建的任务 ID
   */
  @Override
  public String appendNode(
      String instanceId,
      String currentNodeCode,
      String nodeName,
      String assigneeType,
      String assigneeId,
      String operatorId,
      String comment) {
    return lifecycleManager.appendNode(
        instanceId, currentNodeCode, nodeName, assigneeType, assigneeId, operatorId, comment);
  }

  /**
   * P1-1: 查询可撤回的历史节点列表。
   *
   * @param instanceId 实例 ID
   * @param initiatorId 发起人 ID
   * @return 节点列表
   */
  @Override
  public List<Map<String, Object>> listRecallableNodes(String instanceId, String initiatorId) {
    return queryService.listRecallableNodes(instanceId, initiatorId);
  }

  /**
   * P2-3: 回滚已完成的流程实例（撤销）
   *
   * @param instanceId 实例 ID
   * @param operatorId 操作人 ID
   * @param reason 回滚原因
   * @param maxRollbackDays 允许回滚的最大天数
   * @return 是否回滚成功
   */
  @Override
  public boolean rollback(
      String instanceId, String operatorId, String reason, int maxRollbackDays) {
    return lifecycleManager.rollback(instanceId, operatorId, reason, maxRollbackDays);
  }

  /**
   * P3-1: 重审已结束实例（对标 flowlong reopen）。
   *
   * @param instanceId    实例 ID
   * @param operatorId    操作人 ID
   * @param targetNodeCode 目标节点编码
   * @param reason        重审原因
   * @return 是否重审成功
   */
  @Override
  public boolean reopen(
      String instanceId, String operatorId, String targetNodeCode, String reason) {
    return lifecycleManager.reopen(instanceId, operatorId, targetNodeCode, reason);
  }

  /**
   * P2-23: 实例多维分页查询
   *
   * @param query 分页查询参数对象（含筛选条件、分页信息）
   * @return 分页结果（VO）
   */
  @Override
  public PageResponse<List<FlowInstanceVO>> page(FlowInstancePageQuery query) {
    return queryService.page(query);
  }

  /**
   * P2-24: 读取实例流程变量
   *
   * @param instanceId 实例 ID
   * @return 变量 Map，无变量返回空 Map
   */
  @Override
  public Map<String, Object> getVariables(String instanceId) {
    return variableManager.getVariables(instanceId);
  }

  /**
   * P2-24: 合并写入单个变量并持久化
   *
   * @param instanceId 实例 ID
   * @param key 变量名
   * @param value 变量值
   */
  @Override
  public void setVariable(String instanceId, String key, Object value) {
    variableManager.setVariable(instanceId, key, value);
  }

  /**
   * P2-24: 批量合并写入变量并持久化
   *
   * @param instanceId 实例 ID
   * @param variables 变量 Map
   */
  @Override
  public void setVariables(String instanceId, Map<String, Object> variables) {
    variableManager.setVariables(instanceId, variables);
  }

  /**
   * 引擎内部方法：创建第一个待办任务（供 DefaultFlowAdvancer 调用）
   *
   * @param instanceId 流程实例 ID
   * @param startNode 开始节点
   * @param variables 流程变量
   * @return 实例 ID
   */
  public String createFirstTask(
      String instanceId, FlowNodeVO startNode, Map<String, Object> variables) {
    return lifecycleManager.createFirstTask(instanceId, startNode, variables);
  }

  /**
   * 引擎内部方法：推进后批量生成任务（供 DefaultFlowAdvancer / FlowTaskService 调用）
   *
   * @param instanceId 流程实例 ID
   * @param nextNodes 推进后的下一节点列表
   * @param variables 流程变量
   */
  @Override
  public void generateTasksForNodes(
      String instanceId, List<FlowNodeVO> nextNodes, Map<String, Object> variables) {
    lifecycleManager.generateTasksForNodes(instanceId, nextNodes, variables);
  }

  /**
   * GAP-V2-03: 动态追加节点（对标 flowlong executeAppendNodeModel）。
   *
   * @param instanceId     流程实例 ID
   * @param currentNodeCode 当前节点编码
   * @param nodeName       新节点名称
   * @param assigneeType   办理人类型
   * @param assigneeId     办理人 ID
   * @param operatorId     操作人 ID
   * @param comment        追加原因
   * @return 新创建的任务 ID
   */
  @Override
  public String appendNode(
      String instanceId,
      String currentNodeCode,
      String nodeName,
      String assigneeType,
      String assigneeId,
      String operatorId,
      String comment) {
    return lifecycleManager.appendNode(
        instanceId, currentNodeCode, nodeName, assigneeType, assigneeId, operatorId, comment);
  }

  /**
   * GAP-V2-02: 获取表单渲染数据 — 根据当前任务所在节点返回字段权限配置
   *
   * @param instanceId 流程实例 ID
   * @param taskId 当前任务 ID（可空）
   * @return Map 包含 nodeCode / nodeName / formFieldsConfig / variables
   */
  @Override
  public Map<String, Object> getFormRenderData(String instanceId, String taskId) {
    return queryService.getFormRenderData(instanceId, taskId);
  }

  /**
   * 设置实例的 dueAt 字段（子流程超时处理）
   *
   * @param instanceId 实例 ID
   * @param dueAt 超时时间（传 null 清除超时标记）
   */
  @Override
  public void setDueAt(String instanceId, LocalDateTime dueAt) {
    lifecycleManager.setDueAt(instanceId, dueAt);
  }

  /**
   * P2-2 (GAP-10): 驳回后快速重审
   *
   * @param instanceId 被驳回的实例 ID
   * @param initiatorId 发起人 ID
   * @param variables 重审时新增/覆盖的变量（可空）
   * @param comment 重审说明（可选）
   * @return 实例 ID
   */
  @Override
  public String resubmit(
      String instanceId, String initiatorId, Map<String, Object> variables, String comment) {
    return lifecycleManager.resubmit(instanceId, initiatorId, variables, comment);
  }

  /**
   * P1-8: 流程重做 — 支持 redoMode 指定重做策略。
   *
   * @param instanceId 原实例 ID
   * @param initiatorId 发起人 ID
   * @param variables 重做时新增/覆盖的变量（可空）
   * @param comment 重做说明（可选）
   * @param redoMode 重做模式：RESTART / NEW_INSTANCE
   * @return 实例 ID
   */
  @Override
  public String resubmit(
      String instanceId,
      String initiatorId,
      Map<String, Object> variables,
      String comment,
      String redoMode) {
    return lifecycleManager.resubmit(instanceId, initiatorId, variables, comment, redoMode);
  }

  // ============================== 监控聚合查询 ==============================

  @Override
  public List<Map<String, Object>> selectCountGroupByStatus(String tenantId) {
    return queryService.selectCountGroupByStatus(tenantId);
  }

  @Override
  public Map<String, Object> selectTodayCount(String tenantId) {
    return queryService.selectTodayCount(tenantId);
  }

  @Override
  public List<Map<String, Object>> selectDailyNewCount(
      String tenantId, LocalDateTime start, LocalDateTime end) {
    return queryService.selectDailyNewCount(tenantId, start, end);
  }

  @Override
  public List<Map<String, Object>> selectDailyCompletedCount(
      String tenantId, LocalDateTime start, LocalDateTime end) {
    return queryService.selectDailyCompletedCount(tenantId, start, end);
  }

  @Override
  public List<Map<String, Object>> selectFlowTypeDistribution(
      String tenantId, LocalDateTime start, LocalDateTime end) {
    return queryService.selectFlowTypeDistribution(tenantId, start, end);
  }
}
