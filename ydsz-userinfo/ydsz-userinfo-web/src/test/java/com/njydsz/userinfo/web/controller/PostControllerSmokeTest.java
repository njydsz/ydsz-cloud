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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.userinfo.server.service.PostService;

/**
 * {@link PostController} Smoke Test。
 *
 * <p>验证岗位管理查询端点可正确返回 HTTP 200 + YdszResponse 信封结构。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class PostControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @Mock
  private PostService postService;

  @InjectMocks
  private PostController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /post/list")
  class ListQuery {

    @Test
    @DisplayName("should return 200 when list succeeds")
    void should_return_200_when_list_succeeds() throws Exception {
      when(postService.list()).thenReturn(List.of());

      mockMvc.perform(get("/post/list"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /post/{id}")
  class GetById {

    @Test
    @DisplayName("should return 200 when get by id succeeds")
    void should_return_200_when_get_by_id_succeeds() throws Exception {
      when(postService.getById("post-001")).thenReturn(null);

      mockMvc.perform(get("/post/{id}", "post-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
