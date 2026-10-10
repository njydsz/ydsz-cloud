package com.njydsz.system.web.controller;

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

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.system.domain.approval.ConfigApprovalQuery;
import com.njydsz.system.server.converter.ConfigApprovalConverter;
import com.njydsz.system.server.service.ConfigApprovalService;

/**
 * {@link ConfigApprovalController} Smoke Test。
 *
 * <p>验证配置变更审批查询端点可正确返回 HTTP 200 + YdszResponse 信封结构。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class ConfigApprovalControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private ConfigApprovalService approvalService;

  @org.mockito.Mock
  private ConfigApprovalConverter approvalConverter;

  @org.mockito.InjectMocks
  private ConfigApprovalController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
    when(approvalService.findAll(any(ConfigApprovalQuery.class))).thenReturn(List.of());
    when(approvalService.findById("approval-001")).thenReturn(null);
  }

  @Nested
  @DisplayName("GET /config/approval/list")
  class ListAll {

    @Test
    @DisplayName("should return 200 when list all succeeds")
    void should_return_200_when_list_all_succeeds() throws Exception {
      mockMvc.perform(get("/config/approval/list").param("pageNum", "1").param("pageSize", "20"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /config/approval/{id}")
  class Detail {

    @Test
    @DisplayName("should return 200 when detail query succeeds")
    void should_return_200_when_detail_query_succeeds() throws Exception {
      mockMvc.perform(get("/config/approval/{id}", "approval-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> T any(Class<T> clazz) {
    return org.mockito.ArgumentMatchers.any(clazz);
  }
}
