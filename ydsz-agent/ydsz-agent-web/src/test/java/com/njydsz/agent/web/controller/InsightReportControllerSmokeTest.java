package com.njydsz.agent.web.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.agent.domain.insight.InsightReportService;
import com.njydsz.common.test.BaseControllerMockTest;

/**
 * {@link InsightReportController} Smoke Test.
 *
 * <p>Verify insight report endpoints return HTTP 200 + YdszResponse envelope.
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class InsightReportControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private InsightReportService insightReportService;

  @org.mockito.InjectMocks
  private InsightReportController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /agent/insight/reports")
  class ListRecentReports {

    @Test
    @DisplayName("should return 200 when list reports succeeds")
    void should_return_200_when_list_reports_succeeds() throws Exception {
      when(insightReportService.listRecentReports("user-001", 10)).thenReturn(List.of());

      mockMvc.perform(get("/agent/insight/reports")
              .param("userId", "user-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
