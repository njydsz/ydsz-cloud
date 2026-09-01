package com.njydsz.workflow.server.service.impl.instance;

import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.workflow.domain.dto.FlowTaskOperateDTO;
import com.njydsz.workflow.domain.vo.FlowNodeVO;

/**
 * 流程任务完成服务实现。
 *
 * <p>封装任务「同意/拒绝/驳回/转交/委派/加签/减签」等完成动作的领域逻辑：
 *
 * <p>状态机校验、下一节点路由、SLA 重置、催办任务调度、审批意见留痕。
 *
 * @author ydsz-team
 * @since 26.09.01
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
   *
   * @param instanceId 流程实例 ID
   * @param node 目标节点
   * @param variables 流程变量
   * @return 创建的任务 ID
   */
  @Transactional(rollbackFor = Exception.class)
  public String createTask(String instanceId, FlowNodeVO node, Map<String, Object> variables) {
    return createService.createTask(instanceId, node, variables);
  }

  /**
   * 创建任务（支持显式指定办理人）
   *
   * @param instanceId 流程实例 ID
   * @param node 目标节点
   * @param variables 流程变量
   * @param explicitAssignees 显式指定的办理人 ID 列表
   * @return 创建的任务 ID
   */
  @Transactional(rollbackFor = Exception.class)
  public String createTask(
      String instanceId,
      FlowNodeVO node,
      Map<String, Object> variables,
      List<String> explicitAssignees) {
    return createService.createTask(instanceId, node, variables, explicitAssignees);
  }

  // ============================== 签收 ==============================

  /**
   * 签收
   *
   * @param taskId 任务 ID
   * @param userId 签收用户 ID
   */
  @Transactional(rollbackFor = Exception.class)
  public void claim(String taskId, String userId) {
    claimService.claim(taskId, userId);
  }

  // ============================== 通过 ==============================

  /**
   * 通过
   *
   * @param dto 任务操作 DTO
   */
  @Transactional(rollbackFor = Exception.class)
  public void pass(FlowTaskOperateDTO dto) {
    passService.pass(dto);
  }

  // ============================== 驳回 ==============================

  /**
   * 驳回
   *
   * @param dto 任务操作 DTO
   */
  @Transactional(rollbackFor = Exception.class)
  public void reject(FlowTaskOperateDTO dto) {
    rejectService.reject(dto);
  }

  // ============================== 转办 / 委派 / 跳转 / 撤回 ==============================

  /**
   * 转办
   *
   * @param dto 任务操作 DTO
   */
  @Transactional(rollbackFor = Exception.class)
  public void transfer(FlowTaskOperateDTO dto) {
    operateService.transfer(dto);
  }

  /**
   * 委派
   *
   * @param dto 任务操作 DTO
   */
  @Transactional(rollbackFor = Exception.class)
  public void delegate(FlowTaskOperateDTO dto) {
    operateService.delegate(dto);
  }

  /**
   * 自由跳转
   *
   * @param dto 任务操作 DTO
   */
  @Transactional(rollbackFor = Exception.class)
  public void jump(FlowTaskOperateDTO dto) {
    operateService.jump(dto);
  }

  /**
   * 取回（已审批后取回）
   *
   * @param hisTaskId 历史任务 ID
   * @param operatorId 取回操作人 ID
   * @param comment 取回原因
   * @return 新创建的任务 ID
   */
  @Transactional(rollbackFor = Exception.class)
  public String retract(String hisTaskId, String operatorId, String comment) {
    return operateService.retract(hisTaskId, operatorId, comment);
  }

  // ============================== 催办 ==============================

  /**
   * 实例级催办
   *
   * @param instanceId 流程实例 ID
   * @param operatorId 催办操作人 ID
   * @param comment 催办意见
   * @return 被催办的任务 ID 列表
   */
  public List<String> urge(String instanceId, String operatorId, String comment) {
    return urgeService.urge(instanceId, operatorId, comment);
  }

  /**
   * 节点级催办
   *
   * @param instanceId 流程实例 ID
   * @param nodeCode 目标节点编码
   * @param operatorId 催办操作人 ID
   * @param comment 催办意见
   * @return 被催办的任务 ID 列表
   */
  public List<String> urgeByNode(
      String instanceId, String nodeCode, String operatorId, String comment) {
    return urgeService.urgeByNode(instanceId, nodeCode, operatorId, comment);
  }

  // ============================== 超时 / 挂起 / 激活 / 取消 ==============================

  /**
   * 标记任务超时
   *
   * @param taskId 任务 ID
   * @param reason 超时原因描述
   */
  @Transactional(rollbackFor = Exception.class)
  public void timeoutTask(String taskId, String reason) {
    timeoutService.timeoutTask(taskId, reason);
  }

  /**
   * 任务级挂起
   *
   * @param taskId 任务 ID
   * @param operatorId 操作人 ID
   * @param reason 挂起原因
   */
  @Transactional(rollbackFor = Exception.class)
  public void suspendTask(String taskId, String operatorId, String reason) {
    timeoutService.suspendTask(taskId, operatorId, reason);
  }

  /**
   * 任务级激活
   *
   * @param taskId 任务 ID
   * @param operatorId 激活操作人 ID
   */
  @Transactional(rollbackFor = Exception.class)
  public void activateTask(String taskId, String operatorId) {
    timeoutService.activateTask(taskId, operatorId);
  }

  /**
   * 取消某实例全部 PENDING 任务
   *
   * @param instanceId 流程实例 ID
   * @param taskStatus 目标取消状态
   */
  public void cancelByInstance(String instanceId, String taskStatus) {
    timeoutService.cancelByInstance(instanceId, taskStatus);
  }
}
