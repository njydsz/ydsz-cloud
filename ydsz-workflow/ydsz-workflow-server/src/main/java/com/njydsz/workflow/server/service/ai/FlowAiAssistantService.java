package com.njydsz.workflow.server.service.ai;

import java.util.List;

import com.njydsz.workflow.domain.vo.FlowAiBottleneckAnalysisVO;
import com.njydsz.workflow.domain.vo.FlowAiDefinitionDraftVO;
import com.njydsz.workflow.domain.vo.FlowAiDelegateRecommendationVO;
import com.njydsz.workflow.domain.vo.FlowAiNotificationVO;
import com.njydsz.workflow.domain.vo.FlowAiTranslationResultVO;

/**
 * AI 辅助服务门面 — 统一入口聚合各 AI 子能力。
 *
 * <p>对标 warm-flow / flowlong 的 AI 辅助特性，提供流程定义生成、实例分析、
 * 通知优化、委派推荐、国际化等智能能力。
 *
 * <p>当前实现为规则引擎驱动（不依赖外部 LLM），基于关键词匹配、统计分析、
 * 上下文感知提供实质性智能能力。后续可无缝升级为 LLM 驱动。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface FlowAiAssistantService {

  /**
   * AI 生成流程定义草稿。
   *
   * <p>根据自然语言描述智能匹配模板，生成 BPMN 2.0 XML 草稿。
   * 当前基于关键词模板匹配，后续可升级为 LLM 生成。
   *
   * @param description 自然语言描述（如"三级审批：部门经理→总监→VP"）
   * @param category 流程分类（HR/FINANCE/ADMIN/PROJECT）
   * @return 生成的流程定义草稿 VO
   */
  FlowAiDefinitionDraftVO generateDefinitionDraft(String description, String category);

  /**
   * AI 分析流程实例瓶颈。
   *
   * <p>基于历史数据分析指定流程定义下各节点的耗时分布，识别拥堵节点。
   *
   * @param flowCode 流程编码
   * @return 瓶颈分析结果 VO
   */
  FlowAiBottleneckAnalysisVO analyzeInstanceBottlenecks(String flowCode);

  /**
   * AI 优化通知内容。
   *
   * <p>根据流程上下文生成个性化的审批通知文案，提高审批人响应率。
   *
   * @param instanceId 实例 ID
   * @param nodeId 节点 ID
   * @param templateCode 通知模板编码
   * @return 优化后的通知内容 VO
   */
  FlowAiNotificationVO optimizeNotification(String instanceId, String nodeId, String templateCode);

  /**
   * AI 推荐委派目标。
   *
   * <p>根据审批人历史审批记录、当前负载、专业领域，推荐最适合的委派目标。
   *
   * @param assigneeId 当前审批人 ID
   * @param flowCode 流程编码
   * @return 推荐委派目标列表
   */
  List<FlowAiDelegateRecommendationVO> recommendDelegateTargets(String assigneeId, String flowCode);

  /**
   * AI 翻译流程定义。
   *
   * <p>将流程名称、节点名称、字段标签翻译为目标语言版本。
   *
   * @param definitionId 流程定义 ID
   * @param targetLang 目标语言代码（en_US / zh_CN / ja_JP）
   * @return 翻译结果 VO
   */
  FlowAiTranslationResultVO translateDefinition(String definitionId, String targetLang);
}
