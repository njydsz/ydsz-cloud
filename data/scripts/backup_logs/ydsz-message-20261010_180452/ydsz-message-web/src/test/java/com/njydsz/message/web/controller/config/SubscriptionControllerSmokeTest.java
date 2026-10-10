package com.njydsz.message.web.controller.config;

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
import com.njydsz.message.domain.vo.MsgSubscriptionVO;
import com.njydsz.message.server.service.config.SubscriptionService;

/**
 * {@link SubscriptionController} Smoke Test。
 *
 * <p>验证订阅关系端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class SubscriptionControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private SubscriptionService subscriptionService;

  @org.mockito.InjectMocks
  private SubscriptionController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /message/subscription/user/{userId}")
  class ListByUser {

    @Test
    @DisplayName("should return 200 when list by user succeeds")
    void should_return_200_when_list_by_user_succeeds() throws Exception {
      when(subscriptionService.listByUser("user-001")).thenReturn(List.of());

      mockMvc.perform(get("/message/subscription/user/{userId}", "user-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
