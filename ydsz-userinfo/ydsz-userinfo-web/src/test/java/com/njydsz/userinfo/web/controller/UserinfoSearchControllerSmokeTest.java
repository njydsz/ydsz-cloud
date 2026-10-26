package com.njydsz.userinfo.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.search.api.SearchRequest;
import com.njydsz.common.search.api.SearchResponse;
import com.njydsz.common.search.service.UnifiedSearchService;
import com.njydsz.common.test.BaseControllerMockTest;

/**
 * {@link UserinfoSearchController} Smoke Test。
 *
 * <p>验证用户搜索端点可正确返回 HTTP 200 + YdszResponse 信封结构。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class UserinfoSearchControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @Mock
  private UnifiedSearchService unifiedSearchService;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(new UserinfoSearchController(unifiedSearchService));
  }

  @Nested
  @DisplayName("GET /userinfo/search")
  class Search {

    @Test
    @DisplayName("should return 200 when search succeeds")
    void should_return_200_when_search_succeeds() throws Exception {
      // SearchRequest 由 Controller 内部 builder 组装，无法按固定实例声明，故使用类型匹配器
      when(unifiedSearchService.search(any(SearchRequest.class)))
          .thenReturn(SearchResponse.empty(1, 20));

      mockMvc.perform(get("/userinfo/search").param("keyword", "admin").param("page", "1").param("pageSize", "20"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
