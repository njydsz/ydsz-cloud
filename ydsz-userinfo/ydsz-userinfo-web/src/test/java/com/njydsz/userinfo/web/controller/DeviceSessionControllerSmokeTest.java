package com.njydsz.userinfo.web.controller.device;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.userinfo.server.device.DeviceSessionService;

/**
 * {@link DeviceSessionController} Smoke Test。
 *
 * <p>验证设备会话查询端点可正确返回 HTTP 200 + YdszResponse 信封结构。
 *
 * <p>端点通过 {@code RequestContext.getUserId()} 取当前用户，故测试需先写入请求上下文。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class DeviceSessionControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @Mock
  private DeviceSessionService deviceSessionService;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(new DeviceSessionController(deviceSessionService));
  }

  @AfterEach
  void tearDown() {
    RequestContext.clear();
  }

  @Nested
  @DisplayName("GET /devices")
  class ListDevices {

    @Test
    @DisplayName("should return 200 when list devices succeeds")
    void should_return_200_when_list_devices_succeeds() throws Exception {
      RequestContext.setUserId("user-001");

      when(deviceSessionService.listMyDevices("user-001")).thenReturn(List.of());

      mockMvc.perform(get("/devices"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
