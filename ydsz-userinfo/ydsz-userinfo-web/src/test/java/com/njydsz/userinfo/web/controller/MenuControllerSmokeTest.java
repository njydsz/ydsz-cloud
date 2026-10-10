package com.njydsz.userinfo.web.controller;

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
import com.njydsz.userinfo.server.service.MenuService;

/**
 * {@link MenuController} Smoke Test。
 *
 * <p>验证菜单查询端点可正确返回 HTTP 200 + YdszResponse 信封结构。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class MenuControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private MenuService menuService;

  @org.mockito.InjectMocks
  private MenuController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
    when(menuService.list()).thenReturn(List.of());
    when(menuService.tree()).thenReturn(List.of());
    when(menuService.getById("menu-001")).thenReturn(null);
  }

  @Nested
  @DisplayName("GET /menu/list")
  class List {

    @Test
    @DisplayName("should return 200 when list succeeds")
    void should_return_200_when_list_succeeds() throws Exception {
      mockMvc.perform(get("/menu/list"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /menu/tree")
  class Tree {

    @Test
    @DisplayName("should return 200 when tree query succeeds")
    void should_return_200_when_tree_query_succeeds() throws Exception {
      mockMvc.perform(get("/menu/tree"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /menu/{id}")
  class GetById {

    @Test
    @DisplayName("should return 200 when get by id succeeds")
    void should_return_200_when_get_by_id_succeeds() throws Exception {
      mockMvc.perform(get("/menu/{id}", "menu-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
