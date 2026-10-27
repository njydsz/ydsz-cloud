package com.njydsz.message.web.controller.config;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.auth.constant.AuthHeaderConstants;
import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.auth.model.LoginUser;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.message.domain.vo.MsgUserChannelVO;
import com.njydsz.message.server.service.config.UserChannelBindingService;

/**
 * {@link UserChannelBindingController} Smoke Test。
 *
 * <p>验证用户通道绑定端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * <p>mine 端点通过 {@code AuthContextUtils.getUserId()} 读取登录态，未写入登录上下文时抛 401
 * {@code SysException}，因此该用例需先装配当前登录用户。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class UserChannelBindingControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @Mock
  private UserChannelBindingService userChannelBindingService;

  @InjectMocks
  private UserChannelBindingController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @AfterEach
  void tearDown() {
    RequestContext.clear();
  }

  @Nested
  @DisplayName("GET /message/user-channels/mine")
  class ListMine {

    @Test
    @DisplayName("should return 200 when list mine succeeds")
    void should_return_200_when_list_mine_succeeds() throws Exception {
      LoginUser loginUser = new LoginUser();
      loginUser.setUserId("user-001");
      AuthContextUtils.setCurrent(loginUser);

      MsgUserChannelVO vo = new MsgUserChannelVO();
      vo.setId("binding-001");
      vo.setUserId("user-001");
      vo.setChannelType("EMAIL");
      when(userChannelBindingService.listByUser("user-001")).thenReturn(List.of(vo));

      mockMvc.perform(get("/message/user-channels/mine")
              .header(AuthHeaderConstants.X_USER_ID, "user-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data[0].id").value("binding-001"))
          .andExpect(jsonPath("$.data[0].channelType").value("EMAIL"));
    }
  }
}
