package com.njydsz.workflow.server.service;

import java.util.List;

import com.njydsz.workflow.infra.entity.FlowAutoTriggerDO;

/**
 * 流程自动触发服务。
 *
 * <p>按条件（数据变更/定时/事件）自动发起流程。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface FlowAutoTriggerService {

  /**
   * 实例完成时触发 — 检查是否需要自动发起下一流程
   *
   * <p>查询 sourceFlowCode 对应的所有 enabled 触发规则，使用 literule 的 ExpressionEvaluator 评估
   * conditionExpression（如果为空则无条件触发）， 读取已完成的实例 variables 作为上下文，调用 WorkflowFacade.startProcess
   * 启动目标流程，并写入审计日志。
   *
   * @param instanceId 已完成的流程实例 ID
   */
  void onInstanceCompleted(String instanceId);

  /**
   * 注册触发规则
   *
   * @param sourceFlowCode 源流程编码（触发方）
   * @param targetFlowCode 目标流程编码（被触发方）
   * @param conditionExpression 条件表达式（Aviator 语法，为空则无条件触发）
   */
  void registerTrigger(String sourceFlowCode, String targetFlowCode, String conditionExpression);

  /**
   * 移除触发规则
   *
   * <p>删除指定源流程编码的所有触发规则（逻辑删除）。
   *
   * @param sourceFlowCode 源流程编码
   */
  void removeTrigger(String sourceFlowCode);

  /**
   * 查询所有触发规则
   *
   * @return 触发规则列表
   */
  List<FlowAutoTriggerDO> listAll();

  /**
   * 按 ID 删除触发规则（逻辑删除）
   *
   * @param id 规则 ID
   */
  void deleteById(String id);

  /**
   * 切换触发规则的启用/禁用状态
   *
   * @param id 规则 ID
   * @return 切换后的状态：true=启用 / false=禁用
   */
  boolean toggleEnabled(String id);
}
