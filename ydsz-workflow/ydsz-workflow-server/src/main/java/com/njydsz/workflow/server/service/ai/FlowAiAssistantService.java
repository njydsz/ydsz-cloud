package com.njydsz.workflow.server.service.ai;

import java.util.List;
import java.util.Map;

/**
 * AI 辅助服务门面 — 统一入口聚合各 AI 子能力。
 *
 * <p>对标 warm-flow / flowlong 的 AI 辅助特性，提供流程定义生成、实例分析、
 * 通知优化、委派推荐、国际化等智能能力。
 *
 * <p>所有 AI 调用均为异步降级设计：AI 服务不可用时返回降级结果，不影响核心审批链路。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface FlowAiAssistantService {

  /**
   * AI 生成流程定义草稿。
   *
   * <p>根据自然语言描述生成 BPMN 2.0 XML 草稿，用户可在设计器中进一步编辑。
   *
   * @param description 自然语言描述（如"三级审批：部门经理→总监→VP"）
   * @param category 流程分类（HR/FINANCE/ADMIN/PROJECT）
   * @return 生成的 BPMN XML 草稿 + 元信息（flowCode 建议、节点列表）
   */
  Map<String, Object> generateDefinitionDraft(String description, String category);

  /**
   * AI 分析流程实例瓶颈。
   *
   * <p>分析指定流程定义下所有运行中实例的耗时分布，识别拥堵节点。
   *
   * @param flowCode 流程编码
   * @return 瓶颈分析结果（拥堵节点列表、平均耗时、建议优化方案）
   */
  Map<String, Object> analyzeInstanceBottlenecks(String flowCode);

  /**
   * AI 优化通知内容。
   *
   * <p>根据流程上下文生成个性化的审批通知文案，提高审批人响应率。
   *
   * @param instanceId 实例 ID
   * @param nodeId 节点 ID
   * @param templateCode 通知模板编码
   * @return 优化后的通知文案（title / content）
   */
  Map<String, Object> optimizeNotification(String instanceId, String nodeId, String templateCode);

  /**
   * AI 推荐委派目标。
   *
   * <p>根据审批人历史审批记录、当前负载、专业领域，推荐最适合的委派目标。
   *
   * @param assigneeId 当前审批人 ID
   * @param flowCode 流程编码
   * @return 推荐委派目标列表（userId / userName / score / reason）
   */
  List<Map<String, Object>> recommendDelegateTargets(String assigneeId, String flowCode);

  /**
   * AI 翻译流程定义。
   *
   * <p>将流程名称、节点名称、字段标签翻译为目标语言版本。
   *
   * @param definitionId 流程定义 ID
   * @param targetLang 目标语言代码（en_US / zh_CN / ja_JP）
   * @return 翻译后的流程定义 Map
   */
  Map<String, Object> translateDefinition(String definitionId, String targetLang);
}
