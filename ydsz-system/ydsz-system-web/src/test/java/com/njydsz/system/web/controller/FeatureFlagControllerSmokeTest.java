package com.njydsz.system.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.core.feature.FeatureFlagService;
import com.njydsz.common.test.BaseControllerMockTest;

/**
 * {@link FeatureFlagController} Smoke Test。
 *
 * <p>验证远程特性开关查询端点可正确返回 HTTP 200 + YdszResponse 信封结构。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class FeatureFlagControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private FeatureFlagService featureFlagService;

  @org.mockito.InjectMocks
  private FeatureFlagController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
    when(featureFlagService.getFeatureFlags()).thenReturn(Map.of());
  }

  @Nested
  @DisplayName("GET /feature-flags/me")
  class Me {

    @Test
    @DisplayName("should return 200 when feature flags query succeeds")
    void should_return_200_when_feature_flags_query_succeeds() throws Exception {
      mockMvc.perform(get("/feature-flags/me"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
