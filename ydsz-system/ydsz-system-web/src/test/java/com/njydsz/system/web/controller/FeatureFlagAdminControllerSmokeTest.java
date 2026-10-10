package com.njydsz.system.web.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.test.BaseControllerMockTest;

/**
 * {@link FeatureFlagAdminController} Smoke Test。
 *
 * <p>验证特性开关分页查询端点可正确返回 HTTP 200 + YdszResponse 信封结构。
 * 该 Controller 基于内存 ConcurrentHashMap 存储，无需 mock 依赖。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class FeatureFlagAdminControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    FeatureFlagAdminController featureFlagController = new FeatureFlagAdminController();
    mockMvc = standaloneSetup(featureFlagController);
  }

  @Nested
  @DisplayName("GET /feature-flag/page")
  class Page {

    @Test
    @DisplayName("should return 200 when page query succeeds")
    void should_return_200_when_page_query_succeeds() throws Exception {
      mockMvc.perform(get("/feature-flag/page").param("pageNum", "1").param("pageSize", "20"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
