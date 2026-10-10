package com.njydsz.system.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.audit.core.AuditQueryService;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.test.BaseControllerMockTest;

/**
 * {@link AuditAdminController} Smoke Test。
 *
 * <p>验证审计日志查询端点可正确返回 HTTP 200 + YdszResponse 信封结构。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class AuditAdminControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private AuditQueryService auditQueryService;

  @org.mockito.InjectMocks
  private AuditAdminController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
    when(auditQueryService.queryByTraceId(any())).thenReturn(List.of());
    when(auditQueryService.getById(any())).thenReturn(null);
    when(auditQueryService.queryByTimeRange(
        any(LocalDateTime.class), any(LocalDateTime.class), anyInt(), anyInt()))
        .thenReturn(YdszResponse.error("A00000", "success"));
  }

  @Nested
  @DisplayName("GET /admin/audit/trace/{traceId}")
  class QueryByTraceId {

    @Test
    @DisplayName("should return 200 when trace query succeeds")
    void should_return_200_when_trace_query_succeeds() throws Exception {
      mockMvc.perform(get("/admin/audit/trace/{traceId}", "trace-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /admin/audit/{id}")
  class GetById {

    @Test
    @DisplayName("should return 200 when get by id succeeds")
    void should_return_200_when_get_by_id_succeeds() throws Exception {
      mockMvc.perform(get("/admin/audit/{id}", "audit-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /admin/audit/logs")
  class QueryByTimeRange {

    @Test
    @DisplayName("should return 200 when time range query succeeds")
    void should_return_200_when_time_range_query_succeeds() throws Exception {
      mockMvc.perform(get("/admin/audit/logs")
              .param("startTime", "2026-01-01 00:00:00")
              .param("endTime", "2026-12-31 23:59:59")
              .param("page", "1")
              .param("size", "20"))
          .andExpect(status().isOk());
    }
  }
}
