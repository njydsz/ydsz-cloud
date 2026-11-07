package com.njydsz.workflow.web.controller;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.auth.model.LoginUser;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.workflow.domain.vo.FlowApproverEfficiencyVO;
import com.njydsz.workflow.domain.vo.FlowBottleneckVO;
import com.njydsz.workflow.domain.vo.FlowEfficiencyStatsVO;
import com.njydsz.workflow.domain.vo.FlowTrendVO;
import com.njydsz.workflow.server.service.FlowEfficiencyService;
import com.njydsz.workflow.web.controller.FlowEfficiencyController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link FlowEfficiencyController} HTTP 契约测试。
 *
 * <p>覆盖 5 个效率分析端点（{@code /workflow/engine/efficiency/*}）：审批效率统计、节点瓶颈排名、审批人效率排名、审批趋势、
 * 流程健康度综合评分。
 *
 * <p>每个端点验证：真实 HTTP 路径与方法、请求参数绑定、响应字段序列化、服务调用入参（含租户上下文透传）与返回值；并对越界
 * {@code limit} 参数做校验负例断言。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
class FlowEfficiencyControllerTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @Mock
  private FlowEfficiencyService efficiencyService;

  @InjectMocks
  private FlowEfficiencyController controller;

  @BeforeEach
  void setUp() {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    mockMvc = MockMvcBuilders.standaloneSetup(controller).setValidator(validator).build();
    LoginUser loginUser = new LoginUser();
    loginUser.setUserId("user-eff-001");
    loginUser.setUsername("效率测试员");
    loginUser.setTenantId("tenant-eff");
    AuthContextUtils.setCurrent(loginUser);
  }

  @org.junit.jupiter.api.AfterEach
  void tearDown() {
    RequestContext.clear();
  }

  @Nested
  @DisplayName("GET /workflow/engine/efficiency/stats")
  class EfficiencyStats {

    @Test
    @DisplayName("绑定时间区间并返回统计字段，租户透传服务")
    void should_bind_time_range_and_return_stats() throws Exception {
      FlowEfficiencyStatsVO stats = new FlowEfficiencyStatsVO();
      stats.setTotalCount(128L);
      stats.setAvgDurationMs(360000L);
      stats.setProxyRate(0.15);
      stats.setOverdueRate(0.08);
      when(efficiencyService.efficiencyStats("tenant-eff", "2026-01-01 00:00:00", "2026-01-31 23:59:59"))
          .thenReturn(stats);

      mockMvc.perform(get("/workflow/engine/efficiency/stats")
              .param("startTime", "2026-01-01 00:00:00")
              .param("endTime", "2026-01-31 23:59:59"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.totalCount").value(128))
          .andExpect(jsonPath("$.data.avgDurationMs").value(360000))
          .andExpect(jsonPath("$.data.proxyRate").value(0.15))
          .andExpect(jsonPath("$.data.overdueRate").value(0.08));

      verify(efficiencyService).efficiencyStats("tenant-eff", "2026-01-01 00:00:00", "2026-01-31 23:59:59");
    }

    @Test
    @DisplayName("时间参数缺省时以 null 透传服务")
    void should_pass_null_when_time_params_absent() throws Exception {
      FlowEfficiencyStatsVO stats = new FlowEfficiencyStatsVO();
      when(efficiencyService.efficiencyStats("tenant-eff", null, null)).thenReturn(stats);

      mockMvc.perform(get("/workflow/engine/efficiency/stats"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(efficiencyService).efficiencyStats("tenant-eff", null, null);
    }
  }

  @Nested
  @DisplayName("GET /workflow/engine/efficiency/bottleneck")
  class BottleneckRanking {

    @Test
    @DisplayName("绑定 flowCode 与 limit，返回瓶颈节点列表")
    void should_bind_flow_code_and_limit() throws Exception {
      FlowBottleneckVO vo = new FlowBottleneckVO();
      vo.setNodeCode("NODE_DEPT_AUDIT");
      vo.setNodeName("部门审批");
      vo.setAvgDurationMs(7200000L);
      vo.setCount(42L);
      when(efficiencyService.bottleneckRanking("tenant-eff", "LEAVE_APPLY", 5)).thenReturn(List.of(vo));

      mockMvc.perform(get("/workflow/engine/efficiency/bottleneck")
              .param("flowCode", "LEAVE_APPLY")
              .param("limit", "5"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data[0].nodeCode").value("NODE_DEPT_AUDIT"))
          .andExpect(jsonPath("$.data[0].nodeName").value("部门审批"))
          .andExpect(jsonPath("$.data[0].avgDurationMs").value(7200000))
          .andExpect(jsonPath("$.data[0].count").value(42));

      verify(efficiencyService).bottleneckRanking("tenant-eff", "LEAVE_APPLY", 5);
    }

    @Test
    @DisplayName("limit 缺省为 10")
    void should_default_limit_to_10() throws Exception {
      when(efficiencyService.bottleneckRanking(eq("tenant-eff"), eq(null), eq(10))).thenReturn(List.of());

      mockMvc.perform(get("/workflow/engine/efficiency/bottleneck"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(efficiencyService).bottleneckRanking("tenant-eff", null, 10);
    }

    @Test
    @DisplayName("limit 超过 100 触发参数校验负例（400）")
    void should_reject_limit_over_max() throws Exception {
      mockMvc.perform(get("/workflow/engine/efficiency/bottleneck").param("limit", "101"))
          .andExpect(status().isBadRequest());

      org.mockito.Mockito.verifyNoInteractions(efficiencyService);
    }

    @Test
    @DisplayName("limit 小于 1 触发参数校验负例（400）")
    void should_reject_limit_below_min() throws Exception {
      mockMvc.perform(get("/workflow/engine/efficiency/bottleneck").param("limit", "0"))
          .andExpect(status().isBadRequest());
    }
  }

  @Nested
  @DisplayName("GET /workflow/engine/efficiency/approverRanking")
  class ApproverRanking {

    @Test
    @DisplayName("绑定时间区间与 limit，返回审批人排名列表")
    void should_bind_range_and_limit() throws Exception {
      FlowApproverEfficiencyVO vo = new FlowApproverEfficiencyVO();
      vo.setUserId("user-1001");
      vo.setUserName("张三");
      vo.setCompletedCount(64L);
      vo.setAvgDurationMs(180000L);
      vo.setTotalDurationMs(11520000L);
      when(efficiencyService.approverRanking("tenant-eff", "2026-02-01 00:00:00", "2026-02-28 23:59:59", 20))
          .thenReturn(List.of(vo));

      mockMvc.perform(get("/workflow/engine/efficiency/approverRanking")
              .param("startTime", "2026-02-01 00:00:00")
              .param("endTime", "2026-02-28 23:59:59")
              .param("limit", "20"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data[0].userId").value("user-1001"))
          .andExpect(jsonPath("$.data[0].userName").value("张三"))
          .andExpect(jsonPath("$.data[0].completedCount").value(64))
          .andExpect(jsonPath("$.data[0].avgDurationMs").value(180000))
          .andExpect(jsonPath("$.data[0].totalDurationMs").value(11520000));

      verify(efficiencyService).approverRanking("tenant-eff", "2026-02-01 00:00:00", "2026-02-28 23:59:59", 20);
    }

    @Test
    @DisplayName("时间区间缺省时以 null 透传，limit 缺省 10")
    void should_pass_null_range_when_absent() throws Exception {
      when(efficiencyService.approverRanking("tenant-eff", null, null, 10)).thenReturn(List.of());

      mockMvc.perform(get("/workflow/engine/efficiency/approverRanking"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(efficiencyService).approverRanking("tenant-eff", null, null, 10);
    }

    @Test
    @DisplayName("limit 非数字触发类型不匹配负例（400）")
    void should_reject_non_numeric_limit() throws Exception {
      mockMvc.perform(get("/workflow/engine/efficiency/approverRanking").param("limit", "abc"))
          .andExpect(status().isBadRequest());
    }
  }

  @Nested
  @DisplayName("GET /workflow/engine/efficiency/trend")
  class ApprovalTrend {

    @Test
    @DisplayName("绑定 interval 与时间区间，返回趋势列表")
    void should_bind_interval_and_range() throws Exception {
      FlowTrendVO vo = new FlowTrendVO();
      vo.setTimeLabel("2026-W03");
      vo.setCount(30L);
      vo.setAvgDurationMs(540000L);
      when(efficiencyService.approvalTrend("tenant-eff", "WEEK", "2026-01-01 00:00:00", "2026-01-31 23:59:59"))
          .thenReturn(List.of(vo));

      mockMvc.perform(get("/workflow/engine/efficiency/trend")
              .param("interval", "WEEK")
              .param("startTime", "2026-01-01 00:00:00")
              .param("endTime", "2026-01-31 23:59:59"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data[0].timeLabel").value("2026-W03"))
          .andExpect(jsonPath("$.data[0].count").value(30))
          .andExpect(jsonPath("$.data[0].avgDurationMs").value(540000));

      verify(efficiencyService).approvalTrend("tenant-eff", "WEEK", "2026-01-01 00:00:00", "2026-01-31 23:59:59");
    }

    @Test
    @DisplayName("interval 缺省为 DAY")
    void should_default_interval_to_day() throws Exception {
      when(efficiencyService.approvalTrend("tenant-eff", "DAY", null, null)).thenReturn(List.of());

      mockMvc.perform(get("/workflow/engine/efficiency/trend"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(efficiencyService).approvalTrend("tenant-eff", "DAY", null, null);
    }
  }

  @Nested
  @DisplayName("GET /workflow/engine/efficiency/healthScore")
  class HealthScore {

    @Test
    @DisplayName("返回 Map 结构评分结果并透传租户")
    void should_return_health_score_map() throws Exception {
      Map<String, Object> score = Map.of("score", 88, "level", "GOOD");
      when(efficiencyService.healthScore("tenant-eff", null, null)).thenReturn(score);

      mockMvc.perform(get("/workflow/engine/efficiency/healthScore"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.score").value(88))
          .andExpect(jsonPath("$.data.level").value("GOOD"));

      ArgumentCaptor<String> tenantCaptor = ArgumentCaptor.forClass(String.class);
      verify(efficiencyService).healthScore(tenantCaptor.capture(),
          org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull());
      assertThat(tenantCaptor.getValue()).isEqualTo("tenant-eff");
    }
  }
}
