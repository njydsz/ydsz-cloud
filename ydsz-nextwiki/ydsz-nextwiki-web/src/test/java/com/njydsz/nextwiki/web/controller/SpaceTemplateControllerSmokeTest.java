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

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.nextwiki.domain.dto.SpaceTemplateDTO;
import com.njydsz.nextwiki.server.service.SpaceTemplateApplicationService;

/**
 * {@link SpaceTemplateController} Smoke Test。
 *
 * <p>验证空间模板端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class SpaceTemplateControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private SpaceTemplateApplicationService spaceTemplateApplicationService;

  @org.mockito.InjectMocks
  private SpaceTemplateController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /nextwiki/templates")
  class ListTemplates {

    @Test
    @DisplayName("should return 200 when list space templates succeeds")
    void should_return_200_when_list_space_templates_succeeds() throws Exception {
      when(spaceTemplateApplicationService.listTemplates(null)).thenReturn(List.of(new SpaceTemplateDTO()));

      mockMvc.perform(get("/nextwiki/templates"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
