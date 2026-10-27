package com.njydsz.workflow.web.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.auth.model.LoginUser;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.workflow.server.engine.FlowUrgeLimiter;
import com.njydsz.workflow.server.service.FlowInstanceMergeService;
import com.njydsz.workflow.server.service.FlowOfflineAutoForwardService;
import com.njydsz.workflow.server.service.FlowReportService;
import com.njydsz.workflow.server.service.impl.instance.FlowAssigneeDedupService;
import com.njydsz.workflow.server.service.impl.instance.FlowCountersignDynamicService;
import com.njydsz.workflow.web.controller.instance.FlowAdvancedController;

/**
 * {@link FlowAdvancedController} Smoke Test。
 *
 * <p>验证高级功能核心端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更。
 *
 * <p>端点通过 {@code AuthContextUtils.getUserId()} 读取登录态，未写入登录上下文时抛 401
 * {@code SysException}，因此相关用例需先装配当前登录用户。
 *
 * @author ydsz-smoke-test
 * @since 26.10.10
 */
class FlowAdvancedControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @Mock
  private FlowReportService reportService;

  @Mock
  private FlowInstanceMergeService mergeService;

  @Mock
  private FlowAssigneeDedupService dedupService;

  @Mock
  private FlowUrgeLimiter urgeLimiter;

  @Mock
  private FlowOfflineAutoForwardService offlineAutoForwardService;

  @Mock
  private FlowCountersignDynamicService countersignDynamicService;

  @InjectMocks
  private FlowAdvancedController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @AfterEach
  void tearDown() {
    RequestContext.clear();
  }

  @Nested
  @DisplayName("GET /workflow/advanced/report/weekly")
  class WeeklyReport {

    @Test
    @DisplayName("should return 200 when weekly report query succeeds")
    void should_return_200_when_weekly_report_succeeds() throws Exception {
      when(reportService.generateWeeklyReport(anyString())).thenReturn(new HashMap<>());

      mockMvc.perform(get("/workflow/advanced/report/weekly"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /workflow/advanced/mergeable")
  class Mergeable {

    @Test
    @DisplayName("should return 200 when mergeable instances query succeeds")
    void should_return_200_when_mergeable_succeeds() throws Exception {
      LoginUser loginUser = new LoginUser();
      loginUser.setUserId("user-001");
      AuthContextUtils.setCurrent(loginUser);

      when(mergeService.listMergeable(anyString(), anyString())).thenReturn(List.of());

      mockMvc.perform(get("/workflow/advanced/mergeable"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
