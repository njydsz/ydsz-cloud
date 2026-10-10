package com.njydsz.message.web.controller.template;

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
import com.njydsz.message.domain.vo.MsgTemplateVersionVO;
import com.njydsz.message.server.service.template.TemplateVersionService;

/**
 * {@link TemplateVersionController} Smoke Test。
 *
 * <p>验证模板版本管理端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class TemplateVersionControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private TemplateVersionService templateVersionService;

  @org.mockito.InjectMocks
  private TemplateVersionController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /message/template/version/list/{templateCode}")
  class ListVersions {

    @Test
    @DisplayName("should return 200 when list versions succeeds")
    void should_return_200_when_list_versions_succeeds() throws Exception {
      when(templateVersionService.listVersions("ORDER_NOTIFY")).thenReturn(List.of());

      mockMvc.perform(get("/message/template/version/list/{templateCode}", "ORDER_NOTIFY"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
