package com.njydsz.agent.domain.insight;

import java.util.Set;

/**
 * 洞察报告章节值对象（不可变 record）。
 *
 * <p>描述 BI 洞察报告中的一个结构化章节，每个章节有独立的类型、标题、内容和可选的结构化数据
 * （如图表数据 JSON）。章节按 {@code sort} 排序后组成完整报告。
 *
 * <p>支持类型：
 * <ul>
 *   <li>summary — 数据摘要（总览概览）</li>
 *   <li>data — 数据明细（表格展示）</li>
 *   <li>chart — 图表可视化（含 chartType + datasets）</li>
 *   <li>insight — 分析洞察（文字结论）</li>
 *   <li>trend — 趋势分析（时序变化）</li>
 *   <li>prediction — 预测推断</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.07
 */
public record InsightSection(
    String sectionType,
    String title,
    String content,
    String dataJson,
    int sort) {

  /** 允许的章节类型集合 */
  private static final Set<String> VALID_TYPES = Set.of(
      "summary", "data", "chart", "insight", "trend", "prediction");

  /**
   * 全参构造。
   *
   * @param sectionType 章节类型
   * @param title 章节标题
   * @param content 章节内容（Markdown 或纯文本）
   * @param dataJson 结构化数据 JSON（可选）
   * @param sort 排序序号
   */
  public InsightSection {
    if (sectionType == null || sectionType.isBlank()) {
      sectionType = "insight";
    }
    if (!VALID_TYPES.contains(sectionType)) {
      sectionType = "insight";
    }
    if (title == null) {
      title = "";
    }
    if (content == null) {
      content = "";
    }
    if (dataJson == null) {
      dataJson = "{}";
    }
    if (sort < 0) {
      sort = 0;
    }
  }
}
