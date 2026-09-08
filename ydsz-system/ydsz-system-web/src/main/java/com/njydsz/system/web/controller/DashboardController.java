package com.njydsz.system.web.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.system.domain.vo.DashboardOverviewItemVO;
import com.njydsz.system.domain.vo.DashboardWorkspaceVO;
import com.njydsz.system.server.service.DashboardService;

/**
 * 工作台聚合 Controller
 *
 * <p>为主框架工作台（analytics/workspace 页面）提供可真实计算的聚合数据端点，
 * 前端 {@code useOverviewStats / useWorkspaceData} 已内置「后端不可用/空数据回退本地默认值」
 * 的容错逻辑，本端点就绪后自动切换为真实数据。
 *
 * <p><b>接口路径：</b>{@code /api/dashboard}
 *
 * <p><b>数据口径：</b>
 *
 * <ul>
 *   <li>概览统计项：租户/字典类型/系统变量/注册应用的真实规模计数（不做估算）
 *   <li>工作台：时段化问候语；项目/待办/动态列表待各业务模块沉淀聚合能力后逐字段接入
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.08
 * @see com.njydsz.system.server.service.DashboardService 聚合业务逻辑
 */
@Slf4j
@ApiVersion("26.09.01")
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "工作台聚合", description = "主框架工作台概览统计与聚合数据")
public class DashboardController {

  private final DashboardService dashboardService;

  /**
   * 查询概览统计项。
   *
   * <p>限流 50 QPS：工作台首屏调用，内部为各域缓存读取。
   *
   * @return 概览统计项列表
   */
  @RateLimit(resource = "system.Dashboard.overview", threshold = 50)
  @GetMapping("/overview")
  @Operation(summary = "查询概览统计项", description = "租户/字典类型/系统变量/注册应用规模统计")
  public YdszResponse<List<DashboardOverviewItemVO>> overview() {
    return YdszResponse.success(dashboardService.overview());
  }

  /**
   * 查询工作台聚合数据。
   *
   * <p>当前版本返回时段化问候语；列表字段由前端按字段粒度回退本地默认值。
   *
   * @return 工作台聚合 VO
   */
  @RateLimit(resource = "system.Dashboard.workspace", threshold = 50)
  @GetMapping("/workspace")
  @Operation(summary = "查询工作台聚合数据", description = "时段化问候语，列表字段由前端回退默认值")
  public YdszResponse<DashboardWorkspaceVO> workspace() {
    return YdszResponse.success(dashboardService.workspace());
  }
}
