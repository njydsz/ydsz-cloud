package com.njydsz.workflow.server.service;

import com.njydsz.workflow.domain.entity.FlowDmnDecision;
import com.njydsz.workflow.domain.entity.FlowDmnRule;
import java.util.List;
import java.util.Map;

/**
 * DMN 决策服务。
 *
 * <p>DMN 决策表评估。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface FlowDmnDecisionService {

  /**
   * 创建决策表（草稿状态）
   *
   * @param decision 决策表元数据
   * @param rules 规则行列表
   * @return 决策表 ID
   */
  String createDecision(FlowDmnDecision decision, List<FlowDmnRule> rules);

  /** 更新决策表（仅草稿状态可编辑） */
  void updateDecision(String decisionId, FlowDmnDecision decision, List<FlowDmnRule> rules);

  /** 发布决策表（DRAFT → PUBLISHED，版本递增） */
  void publish(String decisionId);

  /** 停用决策表（PUBLISHED → DEPRECATED） */
  void deprecate(String decisionId);

  /** 查询决策表详情（含规则列表） */
  Map<String, Object> getDetail(String decisionId);

  /** 分页查询决策表列表 */
  List<FlowDmnDecision> listDecisions(String decisionCode, String tenantId);

  /**
   * 评估决策表
   *
   * <p>根据输入变量匹配规则，返回输出结果。
   *
   * <ul>
   *   <li>UNIQUE / FIRST — 返回第一条命中规则的输出
   *   <li>COLLECT — 返回所有命中规则的输出列表
   *   <li>ANY — 多条命中时校验输出一致，不一致抛异常
   * </ul>
   *
   * @param decisionCode 决策表编码
   * @param variables 输入变量
   * @param tenantId 租户 ID
   * @return 输出结果 Map（key = outputDefinitions.name, value = 输出值）； COLLECT 策略时 value 为 List
   */
  Map<String, Object> evaluate(String decisionCode, Map<String, Object> variables, String tenantId);

  /**
   * 根据流程编码 + 节点编码评估绑定的决策表
   *
   * @param flowCode 流程编码
   * @param nodeCode 节点编码
   * @param variables 输入变量
   * @param tenantId 租户 ID
   * @return 输出结果 Map；无绑定决策表时返回 null
   */
  Map<String, Object> evaluateByNode(
      String flowCode, String nodeCode, Map<String, Object> variables, String tenantId);
}
