package com.njydsz.agent.domain.insight;

import java.util.Map;

/**
 * 洞察报告生成请求值对象（不可变 record）。
 *
 * <p>封装用户触发的 BI 洞察报告生成所需的全部输入参数，包括原始分析查询、数据分析结果、
 * 期望输出格式等。作为 {@link InsightReportService#generateReport} 的入参。
 *
 * @param userId 触发用户
 * @param conversationId 关联对话 ID（可选）
 * @param reportTitle 报告标题
 * @param query 原始分析查询
 * @param dataSourceType 数据源类型（sql / python / mixed）
 * @param dataJson 原始数据分析结果的 JSON 字符串
 * @param reportFormat 报告格式（html / pdf / markdown）
 * @param extraParams 额外参数
 * @author ydsz-team
 * @since 26.09.07
 */
public record InsightReportRequest(
    String userId,
    String conversationId,
    String reportTitle,
    String query,
    String dataSourceType,
    String dataJson,
    String reportFormat,
    Map<String, String> extraParams) {

  /**
   * 全参构造。
   *
   * @param userId 触发用户
   * @param conversationId 关联对话 ID（可选）
   * @param reportTitle 报告标题
   * @param query 原始分析查询
   * @param dataSourceType 数据源类型（sql / python / mixed）
   * @param dataJson 原始数据分析结果的 JSON 字符串
   * @param reportFormat 报告格式（html / pdf / markdown）
   * @param extraParams 额外参数
   */
  public InsightReportRequest {
    if (userId == null || userId.isBlank()) {
      throw new IllegalArgumentException("userId 不能为空");
    }
    if (reportTitle == null || reportTitle.isBlank()) {
      throw new IllegalArgumentException("reportTitle 不能为空");
    }
    if (dataJson == null) {
      dataJson = "{}";
    }
    if (reportFormat == null || reportFormat.isBlank()) {
      reportFormat = "html";
    }
    if (extraParams == null) {
      extraParams = Map.of();
    } else {
      extraParams = Map.copyOf(extraParams);
    }
  }

  /**
   * 构造：使用必要字段初始化，其他参数使用默认值。
   *
   * @param userId 触发用户
   * @param reportTitle 报告标题
   * @param dataJson 原始数据分析结果的 JSON 字符串
   * @return 简化构造的实例
   */
  public static InsightReportRequest of(String userId, String reportTitle, String dataJson) {
    return new InsightReportRequest(userId, null, reportTitle, null, "mixed", dataJson, "html", Map.of());
  }

  /**
   * 获取额外参数值（安全访问，不存在时返回 null）。
   *
   * @param key 参数键
   * @return 参数值或 null
   */
  public String getExtra(String key) {
    return extraParams != null ? extraParams.get(key) : null;
  }
}
