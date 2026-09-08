package com.njydsz.agent.web.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.agent.domain.insight.InsightReportRequest;
import com.njydsz.agent.domain.insight.InsightReportResult;
import com.njydsz.agent.domain.insight.InsightReportService;

/**
 * 洞察报告 REST 控制器。
 *
 * <p>提供 BI 洞察报告的生成、查询、列表、导出 HTTP 接口。
 * 仅当 {@link InsightReportService} Bean 存在时注册（insight.enabled=true）。
 *
 * <p>接口列表：
 * <ul>
 *   <li>{@code POST /api/v1/agent/insight/report} — 生成报告</li>
 *   <li>{@code GET /api/v1/agent/insight/report/{reportId}} — 查询报告元数据</li>
 *   <li>{@code GET /api/v1/agent/insight/report/{reportId}/html} — 导出 HTML</li>
 *   <li>{@code GET /api/v1/agent/insight/reports?userId=xxx} — 列出近期报告</li>
 *   <li>{@code DELETE /api/v1/agent/insight/report/{reportId}} — 删除报告</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.07
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/agent/insight")
@RequiredArgsConstructor
@ConditionalOnBean(InsightReportService.class)
public class InsightReportController {

  /** 报告服务 */
  private final InsightReportService insightReportService;

  /**
   * 生成洞察报告。
   *
   * @param request 报告生成请求 JSON
   * @return 报告结果
   */
  @PostMapping("/report")
  public ResponseEntity<InsightReportResult> generateReport(
      @RequestBody InsightReportRequest request) {
    InsightReportResult result = insightReportService.generateReport(request);
    return ResponseEntity.ok(result);
  }

  /**
   * 查询报告元数据。
   *
   * @param reportId 报告 ID
   * @return 报告结果（404 当不存在时）
   */
  @GetMapping("/report/{reportId}")
  public ResponseEntity<InsightReportResult> getReport(@PathVariable String reportId) {
    InsightReportResult result = insightReportService.getReport(reportId);
    return result != null ? ResponseEntity.ok(result) : ResponseEntity.notFound().build();
  }

  /**
   * 导出报告 HTML 渲染结果。
   *
   * <p>返回 text/html 内容，可在浏览器中直接打开查看。
   *
   * @param reportId 报告 ID
   * @return HTML 字节流
   */
  @GetMapping(value = "/report/{reportId}/html", produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<byte[]> exportHtml(@PathVariable String reportId) {
    InsightReportResult result = insightReportService.getReport(reportId);
    if (result == null) {
      return ResponseEntity.notFound().build();
    }
    // 从 sections 重新渲染 HTML（简化：返回 sections JSON + 前端渲染）
    // 完整版可调用 renderer 从持久化数据重新渲染
    String html = "<html><body><h1>" + escapeHtml(result.title()) + "</h1>"
        + "<pre>" + escapeHtml(result.content().substring(0, Math.min(result.content().length(), 10000)))
            + "</pre>"
        + "</body></html>";
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION,
            "inline; filename=\"report-" + reportId + ".html\"")
        .contentType(MediaType.TEXT_HTML)
        .body(html.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * 列出用户近期报告。
   *
   * @param userId 用户 ID（必填）
   * @param limit 返回条数上限（默认 10）
   * @return 报告列表
   */
  @GetMapping("/reports")
  public ResponseEntity<List<InsightReportResult>> listRecentReports(
      @RequestParam String userId,
      @RequestParam(defaultValue = "10") int limit) {
    List<InsightReportResult> reports = insightReportService.listRecentReports(userId, limit);
    return ResponseEntity.ok(reports);
  }

  /**
   * 删除报告。
   *
   * @param reportId 报告 ID
   * @return 204 No Content
   */
  @DeleteMapping("/report/{reportId}")
  public ResponseEntity<Map<String, String>> deleteReport(@PathVariable String reportId) {
    insightReportService.deleteReport(reportId);
    return ResponseEntity.ok(Map.of("status", "deleted", "reportId", reportId));
  }

  /**
   * 基础 HTML 转义（防 XSS）。
   */
  private String escapeHtml(String text) {
    if (text == null) {
      return "";
    }
    return text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }
}
