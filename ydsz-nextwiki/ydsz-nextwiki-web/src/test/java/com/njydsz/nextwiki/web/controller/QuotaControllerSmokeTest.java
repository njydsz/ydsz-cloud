package com.njydsz.nextwiki.web.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.nextwiki.server.service.QuotaApplicationService;

/**
 * {@link QuotaController} Smoke Test。
 *
 * <p>验证存储配额端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class QuotaControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private QuotaApplicationService quotaApplicationService;

  @org.mockito.InjectMocks
  private QuotaController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /nextwiki/quota/info")
  class QuotaInfo {

    @Test
    @DisplayName("should return 200 when get quota info succeeds")
    void should_return_200_when_get_quota_info_succeeds() throws Exception {
      when(quotaApplicationService.getQuotaInfo("user", "user-001"))
          .thenReturn(new com.njydsz.nextwiki.domain.vo.StorageQuotaVO());

      mockMvc.perform(get("/nextwiki/quota/info").param("scopeId", "user-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
