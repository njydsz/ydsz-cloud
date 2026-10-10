package com.njydsz.agent.web.controller.trigger;

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

import com.njydsz.agent.server.trigger.TriggerManagementService;
import com.njydsz.common.test.BaseControllerMockTest;

/**
 * {@link TriggerController} Smoke Test.
 *
 * <p>Verify trigger management endpoints return HTTP 200 + YdszResponse envelope.
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class TriggerControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private TriggerManagementService triggerManagementService;

  @org.mockito.InjectMocks
  private TriggerController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /agent/triggers")
  class ListTriggers {

    @Test
    @DisplayName("should return 200 when list triggers succeeds")
    void should_return_200_when_list_triggers_succeeds() throws Exception {
      when(triggerManagementService.listEnabledTriggers("1")).thenReturn(List.of());

      mockMvc.perform(get("/agent/triggers"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /agent/triggers/{triggerId}")
  class GetTrigger {

    @Test
    @DisplayName("should return 200 when get trigger succeeds")
    void should_return_200_when_get_trigger_succeeds() throws Exception {
      when(triggerManagementService.getTrigger("trigger-001", "1")).thenReturn(null);

      mockMvc.perform(get("/agent/triggers/{triggerId}", "trigger-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
