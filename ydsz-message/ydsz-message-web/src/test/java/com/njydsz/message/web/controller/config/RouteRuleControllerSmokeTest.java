package com.njydsz.message.web.controller.config;

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
import com.njydsz.message.domain.vo.MsgRouteRuleVO;
import com.njydsz.message.server.service.config.RouteRuleService;

/**
 * {@link RouteRuleController} Smoke Test。
 *
 * <p>验证消息路由规则端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class RouteRuleControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private RouteRuleService routeRuleService;

  @org.mockito.InjectMocks
  private RouteRuleController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /message/route-rule/page")
  class Page {

    @Test
    @DisplayName("should return 200 when page query succeeds")
    void should_return_200_when_page_query_succeeds() throws Exception {
      MsgRouteRuleVO vo = new MsgRouteRuleVO();
      vo.setId("route-001");
      PageResponse<List<MsgRouteRuleVO>> pageResult = PageResponse.success(1L, 1L, 20L, List.of(vo));
      when(routeRuleService.page(any())).thenReturn(pageResult);

      mockMvc.perform(get("/message/route-rule/page").param("pageNum", "1").param("pageSize", "20"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /message/route-rule/{id}")
  class GetById {

    @Test
    @DisplayName("should return 200 when get by id succeeds")
    void should_return_200_when_get_by_id_succeeds() throws Exception {
      MsgRouteRuleVO vo = new MsgRouteRuleVO();
      vo.setId("route-001");
      when(routeRuleService.getById("route-001")).thenReturn(vo);

      mockMvc.perform(get("/message/route-rule/{id}", "route-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
