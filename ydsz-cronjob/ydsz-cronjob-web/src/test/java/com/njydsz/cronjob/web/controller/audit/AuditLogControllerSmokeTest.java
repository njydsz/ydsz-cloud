package com.njydsz.cronjob.web.controller.audit;

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
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.audit.core.AuditQueryService;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.test.BaseControllerMockTest;

/**
 * {@link AuditLogController} Smoke Test。
 *
 * <p>验证操作审计端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class AuditLogControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private AuditQueryService auditQueryService;

  @org.mockito.InjectMocks
  private AuditLogController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /cronjob/audit/page")
  class Page {

    @Test
    @DisplayName("should return 200 when page succeeds")
    void should_return_200_when_page_succeeds() throws Exception {
      when(auditQueryService.queryByTimeRange(any(), any(), any(int.class), any(int.class)))
          .thenReturn(YdszResponse.success(new java.util.ArrayList<>()));

      mockMvc.perform(get("/cronjob/audit/page").param("pageNum", "1").param("pageSize", "20"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
