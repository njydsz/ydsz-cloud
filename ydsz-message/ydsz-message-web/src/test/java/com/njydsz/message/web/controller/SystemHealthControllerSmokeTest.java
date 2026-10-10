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
import com.njydsz.message.domain.vo.SystemHealthVO;
import com.njydsz.message.server.service.core.MessageHealthService;

/**
 * {@link SystemHealthController} Smoke Test。
 *
 * <p>验证系统健康检查端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class SystemHealthControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private MessageHealthService messageHealthService;

  @org.mockito.InjectMocks
  private SystemHealthController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /message/health")
  class GetSystemHealth {

    @Test
    @DisplayName("should return 200 when get system health succeeds")
    void should_return_200_when_get_system_health_succeeds() throws Exception {
      SystemHealthVO vo = new SystemHealthVO();
      vo.setStatus("UP");
      when(messageHealthService.getSystemHealth()).thenReturn(vo);

      mockMvc.perform(get("/message/health"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
