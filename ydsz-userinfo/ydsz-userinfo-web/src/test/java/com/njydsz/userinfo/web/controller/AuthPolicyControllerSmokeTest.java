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
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.userinfo.domain.query.AuthPolicyPageQuery;
import com.njydsz.userinfo.server.service.AuthPolicyService;

/**
 * {@link AuthPolicyController} Smoke Test。
 *
 * <p>验证认证策略查询端点可正确返回 HTTP 200 + YdszResponse 信封结构。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class AuthPolicyControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private AuthPolicyService authPolicyService;

  @org.mockito.InjectMocks
  private AuthPolicyController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
    when(authPolicyService.findByPage(any(AuthPolicyPageQuery.class))).thenReturn(List.of());
    when(authPolicyService.findByTenantId("tenant-001")).thenReturn(null);
  }

  @Nested
  @DisplayName("GET /auth-policy/page")
  class Page {

    @Test
    @DisplayName("should return 200 when page query succeeds")
    void should_return_200_when_page_query_succeeds() throws Exception {
      mockMvc.perform(get("/auth-policy/page").param("pageNum", "1").param("pageSize", "20"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /auth-policy/{tenantId}")
  class GetByTenantId {

    @Test
    @DisplayName("should return 200 when get by tenant id succeeds")
    void should_return_200_when_get_by_tenant_id_succeeds() throws Exception {
      mockMvc.perform(get("/auth-policy/{tenantId}", "tenant-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
