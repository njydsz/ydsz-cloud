package com.njydsz.workflow.web.controller;

import static org.mockito.ArgumentMatchers.anyString;
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
import com.njydsz.workflow.domain.vo.FlowDelegateAuthVO;
import com.njydsz.workflow.server.service.FlowDelegateAuthService;

/**
 * {@link FlowDelegateAuthController} Smoke Test。
 *
 * <p>验证委托授权核心端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更。
 *
 * @author ydsz-smoke-test
 * @since 26.10.10
 */
class FlowDelegateAuthControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private FlowDelegateAuthService delegateAuthService;

  @org.mockito.InjectMocks
  private FlowDelegateAuthController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /workflow/engine/delegateAuth/mine")
  class ListMyDelegateAuths {

    @Test
    @DisplayName("should return 200 when list my delegate auths succeeds")
    void should_return_200_when_list_my_delegate_auths_succeeds() throws Exception {
      FlowDelegateAuthVO vo = new FlowDelegateAuthVO();
      vo.setId("da-001");
      when(delegateAuthService.listMine(anyString(), anyString(), any())).thenReturn(List.of(vo));

      mockMvc.perform(get("/workflow/engine/delegateAuth/mine"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /workflow/engine/delegateAuth/asDelegate")
  class ListAsDelegate {

    @Test
    @DisplayName("should return 200 when list as delegate succeeds")
    void should_return_200_when_list_as_delegate_succeeds() throws Exception {
      when(delegateAuthService.listAsDelegate(anyString(), anyString(), any())).thenReturn(List.of());

      mockMvc.perform(get("/workflow/engine/delegateAuth/asDelegate"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
