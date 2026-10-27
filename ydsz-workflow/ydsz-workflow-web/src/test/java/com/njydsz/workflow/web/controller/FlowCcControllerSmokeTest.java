package com.njydsz.workflow.web.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.auth.model.LoginUser;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.workflow.server.service.FlowCcService;

/**
 * {@link FlowCcController} Smoke Test。
 *
 * <p>验证抄送中心核心端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更。
 *
 * <p>端点通过 {@code AuthContextUtils.getUserId()} 读取登录态，未写入登录上下文时抛 401
 * {@code SysException}，因此用例需先装配当前登录用户。
 *
 * @author ydsz-smoke-test
 * @since 26.10.10
 */
class FlowCcControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @Mock
  private FlowCcService ccService;

  @InjectMocks
  private FlowCcController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @AfterEach
  void tearDown() {
    RequestContext.clear();
  }

  @Nested
  @DisplayName("GET /workflow/engine/cc/unreadCount")
  class CcUnreadCount {

    @Test
    @DisplayName("should return 200 when unread count query succeeds")
    void should_return_200_when_unread_count_succeeds() throws Exception {
      LoginUser loginUser = new LoginUser();
      loginUser.setUserId("user-001");
      AuthContextUtils.setCurrent(loginUser);

      when(ccService.countUnread(anyString(), anyString())).thenReturn(5L);

      mockMvc.perform(get("/workflow/engine/cc/unreadCount"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
