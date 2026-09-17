package com.njydsz.agent.domain.knowledge;

import java.util.List;

/**
 * 实体抽取服务接口。
 *
 * <p>从文本中抽取实体-关系三元组（Entity→Relation→Entity）。
 * 支持 LLM 抽取和规则抽取两种模式，实现类通过 {@code @Component} 注册。
 *
 * <p><b>使用场景：</b>文档摄入时调用此接口从原文中自动识别关键实体及其关系，
 * 构建知识图谱以补充向量检索的精确匹配能力。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
public interface EntityExtractionService {

  /**
   * 抽取实体和关系。
   *
   * <p>分析输入文本，识别其中的命名实体和它们之间的语义关系，
   * 返回结构化的实体列表和去重后的关系列表。
   *
   * @param text 输入文本（非空）
   * @param sourceDocId 来源文档 ID
   * @return 抽取结果（实体列表 + 关系列表）
   */
  ExtractionResult extract(String text, String sourceDocId);

  /**
   * 抽取结果值对象。
   *
   * @param entities 抽取的实体列表
   * @param relations 抽取的关系列表
   */
  record ExtractionResult(List<Entity> entities, List<Relation> relations) {}
}
