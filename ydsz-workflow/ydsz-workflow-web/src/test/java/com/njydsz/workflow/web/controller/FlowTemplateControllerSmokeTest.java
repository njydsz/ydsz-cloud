package com.njydsz.workflow.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
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

import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.auth.model.LoginUser;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.workflow.web.controller.definition.FlowTemplateController;
import com.njydsz.workflow.server.service.FlowTemplateRecommendService;
import com.njydsz.workflow.server.service.FlowTemplateService;

/**
 * {@link FlowTemplateController} Smoke Test。
 *
 * <p>验证流程模板市场核心端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更。
 *
 * <p>智能推荐端点通过 {@code AuthContextUtils.getUserId()} 读取登录态，未写入登录上下文时抛 401
 * {@code SysException}，因此该用例需先装配当前登录用户。
 *
 * @author ydsz-smoke-test
 * @since 26.10.10
 */
class FlowTemplateControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @Mock
  private FlowTemplateService templateService;

  @Mock
  private FlowTemplateRecommendService recommendService;

  @InjectMocks
  private FlowTemplateController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @AfterEach
  void tearDown() {
    RequestContext.clear();
  }

  @Nested
  @DisplayName("GET /workflow/template/list")
  class ListTemplates {

    @Test
    @DisplayName("should return 200 when list templates succeeds")
    void should_return_200_when_list_templates_succeeds() throws Exception {
      when(templateService.listTemplates(any())).thenReturn(List.of());

      mockMvc.perform(get("/workflow/template/list"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /workflow/template/recommend")
  class Recommend {

    @Test
    @DisplayName("should return 200 when recommend templates succeeds")
    void should_return_200_when_recommend_templates_succeeds() throws Exception {
      LoginUser loginUser = new LoginUser();
      loginUser.setUserId("user-001");
      AuthContextUtils.setCurrent(loginUser);

      when(recommendService.recommendTemplates(anyString(), anyString(), anyInt()))
          .thenReturn(List.of());

      mockMvc.perform(get("/workflow/template/recommend")
              .param("topN", "5"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
