package com.njydsz.nextwiki.web.controller;

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

import com.njydsz.common.auth.constant.AuthHeaderConstants;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.nextwiki.domain.vo.SpaceVO;
import com.njydsz.nextwiki.server.service.SpaceApplicationService;

/**
 * {@link SpaceController} Smoke Test。
 *
 * <p>验证知识库空间管理端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class SpaceControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private SpaceApplicationService spaceApplicationService;

  @org.mockito.InjectMocks
  private SpaceController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /nextwiki/spaces")
  class ListSpaces {

    @Test
    @DisplayName("should return 200 when list spaces succeeds")
    void should_return_200_when_list_spaces_succeeds() throws Exception {
      when(spaceApplicationService.listSpaces("user-001")).thenReturn(List.of());

      mockMvc.perform(get("/nextwiki/spaces")
              .header(AuthHeaderConstants.X_USER_ID, "user-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
