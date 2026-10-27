package com.njydsz.workflow.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.workflow.domain.vo.FlowAnalyticsOverviewVO;
import com.njydsz.workflow.server.service.FlowAnalyticsService;
import com.njydsz.workflow.server.service.FlowHistoryArchiveService;
import com.njydsz.workflow.server.service.FlowI18nService;
import com.njydsz.workflow.web.controller.analytics.FlowAnalyticsController;

/**
 * {@link FlowAnalyticsController} Smoke Test。
 *
 * <p>验证审批数据分析核心端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更。
 *
 * @author ydsz-smoke-test
 * @since 26.10.10
 */
class FlowAnalyticsControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @Mock
  private FlowAnalyticsService analyticsService;

  @Mock
  private ApplicationEventPublisher eventPublisher;

  @Mock
  private FlowHistoryArchiveService archiveService;

  @Mock
  private FlowI18nService i18nService;

  @InjectMocks
  private FlowAnalyticsController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /workflow/analytics/overview")
  class Overview {

    @Test
    @DisplayName("should return 200 when analytics overview query succeeds")
    void should_return_200_when_overview_succeeds() throws Exception {
      // Controller 透传 TenantContextHolder.getTenantId()；standalone MockMvc 无租户上下文，实参为 null
      when(analyticsService.overview(any(), any(), isNull()))
          .thenReturn(new FlowAnalyticsOverviewVO());

      mockMvc.perform(get("/workflow/analytics/overview"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /workflow/analytics/history/config")
  class HistoryConfig {

    @Test
    @DisplayName("should return 200 when archive config query succeeds")
    void should_return_200_when_history_config_succeeds() throws Exception {
      when(archiveService.getArchiveConfig()).thenReturn(new HashMap<>());

      mockMvc.perform(get("/workflow/analytics/history/config"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
