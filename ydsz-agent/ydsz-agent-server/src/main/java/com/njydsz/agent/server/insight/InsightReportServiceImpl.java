package com.njydsz.agent.server.insight;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.njydsz.common.json.YdszJson;

import com.njydsz.agent.domain.insight.InsightReport;
import com.njydsz.agent.domain.insight.InsightReportRepository;
import com.njydsz.agent.domain.insight.InsightReportRequest;
import com.njydsz.agent.domain.insight.InsightReportResult;
import com.njydsz.agent.domain.insight.InsightReportService;
import com.njydsz.agent.domain.insight.InsightReportStatus;
import com.njydsz.agent.domain.insight.InsightSection;
import com.njydsz.agent.domain.insight.InsightReportGenerator;
import com.njydsz.agent.domain.insight.ReportRenderer;

/**
 * 洞察报告服务实现。
 *
 * <p>完成 BI 洞察报告从创建到持久化的完整流程编排：
 * <ol>
 *   <li>生成 reportId，创建草稿记录</li>
 *   <li>调用 {@link LlmInsightReportGenerator} 生成章节</li>
 *   <li>调用 {@link HtmlReportRenderer} 渲染 HTML</li>
 *   <li>更新状态为 COMPLETED 并持久化</li>
 * </ol>
 *
 * <p><b>容错策略</b>：每个步骤包入 try-catch，LLM 失败时 Generator 会返回降级章节，
 * Renderer 失败时仅保留章节数据（content 为空），确保最终状态始终可达 COMPLETED（有降级内容）。
 *
 * @author ydsz-team
 * @since 26.09.07
 */
@Slf4j
@Service
@ConditionalOnProperty(
    prefix = "ydsz.agent.insight",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false)
public class InsightReportServiceImpl implements InsightReportService {

  /** 持久化仓储 */
  private final InsightReportRepository reportRepository;

  /** LLM 报告内容生成器 */
  private final InsightReportGenerator generator;

  /** HTML 报告渲染器 */
  private final ReportRenderer renderer;

  /** 渲染初始容量 */
  private static final int COLLECTION_CAPACITY = 8;

  /**
   * 构造服务实现。
   *
   * @param reportRepository 报告仓储
   * @param generator LLM 生成器
   * @param renderer HTML 渲染器
   */
  public InsightReportServiceImpl(
      InsightReportRepository reportRepository,
      InsightReportGenerator generator,
      ReportRenderer renderer) {
    this.reportRepository = reportRepository;
    this.generator = generator;
    this.renderer = renderer;
  }

  /**
   * {@inheritDoc}
   *
   * <p>执行完整流程，任何阶段的异常都会被捕获并设置 errorMessage，状态标记为 FAILED。
   */
  @Override
  public InsightReportResult generateReport(InsightReportRequest request) {
    long startTime = System.currentTimeMillis();
    String reportId = generateReportId();

    // 1. 创建草稿记录
    InsightReport report = InsightReport.builder()
        .reportId(reportId)
        .userId(request.userId())
        .conversationId(request.conversationId())
        .title(request.reportTitle())
        .query(request.query())
        .dataSourceType(request.dataSourceType())
        .dataJson(request.dataJson())
        .status(InsightReportStatus.DRAFT.getCode())
        .reportFormat(request.reportFormat())
        .createdAt(LocalDateTime.now())
        .updatedAt(LocalDateTime.now())
        .build();
    reportRepository.save(report);

    try {
      // 2. LLM 生成章节
      List<InsightSection> sections = generator.generateSections(request);
      log.info("[Insight] 章节生成完成: reportId={}, sectionCount={}", reportId, sections.size());

      // 3. 渲染 HTML 内容
      String renderedContent;
      try {
        renderedContent = renderer.render(report, sections);
      } catch (Exception renderEx) {
        log.warn("[Insight] HTML 渲染异常，降级为纯文本: {}", renderEx.getMessage());
        renderedContent = buildPlainTextFallback(sections);
      }

      // 4. 更新为完成状态
      report.setContentJson(YdszJson.toJson(sections));
      report.setStatus(InsightReportStatus.COMPLETED.getCode());
      report.setDurationMs((int) (System.currentTimeMillis() - startTime));
      report.setUpdatedAt(LocalDateTime.now());
      reportRepository.save(report);

      return buildResult(report, sections, renderedContent, startTime);
    } catch (Exception e) {
      log.error("[Insight] 报告生成失败: reportId={}", reportId, e);
      report.setStatus(InsightReportStatus.FAILED.getCode());
      report.setErrorMessage(truncate(e.getMessage(), 500));
      report.setDurationMs((int) (System.currentTimeMillis() - startTime));
      report.setUpdatedAt(LocalDateTime.now());
      reportRepository.save(report);

      throw new InsightReportException(
          "报告生成失败: " + e.getMessage(), "INSIGHT_GENERATION_FAILED", e);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public InsightReportResult getReport(String reportId) {
    return reportRepository.findById(reportId)
        .map(entity -> deserializeResult(entity))
        .orElse(null);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<InsightReportResult> listRecentReports(String userId, int limit) {
    return reportRepository.findByUserId(userId, limit).stream()
        .map(this::deserializeResult)
        .toList();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void deleteReport(String reportId) {
    reportRepository.deleteById(reportId);
    log.info("[Insight] 报告已删除: {}", reportId);
  }

  // ========================= 私有方法 =========================

  /**
   * 生成唯一报告业务 ID。
   */
  private String generateReportId() {
    return "rpt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
  }

  /**
   * 反序列化持久化实体为结果值对象。
   */
  private InsightReportResult deserializeResult(InsightReport entity) {
    List<InsightSection> sections = parseSectionsFromJson(entity.getContentJson());
    InsightReportStatus status = InsightReportStatus.fromCode(entity.getStatus());
    return new InsightReportResult(
        entity.getReportId(),
        entity.getTitle(),
        "",  // 完整 HTML 内容不在列表中返回
        sections,
        status,
        entity.getReportPath(),
        entity.getCreatedAt(),
        entity.getDurationMs() != null ? entity.getDurationMs().longValue() : 0);
  }

  /**
   * 从 contentJson 反序列化章节列表（简化处理：返回空列表）。
   *
   * <p>为避免暴露 JSON 解析逻辑给 server 层，实际生产可调用 Converter。
   * 当前返回空章节列表（列表 API 不需要完整内容）。
   */
  private List<InsightSection> parseSectionsFromJson(String contentJson) {
    // 列表查询时不解析详细内容，返回空
    return new ArrayList<>(0);
  }

  /**
   * 构建最终结果值对象。
   */
  private InsightReportResult buildResult(
      InsightReport entity, List<InsightSection> sections, String content, long startTime) {
    return new InsightReportResult(
        entity.getReportId(),
        entity.getTitle(),
        content,
        sections,
        InsightReportStatus.COMPLETED,
        entity.getReportPath(),
        entity.getCreatedAt(),
        System.currentTimeMillis() - startTime);
  }

  /**
   * 构建降级纯文本内容（HTML 渲染失败时）。
   */
  private String buildPlainTextFallback(List<InsightSection> sections) {
    StringBuilder sb = new StringBuilder(512);
    if (sections != null) {
      for (InsightSection section : sections) {
        sb.append("=== ").append(section.title()).append(" ===\n");
        sb.append(section.content()).append("\n\n");
      }
    }
    return sb.toString();
  }

  /**
   * 截断超长字符串。
   */
  private String truncate(String value, int maxLength) {
    if (value == null) {
      return null;
    }
    return value.length() > maxLength ? value.substring(0, maxLength) : value;
  }
}
