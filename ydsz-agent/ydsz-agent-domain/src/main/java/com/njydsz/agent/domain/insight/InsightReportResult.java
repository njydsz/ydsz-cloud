package com.njydsz.agent.domain.insight;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 洞察报告生成结果值对象（不可变 record）。
 *
 * <p>封装 BI 洞察报告生成的完整输出，包括报告 ID、标题、完整内容、结构化章节列表、
 * 状态、存储路径和耗时。作为 {@link InsightReportService#generateReport} 的返回值。
 *
 * @param reportId 报告唯一 ID
 * @param title 报告标题
 * @param content 生成的报告全文
 * @param sections 报告章节列表
 * @param status 报告状态
 * @param reportPath 存储路径/URL
 * @param createdAt 创建时间
 * @param durationMs 生成耗时（毫秒）
 * @author ydsz-team
 * @since 26.09.07
 */
public record InsightReportResult(
    String reportId,
    String title,
    String content,
    List<InsightSection> sections,
    InsightReportStatus status,
    String reportPath,
    LocalDateTime createdAt,
    long durationMs) {

  /** 集合初始容量 */
  private static final int COLLECTION_CAPACITY = 16;

  /**
   * 全参构造。
   *
   * @param reportId 报告唯一 ID
   * @param title 报告标题
   * @param content 生成的报告全文
   * @param sections 报告章节列表
   * @param status 报告状态
   * @param reportPath 存储路径/URL
   * @param createdAt 创建时间
   * @param durationMs 生成耗时（毫秒）
   */
  public InsightReportResult {
    if (reportId == null || reportId.isBlank()) {
      throw new IllegalArgumentException("reportId 不能为空");
    }
    if (title == null) {
      title = "";
    }
    if (content == null) {
      content = "";
    }
    if (sections == null) {
      sections = List.of();
    } else {
      sections = List.copyOf(sections);
    }
    if (status == null) {
      status = InsightReportStatus.DRAFT;
    }
    if (createdAt == null) {
      createdAt = LocalDateTime.now();
    }
  }

  /**
   * 创建进行中（草稿）状态的中间结果。
   *
   * @param reportId 报告唯一 ID
   * @param title 报告标题
   * @return 草稿状态的结果
   */
  public static InsightReportResult draft(String reportId, String title) {
    return new InsightReportResult(
        reportId, title, "", List.of(), InsightReportStatus.DRAFT, null, LocalDateTime.now(), 0);
  }

  /**
   * 创建失败状态的结果。
   *
   * @param reportId 报告唯一 ID
   * @param title 报告标题
   * @param durationMs 已消耗耗时（毫秒）
   * @return 失败状态的结果
   */
  public static InsightReportResult failed(String reportId, String title, long durationMs) {
    return new InsightReportResult(
        reportId, title, "", List.of(), InsightReportStatus.FAILED, null, LocalDateTime.now(), durationMs);
  }
}
