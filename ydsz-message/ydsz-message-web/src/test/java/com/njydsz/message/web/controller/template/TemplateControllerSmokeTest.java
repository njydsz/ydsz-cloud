package com.njydsz.message.web.controller.template;

import static org.mockito.ArgumentMatchers.any;
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

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.message.domain.dto.TemplateQueryDTO;
import com.njydsz.message.domain.vo.MsgTemplateVO;
import com.njydsz.message.server.service.TemplateService;

/**
 * {@link TemplateController} Smoke Test。
 *
 * <p>验证消息模板管理端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class TemplateControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private TemplateService templateService;

  @org.mockito.InjectMocks
  private TemplateController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /message/template/page")
  class Page {

    @Test
    @DisplayName("should return 200 when page query succeeds")
    void should_return_200_when_page_query_succeeds() throws Exception {
      MsgTemplateVO vo = new MsgTemplateVO();
      vo.setId("tpl-001");
      vo.setTemplateCode("ORDER_NOTIFY");
      PageResponse<List<MsgTemplateVO>> pageResult = PageResponse.success(1L, 1L, 20L, List.of(vo));
      when(templateService.page(any(TemplateQueryDTO.class))).thenReturn(pageResult);

      mockMvc.perform(get("/message/template/page").param("pageNum", "1").param("pageSize", "20"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /message/template/{id}")
  class GetById {

    @Test
    @DisplayName("should return 200 when get by id succeeds")
    void should_return_200_when_get_by_id_succeeds() throws Exception {
      MsgTemplateVO vo = new MsgTemplateVO();
      vo.setId("tpl-001");
      vo.setTemplateCode("ORDER_NOTIFY");
      when(templateService.getById("tpl-001")).thenReturn(vo);

      mockMvc.perform(get("/message/template/{id}", "tpl-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
