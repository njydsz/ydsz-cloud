package com.njydsz.agent.domain.insight;

import java.util.List;

/**
 * 洞察报告服务领域网关接口。
 *
 * <p>定义 BI 洞察报告生成、查询、列表和删除等核心操作的领域契约。
 * 实现类位于 server 层，负责编排 LLM 生成、渲染输出和持久化。
 *
 * @author ydsz-team
 * @since 26.09.07
 */
public interface InsightReportService {

  /**
   * 生成洞察报告。
   *
   * <p>执行完整的报告生成流程：创建记录 -> LLM 生成章节 -> 渲染 HTML/Markdown -> 持久化。
   *
   * @param request 报告生成请求（含用户、数据、格式等）
   * @return 生成的报告结果（含章节列表和状态）
   * @throws IllegalArgumentException 当请求参数无效时抛出
   * @throws InsightReportException 当报告生成失败时抛出
   */
  InsightReportResult generateReport(InsightReportRequest request);

  /**
   * 获取已生成的报告。
   *
   * @param reportId 报告唯一 ID
   * @return 报告结果，不存在时返回 null
   */
  InsightReportResult getReport(String reportId);

  /**
   * 列出用户近期报告。
   *
   * @param userId 用户 ID
   * @param limit 返回条数上限
   * @return 报告列表（按创建时间倒序）
   */
  List<InsightReportResult> listRecentReports(String userId, int limit);

  /**
   * 删除报告。
   *
   * @param reportId 报告唯一 ID
   */
  void deleteReport(String reportId);

  /**
   * 洞察报告异常。
   */
  class InsightReportException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 错误码 */
    private final String errorCode;

    /**
     * 构造异常。
     *
     * @param message 错误描述
     * @param errorCode 错误码
     */
    public InsightReportException(String message, String errorCode) {
      super(message);
      this.errorCode = errorCode;
    }

    /**
     * 构造异常（携带根因）。
     *
     * @param message 错误描述
     * @param errorCode 错误码
     * @param cause 根因异常
     */
    public InsightReportException(String message, String errorCode, Throwable cause) {
      super(message, cause);
      this.errorCode = errorCode;
    }

    /**
     * 获取错误码。
     *
     * @return 错误码
     */
    public String getErrorCode() {
      return errorCode;
    }
  }
}
