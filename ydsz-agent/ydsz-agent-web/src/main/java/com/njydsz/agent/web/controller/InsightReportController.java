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

import com.njydsz.agent.domain.enums.AgentExceptionCode;
import com.njydsz.agent.domain.insight.InsightReportRequest;
import com.njydsz.agent.domain.insight.InsightReportResult;
import com.njydsz.agent.domain.insight.InsightReportService;
import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.exception.custom.BusinessException;

/**
 * 洞察报告 REST 控制器。
 *
 * <p>提供 BI 洞察报告的生成、查询、列表、导出 HTTP 接口。
 * 仅当 {@link InsightReportService} Bean 存在时注册（insight.enabled=true）。
 *
 * <p>接口列表：
 * <ul>
 *   <li>{@code POST /api/agent/insight/report} — 生成报告</li>
 *   <li>{@code GET /api/agent/insight/report/{reportId}} — 查询报告元数据</li>
 *   <li>{@code GET /api/agent/insight/report/{reportId}/html} — 导出 HTML</li>
 *   <li>{@code GET /api/agent/insight/reports?userId=xxx} — 列出近期报告</li>
 *   <li>{@code DELETE /api/agent/insight/report/{reportId}} — 删除报告</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.07
 */
@Slf4j
@ApiVersion("26.09.01")
@RestController
@RequestMapping("/agent/insight")
@RequiredArgsConstructor
@ConditionalOnBean(InsightReportService.class)
public class InsightReportController {

  /** 报告服务 */
  private final InsightReportService insightReportService;

  /**
   * 生成洞察报告。
   *
   * <p>处理流程（典型耗时 30-120 秒，取决于数据源复杂度和报告长度）：
   *
   * <ol>
   *   <li>解析请求参数：数据源类型（DB查询/API接入/文件上传）、报告模板、分析维度</li>
   *   <li>从指定数据源拉取原始数据（SQL 查询 / API 调用 / 文件解析）</li>
   *   <li>调用 LLM 对数据进行多维度分析（趋势识别、异常检测、归因分析、建议生成）</li>
   *   <li>构建 {@link InsightReportResult}（含 title / content / sections / dataSnapshot）</li>
   *   <li>持久化报告到数据库，便于后续查询和导出</li>
   * </ol>
   *
   * <p>Token 消耗：与数据量和报告长度相关，估算 {@code ≈ 数据摘要字数 / 4 * 3}（输入+多轮生成），
   * 大报表（>10000 字数据摘要）单次消耗可能超过 10K Token。
   *
   * <p>数据来源：支持 MySQL / PostgreSQL 数据库查询、HTTP API 拉取、CSV/文件解析，具体由请求参数指定。
   * 报告生成算法采用"数据摘要 → LLM 多维度分析 → 结构化输出"的 ReAct 模式。
   *
   * @param request 报告生成请求（必填：dataSource / template / analysisDimensions；可选：title / filters / outputSections）
   * @return 统一响应结果，data 为 {@link InsightReportResult}（含 reportId / title / content / sections / generatedAt 等）
   */
  @PostMapping("/report")
  public YdszResponse<InsightReportResult> generateReport(
      @RequestBody InsightReportRequest request) {
    return YdszResponse.success(insightReportService.generateReport(request));
  }

  /**
   * 查询报告元数据。
   *
   * <p>按报告 ID 查询已持久化报告的完整元数据（含标题 / 内容 / 各章节 / 数据快照 / 生成时间）。
   * 报告数据在生成后长期保留，不受 Token 配额影响。
   *
   * @param reportId 报告 ID（路径参数，由生成接口返回）
   * @return 统一响应结果，data 为 {@link InsightReportResult}
   * @throws com.njydsz.common.exception.custom.BusinessException 报告不存在时抛 {@link AgentExceptionCode#INSIGHT_REPORT_NOT_FOUND}，经统一异常处理映射为 HTTP 404
   */
  @GetMapping("/report/{reportId}")
  public YdszResponse<InsightReportResult> getReport(@PathVariable String reportId) {
    InsightReportResult result = insightReportService.getReport(reportId);
    if (result == null) {
      throw new BusinessException(AgentExceptionCode.INSIGHT_REPORT_NOT_FOUND);
    }
    return YdszResponse.success(result);
  }

  /**
   * 导出报告 HTML 渲染结果。
   *
   * <p>返回 text/html 内容，可在浏览器中直接打开查看。
   *
   * <p><b>API-RESP 豁免说明（P1-9）：</b>本端点返回 HTML 字节流供浏览器直接渲染，
   * 非平台统一 JSON 响应体适用场景，豁免 {@code YdszResponse} 包装。
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
  public YdszResponse<List<InsightReportResult>> listRecentReports(
      @RequestParam String userId,
      @RequestParam(defaultValue = "10") int limit) {
    return YdszResponse.success(insightReportService.listRecentReports(userId, limit));
  }

  /**
   * 删除报告。
   *
   * @param reportId 报告 ID
   * @return 删除结果（status=deleted）
   */
  @DeleteMapping("/report/{reportId}")
  public YdszResponse<Map<String, String>> deleteReport(@PathVariable String reportId) {
    insightReportService.deleteReport(reportId);
    return YdszResponse.success(Map.of("status", "deleted", "reportId", reportId));
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
