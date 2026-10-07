package com.njydsz.agent.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import com.njydsz.agent.domain.insight.InsightReportRequest;
import com.njydsz.agent.domain.insight.InsightReportResult;
import com.njydsz.agent.domain.insight.InsightReportService;
import com.njydsz.agent.domain.insight.InsightReportStatus;
import com.njydsz.agent.domain.insight.InsightSection;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

import com.njydsz.common.json.YdszJson;

/**
 * InsightReportController 集成测试。
 *
 * <p>使用 MockMvcBuilders.standaloneSetup + MockitoExtension 测试洞察报告接口。
 *
 * <p>注意：本测试绕过 @ConditionalOnBean 条件检查，直接实例化 Controller。
 */
@ExtendWith(MockitoExtension.class)
class InsightReportControllerTest {

  private MockMvc mockMvc;

  /**
   * 简单异常解析器：将所有未捕获异常转为 HTTP 500。
   */
  private static final HandlerExceptionResolver EXCEPTION_RESOLVER =
      (request, response, handler, ex) -> {
        try {
          response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (java.io.IOException e) {
          // ignore
        }
        return new ModelAndView();
      };

  @Mock
  private InsightReportService insightReportService;

  private InsightReportController insightReportController;

  @BeforeEach
  void setUp() {
    this.insightReportController = new InsightReportController(insightReportService);
    this.mockMvc =
        MockMvcBuilders.standaloneSetup(insightReportController)
            .setHandlerExceptionResolvers(EXCEPTION_RESOLVER)
            .build();
  }

  @Nested
  @DisplayName("POST /agent/insight/report")
  class GenerateReport {

    @Test
    @DisplayName("生成洞察报告 — 返回报告结果")
    void shouldGenerateReport() throws Exception {
      InsightReportResult result = new InsightReportResult(
          "rpt-001", "销售分析报告",
          "本报告分析了近期销售趋势...",
          List.of(new InsightSection("summary", "摘要", "数据摘要内容", "{}", 1)),
          InsightReportStatus.COMPLETED, null,
          LocalDateTime.now(), 5000L);

      when(insightReportService.generateReport(any(InsightReportRequest.class)))
          .thenReturn(result);

      String body = "{\"userId\":\"user-001\",\"reportTitle\":\"销售分析报告\","
          + "\"dataSourceType\":\"sql\",\"dataJson\":\"{\\\"total\\\": 1000}\","
          + "\"reportFormat\":\"html\"}";

      mockMvc.perform(post("/agent/insight/report")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data.reportId").value("rpt-001"))
          .andExpect(jsonPath("$.data.title").value("销售分析报告"))
          .andExpect(jsonPath("$.data.status").value("COMPLETED"));

      verify(insightReportService, times(1)).generateReport(any(InsightReportRequest.class));
    }
  }

  @Nested
  @DisplayName("GET /agent/insight/report/{reportId}")
  class GetReport {

    @Test
    @DisplayName("查询报告元数据 — 返回报告详情")
    void shouldReturnReportMetadata() throws Exception {
      InsightReportResult result = new InsightReportResult(
          "rpt-001", "测试报告", "报告内容",
          List.of(), InsightReportStatus.DRAFT, null,
          LocalDateTime.now(), 1000L);

      when(insightReportService.getReport("rpt-001")).thenReturn(result);

      mockMvc.perform(get("/agent/insight/report/{reportId}", "rpt-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data.reportId").value("rpt-001"));

      verify(insightReportService, times(1)).getReport("rpt-001");
    }

    @Test
    @DisplayName("查询不存在的报告 — 抛出业务异常（异常解析器映射为 HTTP 500）")
    void shouldThrowWhenReportNotFound() throws Exception {
      when(insightReportService.getReport("rpt-missing")).thenReturn(null);

      // throw new BusinessException(AgentExceptionCode.INSIGHT_REPORT_NOT_FOUND) 被解析器转为 500
      mockMvc.perform(get("/agent/insight/report/{reportId}", "rpt-missing"))
          .andExpect(status().isInternalServerError());

      verify(insightReportService).getReport("rpt-missing");
    }
  }

  @Nested
  @DisplayName("GET /agent/insight/reports")
  class ListRecentReports {

    @Test
    @DisplayName("列出用户近期报告 — 返回报告列表")
    void shouldReturnRecentReports() throws Exception {
      InsightReportResult result1 = new InsightReportResult(
          "rpt-001", "报告一", "内容一",
          List.of(), InsightReportStatus.COMPLETED, null,
          LocalDateTime.now(), 2000L);
      InsightReportResult result2 = new InsightReportResult(
          "rpt-002", "报告二", "内容二",
          List.of(), InsightReportStatus.DRAFT, null,
          LocalDateTime.now(), 1000L);

      when(insightReportService.listRecentReports("user-001", 10))
          .thenReturn(List.of(result1, result2));

      mockMvc.perform(get("/agent/insight/reports")
              .param("userId", "user-001")
              .param("limit", "10"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data").isArray())
          .andExpect(jsonPath("$.data.length()").value(2));

      verify(insightReportService).listRecentReports("user-001", 10);
    }
  }

  @Nested
  @DisplayName("DELETE /agent/insight/report/{reportId}")
  class DeleteReport {

    @Test
    @DisplayName("删除报告 — 返回删除状态")
    void shouldDeleteReport() throws Exception {
      doNothing().when(insightReportService).deleteReport("rpt-001");

      mockMvc.perform(delete("/agent/insight/report/{reportId}", "rpt-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data.status").value("deleted"))
          .andExpect(jsonPath("$.data.reportId").value("rpt-001"));

      verify(insightReportService, times(1)).deleteReport("rpt-001");
    }
  }
}
