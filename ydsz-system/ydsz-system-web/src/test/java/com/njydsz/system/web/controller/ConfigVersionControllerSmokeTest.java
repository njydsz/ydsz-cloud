package com.njydsz.system.web.controller;

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
import com.njydsz.system.server.service.ConfigService;
import com.njydsz.system.server.service.EntityVersionService;

/**
 * {@link ConfigVersionController} Smoke Test。
 *
 * <p>验证配置版本查询端点可正确返回 HTTP 200 + YdszResponse 信封结构。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class ConfigVersionControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private EntityVersionService entityVersionService;

  @org.mockito.Mock
  private ConfigService configService;

  @org.mockito.InjectMocks
  private ConfigVersionController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
    when(entityVersionService.listByResourceTypeAndKey(
        EntityVersionService.RESOURCE_TYPE_CONFIG, "ydsz.test.key"))
        .thenReturn(List.of());
    when(entityVersionService.diffConfigVersions(
        EntityVersionService.RESOURCE_TYPE_CONFIG, "ydsz.test.key", "1", "2"))
        .thenReturn(null);
  }

  @Nested
  @DisplayName("GET /config/version/{resourceKey}")
  class ListByResourceKey {

    @Test
    @DisplayName("should return 200 when list succeeds")
    void should_return_200_when_list_succeeds() throws Exception {
      mockMvc.perform(get("/config/version/{resourceKey}", "ydsz.test.key"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /config/version/{resourceKey}/diff")
  class DiffVersions {

    @Test
    @DisplayName("should return 200 when diff succeeds")
    void should_return_200_when_diff_succeeds() throws Exception {
      mockMvc.perform(get("/config/version/{resourceKey}/diff", "ydsz.test.key")
              .param("fromVersion", "1")
              .param("toVersion", "2"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
