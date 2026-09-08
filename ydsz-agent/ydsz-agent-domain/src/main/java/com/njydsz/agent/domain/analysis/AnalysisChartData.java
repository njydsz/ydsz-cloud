package com.njydsz.agent.domain.analysis;

import java.util.List;
import java.util.Map;

/**
 * 图表数据值对象（不可变 record）。
 *
 * <p>描述一个可视化图表所需的全部数据：图表类型、标签轴和数据集列表。
 * JSON 序列化后嵌入报告的 {@link com.njydsz.agent.domain.insight.InsightSection#getDataJson()} 中，
 * 供前端 ECharts 渲染。
 *
 * <p>示例结构：
 * <pre>{@code
 * {
 *   "chartType": "bar",
 *   "labels": ["1月", "2月", "3月"],
 *   "datasets": [
 *     {"label": "销售额", "data": [120, 200, 150]}
 *   ]
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.07
 */
public record AnalysisChartData(
    String chartType,
    List<String> labels,
    List<Map<String, Object>> datasets) {

  /** 集合初始容量 */
  private static final int COLLECTION_CAPACITY = 16;

  /**
   * 全参构造。
   *
   * @param chartType 图表类型（bar / line / pie / scatter / radar / table）
   * @param labels X 轴标签列表
   * @param datasets 数据集列表（每项含 label + data 字段）
   */
  public AnalysisChartData {
    if (chartType == null || chartType.isBlank()) {
      chartType = "table";
    }
    if (labels == null) {
      labels = List.of();
    } else {
      labels = List.copyOf(labels);
    }
    if (datasets == null) {
      datasets = List.of();
    } else {
      datasets = List.copyOf(datasets);
    }
  }

  /**
   * 创建表格类型图表数据。
   *
   * @param labels 列标题
   * @param datasets 数据行
   * @return 表格图表数据
   */
  public static AnalysisChartData table(java.util.List<String> labels, java.util.List<Map<String, Object>> datasets) {
    return new AnalysisChartData("table", labels, datasets);
  }

  /**
   * 创建柱状图数据。
   *
   * @param labels X 轴标签
   * @param datasets 数据集
   * @return 柱状图数据
   */
  public static AnalysisChartData bar(java.util.List<String> labels, java.util.List<Map<String, Object>> datasets) {
    return new AnalysisChartData("bar", labels, datasets);
  }
}
