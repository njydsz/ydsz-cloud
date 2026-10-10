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
import com.njydsz.workflow.domain.vo.FlowCategoryVO;
import com.njydsz.workflow.server.service.impl.definition.FlowCategoryServiceImpl;

/**
 * {@link FlowCategoryController} Smoke Test。
 *
 * <p>验证流程分类核心端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更。
 *
 * @author ydsz-smoke-test
 * @since 26.10.10
 */
class FlowCategoryControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private FlowCategoryServiceImpl categoryService;

  @org.mockito.InjectMocks
  private FlowCategoryController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /workflow/categories")
  class List {

    @Test
    @DisplayName("should return 200 when list all categories succeeds")
    void should_return_200_when_list_all_categories_succeeds() throws Exception {
      FlowCategoryVO vo = new FlowCategoryVO();
      vo.setId("cat-001");
      vo.setCategoryCode("HR");
      vo.setCategoryName("人事流程");
      when(categoryService.listAllVO(anyString())).thenReturn(List.of(vo));

      mockMvc.perform(get("/workflow/categories"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /workflow/categories/tree")
  class Tree {

    @Test
    @DisplayName("should return 200 when category tree query succeeds")
    void should_return_200_when_category_tree_succeeds() throws Exception {
      when(categoryService.tree(anyString())).thenReturn(List.of());

      mockMvc.perform(get("/workflow/categories/tree"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
