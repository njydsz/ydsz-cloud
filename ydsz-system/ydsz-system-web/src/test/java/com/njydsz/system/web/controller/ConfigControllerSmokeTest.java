package com.njydsz.system.web.controller;

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
import com.njydsz.system.domain.query.ConfigPageQuery;
import com.njydsz.system.domain.vo.ConfigVO;
import com.njydsz.system.server.service.ConfigService;

/**
 * {@link ConfigController} Smoke Test。
 *
 * <p>验证系统配置端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class ConfigControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private ConfigService configService;

  @org.mockito.InjectMocks
  private ConfigController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /config/page")
  class Page {

    @Test
    @DisplayName("should return 200 when page query succeeds")
    void should_return_200_when_page_query_succeeds() throws Exception {
      ConfigVO vo = new ConfigVO();
      vo.setId("config-001");
      vo.setConfigKey("ydsz.test.key");
      PageResponse<List<ConfigVO>> pageResult = PageResponse.success(1L, 1L, 20L, List.of(vo));
      when(configService.page(any(ConfigPageQuery.class))).thenReturn(pageResult);

      mockMvc.perform(get("/config/page").param("pageNum", "1").param("pageSize", "20"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /config/{id}")
  class GetById {

    @Test
    @DisplayName("should return 200 when get by id succeeds")
    void should_return_200_when_get_by_id_succeeds() throws Exception {
      ConfigVO vo = new ConfigVO();
      vo.setId("config-001");
      vo.setConfigKey("ydsz.test.key");
      when(configService.getById("config-001")).thenReturn(vo);

      mockMvc.perform(get("/config/{id}", "config-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /config/public")
  class ListPublicConfigs {

    @Test
    @DisplayName("should return 200 when list public configs succeeds")
    void should_return_200_when_list_public_configs_succeeds() throws Exception {
      when(configService.listPublicConfigs()).thenReturn(List.of());

      mockMvc.perform(get("/config/public"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
