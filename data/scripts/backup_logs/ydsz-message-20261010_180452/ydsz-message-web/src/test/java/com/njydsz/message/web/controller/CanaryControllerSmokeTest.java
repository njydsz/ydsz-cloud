package com.njydsz.message.web.controller;

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
import com.njydsz.message.server.service.config.CanaryExperimentService;

/**
 * {@link CanaryController} Smoke Test。
 *
 * <p>验证灰度实验端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class CanaryControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private CanaryExperimentService canaryExperimentService;

  @org.mockito.InjectMocks
  private CanaryController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /message/canary/assign")
  class AssignBucket {

    @Test
    @DisplayName("should return 200 when assign bucket succeeds")
    void should_return_200_when_assign_bucket_succeeds() throws Exception {
      when(canaryExperimentService.assignBucket("exp-001", "user-001")).thenReturn("CONTROL");

      mockMvc.perform(get("/message/canary/assign")
              .param("experimentId", "exp-001")
              .param("requestKey", "user-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
