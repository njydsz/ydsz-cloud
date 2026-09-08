package com.njydsz.agent.domain.insight;

import java.util.List;

/**
 * 洞察报告内容生成器接口（领域网关）。
 *
 * <p>定义将数据分析结果转化为结构化报告章节列表的领域契约。
 * 实现可基于 LLM 调用、模板填充或规则引擎，由 infra 层注入装配。
 *
 * @author ydsz-team
 * @since 26.09.07
 */
public interface InsightReportGenerator {

  /**
   * 根据报告请求生成章节列表。
   *
   * <p>实现类可使用 LLM 将数据分析结果 JSON 转化为多种类型章节（摘要/洞察/图表等）。
   * 当 LLM 不可用时，实现类应返回降级章节（基础摘要 + 原始数据）以保证鲁棒性。
   *
   * @param request 报告生成请求（含数据分析结果）
   * @return 章节列表（可能为空但不为 null）
   */
  List<InsightSection> generateSections(InsightReportRequest request);
}
