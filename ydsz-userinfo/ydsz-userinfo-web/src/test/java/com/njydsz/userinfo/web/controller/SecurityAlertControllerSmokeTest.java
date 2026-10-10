package com.njydsz.userinfo.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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

  @org.mockito.Mock
  private SecurityAlertService alertService;

  @org.mockito.InjectMocks
  private SecurityAlertController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
    when(alertService.pageAlerts(any(SecurityAlertPageQuery.class)))
        .thenReturn(PageResponse.success(0L, 1L, 20L, List.of()));
    when(alertService.findPendingAlerts(any(), anyInt())).thenReturn(List.of());
  }

  @Nested
  @DisplayName("GET /admin/security/alerts")
  class PageAlerts {

    @Test
    @DisplayName("should return 200 when page query succeeds")
    void should_return_200_when_page_query_succeeds() throws Exception {
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
      mockMvc.perform(get("/admin/security/alerts/pending").param("limit", "50"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
