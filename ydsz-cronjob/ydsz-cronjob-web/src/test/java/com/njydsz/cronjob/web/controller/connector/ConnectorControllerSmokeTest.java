package com.njydsz.cronjob.web.controller.connector;

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
import com.njydsz.cronjob.server.core.connector.ConnectorManager;

/**
 * {@link ConnectorController} Smoke Test。
 *
 * <p>验证生态连接器端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class ConnectorControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private ConnectorManager connectorManager;

  @org.mockito.InjectMocks
  private ConnectorController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /cronjob/connector/types")
  class Types {

    @Test
    @DisplayName("should return 200 when types query succeeds")
    void should_return_200_when_types_query_succeeds() throws Exception {
      when(connectorManager.getRegisteredTypes()).thenReturn(List.of("XXL_JOB", "POWER_JOB"));

      mockMvc.perform(get("/cronjob/connector/types"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
