package com.njydsz.userinfo.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.userinfo.domain.alert.SecurityAlert;
import com.njydsz.userinfo.domain.query.SecurityAlertPageQuery;
import com.njydsz.userinfo.server.alert.SecurityAlertService;

/**
 * {@link SecurityAlertController} Smoke Test。
 *
 * <p>验证安全告警查询端点可正确返回 HTTP 200 + YdszResponse 信封结构。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class SecurityAlertControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @Mock
  private SecurityAlertService alertService;

  @InjectMocks
  private SecurityAlertController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /admin/security/alerts")
  class PageAlerts {

    @Test
    @DisplayName("should return 200 when page query succeeds")
    void should_return_200_when_page_query_succeeds() throws Exception {
      when(alertService.pageAlerts(any(SecurityAlertPageQuery.class)))
          .thenReturn(PageResponse.success(0L, 1L, 20L, List.of()));

      mockMvc.perform(get("/admin/security/alerts").param("pageNum", "1").param("pageSize", "20"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /admin/security/alerts/pending")
  class GetPendingAlerts {

    @Test
    @DisplayName("should return 200 when pending query succeeds")
    void should_return_200_when_pending_query_succeeds() throws Exception {
      // riskLevel 未传时 Controller 传入 null，limit 走 @RequestParam 默认值 50
      when(alertService.findPendingAlerts(null, 50)).thenReturn(List.of());

      mockMvc.perform(get("/admin/security/alerts/pending").param("limit", "50"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
