package com.njydsz.system.web.controller;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.system.domain.vo.FrontendInitVO;
import com.njydsz.system.server.service.FrontendInitService;

/**
 * {@link FrontendInitController} Smoke Test。
 *
 * <p>验证前端初始化端点可正确返回 HTTP 200 + YdszResponse 信封结构。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class FrontendInitControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private FrontendInitService frontendInitService;

  @org.mockito.InjectMocks
  private FrontendInitController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
    when(frontendInitService.getInitData()).thenReturn(new FrontendInitVO());
    when(frontendInitService.getInitDataWithDicts(anyList())).thenReturn(new FrontendInitVO());
  }

  @Nested
  @DisplayName("GET /system/init")
  class Init {

    @Test
    @DisplayName("should return 200 when init succeeds")
    void should_return_200_when_init_succeeds() throws Exception {
      mockMvc.perform(get("/system/init"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /system/init/dicts")
  class InitWithDicts {

    @Test
    @DisplayName("should return 200 when init with dicts succeeds")
    void should_return_200_when_init_with_dicts_succeeds() throws Exception {
      mockMvc.perform(get("/system/init/dicts").param("dictTypes", "user_status,gender"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
