package com.njydsz.agent.domain.analysis;

import java.util.List;
import java.util.Map;

/**
 * 数据分析结果值对象（不可变 record）。
 *
 * <p>封装 {@link DataSourceAnalysisService#analyze} 的完整输出，包含自然语言摘要、
 * 原始查询数据表格和可选的图表数据列表。
 *
 * @param summary 自然语言摘要
 * @param columns 数据列名列表
 * @param rawData 原始数据行列表
 * @param charts 图表数据列表
 * @param durationMs 分析耗时（毫秒）
 * @param query 原始查询文本
 * @author ydsz-team
 * @since 26.09.07
 */
public record DataSourceAnalysisResult(
    String summary,
    List<String> columns,
    List<Map<String, Object>> rawData,
    List<AnalysisChartData> charts,
    long durationMs,
    String query) {

  /** 集合初始容量 */
  private static final int COLLECTION_CAPACITY = 16;

  /** JSON 拼接缓冲区初始容量 */
  private static final int JSON_BUFFER_CAPACITY = 512;

  /**
   * 全参构造。
   *
   * @param summary 自然语言摘要
   * @param columns 数据列名列表
   * @param rawData 原始数据行列表
   * @param charts 图表数据列表
   * @param durationMs 分析耗时（毫秒）
   * @param query 原始查询文本
   */
  public DataSourceAnalysisResult {
    if (summary == null) {
      summary = "";
    }
    if (columns == null) {
      columns = List.of();
    } else {
      columns = List.copyOf(columns);
    }
    if (rawData == null) {
      rawData = List.of();
    } else {
      rawData = List.copyOf(rawData);
    }
    if (charts == null) {
      charts = List.of();
    } else {
      charts = List.copyOf(charts);
    }
  }

  /**
   * 转换为 JSON 字符串，供报告生成器消费。
   *
   * @return JSON 格式字符串
   */
  public String toDataJson() {
    StringBuilder sb = new StringBuilder(JSON_BUFFER_CAPACITY);
    sb.append("{\"summary\":\"").append(escapeJson(summary)).append("\"");
    sb.append(",\"columns\":");
    appendStringList(sb, columns);
    sb.append(",\"rowCount\":").append(rawData.size());
    sb.append(",\"durationMs\":").append(durationMs);
    sb.append("}");
    return sb.toString();
  }

  /**
   * 安全转义 JSON 字符串中的特殊字符。
   *
   * @param text 原始文本
   * @return 转义后的文本
   */
  private static String escapeJson(String text) {
    if (text == null) {
      return "";
    }
    return text.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }

  /**
   * 将字符串列表拼接为 JSON 数组。
   *
   * @param sb 目标 StringBuilder
   * @param list 字符串列表
   */
  private static void appendStringList(StringBuilder sb, List<String> list) {
    sb.append("[");
    for (int i = 0; i < list.size(); i++) {
      if (i > 0) {
        sb.append(",");
      }
      sb.append("\"").append(escapeJson(list.get(i))).append("\"");
    }
    sb.append("]");
  }
}
