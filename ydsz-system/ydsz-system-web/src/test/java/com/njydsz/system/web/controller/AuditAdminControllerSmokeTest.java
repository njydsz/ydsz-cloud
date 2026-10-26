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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.audit.core.AuditQueryService;
import com.njydsz.common.audit.domain.AuditLog;
import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.core.response.PageResponse;
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

  @Mock
  private AuditQueryService auditQueryService;

  @InjectMocks
  private AuditAdminController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /admin/audit/trace/{traceId}")
  class QueryByTraceId {

    @Test
    @DisplayName("should return 200 when trace query succeeds")
    void should_return_200_when_trace_query_succeeds() throws Exception {
      when(auditQueryService.queryByTraceId("trace-001")).thenReturn(List.of(auditLog()));

      mockMvc.perform(get("/admin/audit/trace/{traceId}", "trace-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data[0].traceId").value("trace-001"));
    }
  }

  @Nested
  @DisplayName("GET /admin/audit/{id}")
  class GetById {

    @Test
    @DisplayName("should return 200 when get by id succeeds")
    void should_return_200_when_get_by_id_succeeds() throws Exception {
      when(auditQueryService.getById("audit-001")).thenReturn(auditLog());

      mockMvc.perform(get("/admin/audit/{id}", "audit-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.id").value("audit-001"));
    }

    @Test
    @DisplayName("should return not-found code when audit log missing")
    void should_return_not_found_when_audit_log_missing() throws Exception {
      when(auditQueryService.getById("audit-404")).thenReturn(null);

      mockMvc.perform(get("/admin/audit/{id}", "audit-404"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(YdszResultCode.NOT_FOUND.getCode()));
    }
  }

  @Nested
  @DisplayName("GET /admin/audit/logs")
  class QueryByTimeRange {

    @Test
    @DisplayName("should return 200 when time range query succeeds")
    void should_return_200_when_time_range_query_succeeds() throws Exception {
      when(auditQueryService.queryByTimeRange(
              any(LocalDateTime.class), any(LocalDateTime.class), anyInt(), anyInt()))
          .thenReturn(PageResponse.success(1L, 1L, 20L, List.of(auditLog())));

      mockMvc.perform(get("/admin/audit/logs")
              .param("startTime", "2026-01-01 00:00:00")
              .param("endTime", "2026-12-31 23:59:59")
              .param("page", "1")
              .param("size", "20"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.total").value(1));
    }
  }

  /** 与 {@link AuditQueryService} 真实返回结构一致的审计日志夹具。 */
  private static AuditLog auditLog() {
    AuditLog log = new AuditLog();
    log.setId("audit-001");
    log.setOperatorId("user-001");
    log.setTraceId("trace-001");
    return log;
  }
}
