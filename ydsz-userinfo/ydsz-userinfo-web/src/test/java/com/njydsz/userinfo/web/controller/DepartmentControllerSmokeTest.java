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
import com.njydsz.userinfo.server.service.DepartmentService;

/**
 * {@link DepartmentController} Smoke Test。
 *
 * <p>验证部门查询端点可正确返回 HTTP 200 + YdszResponse 信封结构。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class DepartmentControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private DepartmentService departmentService;

  @org.mockito.InjectMocks
  private DepartmentController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
    when(departmentService.list()).thenReturn(List.of());
    when(departmentService.tree()).thenReturn(List.of());
    when(departmentService.getById("dept-001")).thenReturn(null);
  }

  @Nested
  @DisplayName("GET /dept/list")
  class List {

    @Test
    @DisplayName("should return 200 when list succeeds")
    void should_return_200_when_list_succeeds() throws Exception {
      mockMvc.perform(get("/dept/list"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /dept/tree")
  class Tree {

    @Test
    @DisplayName("should return 200 when tree query succeeds")
    void should_return_200_when_tree_query_succeeds() throws Exception {
      mockMvc.perform(get("/dept/tree"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /dept/{id}")
  class GetById {

    @Test
    @DisplayName("should return 200 when get by id succeeds")
    void should_return_200_when_get_by_id_succeeds() throws Exception {
      mockMvc.perform(get("/dept/{id}", "dept-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
