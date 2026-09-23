package com.njydsz.agent.server.asynctask;

import java.util.Map;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import com.njydsz.agent.domain.asynctask.AsyncTask;
import com.njydsz.agent.domain.insight.InsightReportRequest;
import com.njydsz.agent.domain.insight.InsightReportResult;
import com.njydsz.agent.domain.insight.InsightReportService;
import com.njydsz.common.json.YdszJson;

/**
 * 洞察报告生成异步任务执行器。
 *
 * <p>后台生成 BI 洞察报告，避免 HTTP 请求因 LLM 调用耗时过长而超时。
 * 仅当 InsightReportService Bean 存在时（ydsz.agent.insight.enabled=true）激活。
 *
 * <p>inputPayload 期望格式（JSON）：
 * <pre>{@code
 * {
 *   "userId": "user-001",
 *   "reportTitle": "销售数据分析报告",
 *   "query": "分析本月销售趋势",
 *   "dataSourceType": "sql",
 *   "dataJson": "{...}",
 *   "reportFormat": "html"
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.23
 */
@Slf4j
@Component
@ConditionalOnBean(InsightReportService.class)
public class ReportGenerateTaskExecutor implements AsyncTaskExecutor {

  /** 报告 ID 随机段长度 */
  private static final int REPORT_ID_RANDOM_LENGTH = 16;

  private final InsightReportService reportService;

  public ReportGenerateTaskExecutor(InsightReportService reportService) {
    this.reportService = reportService;
  }

  @Override
  public String supportedType() {
    return "REPORT_GENERATE";
  }

  @Override
  public void execute(AsyncTask task, Consumer<Integer> progressConsumer) {
    log.info("[AsyncTask:REPORT_GENERATE] 开始执行: taskId={}", task.getId());
    consumeProgress(progressConsumer, 5);

    try {
      Map<String, Object> params = parseInputPayload(task.getInputPayload());
      String userId = getStringParam(params, "userId");
      String reportTitle = getStringParam(params, "reportTitle");
      String query = getStringParam(params, "query");
      String dataSourceType = getStringParam(params, "dataSourceType", "mixed");
      String dataJson = getStringParam(params, "dataJson", "{}");
      String reportFormat = getStringParam(params, "reportFormat", "html");

      if (userId == null || userId.isBlank()) {
        task.fail("userId 不能为空");
        return;
      }
      if (reportTitle == null || reportTitle.isBlank()) {
        task.fail("reportTitle 不能为空");
        return;
      }

      InsightReportRequest request = new InsightReportRequest(
          userId, null, reportTitle, query, dataSourceType, dataJson, reportFormat, Map.of());

      consumeProgress(progressConsumer, 10);
      log.info("[AsyncTask:REPORT_GENERATE] 生成报告: userId={}, title={}", userId, reportTitle);

      InsightReportResult result = reportService.generateReport(request);

      consumeProgress(progressConsumer, 90);

      task.succeed(YdszJson.toJson(Map.of(
          "reportId", result.reportId(),
          "status", result.status().name(),
          "sectionCount", result.sections() != null ? result.sections().size() : 0)));

      log.info("[AsyncTask:REPORT_GENERATE] 执行完成: taskId={}, reportId={}",
          task.getId(), result.reportId());
    } catch (Exception e) {
      log.error("[AsyncTask:REPORT_GENERATE] 执行失败: taskId={}, error={}",
          task.getId(), e.getMessage(), e);
      task.fail("报告生成失败: " + truncate(e.getMessage(), 200));
    }
  }

  private Map<String, Object> parseInputPayload(String payload) {
    if (payload == null || payload.isBlank()) {
      return Map.of();
    }
    try {
      Map<String, Object> result = YdszJson.parseMap(payload);
      return result != null ? result : Map.of();
    } catch (Exception e) {
      log.warn("[AsyncTask:REPORT_GENERATE] 解析输入 JSON 失败: {}", e.getMessage());
      return Map.of();
    }
  }

  private String getStringParam(Map<String, Object> params, String key) {
    return getStringParam(params, key, null);
  }

  private String getStringParam(Map<String, Object> params, String key, String defaultValue) {
    Object value = params.get(key);
    if (value == null) {
      return defaultValue;
    }
    String str = value.toString().trim();
    return str.isEmpty() ? defaultValue : str;
  }

  private void consumeProgress(Consumer<Integer> progressConsumer, int percent) {
    if (progressConsumer != null) {
      progressConsumer.accept(percent);
    }
  }

  private static String truncate(String str, int maxLength) {
    if (str == null) {
      return "null";
    }
    return str.length() > maxLength ? str.substring(0, maxLength) + "..." : str;
  }
}
