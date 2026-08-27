package com.njydsz.cronjob.web.controller.dashboard;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Dashboard 页面视图 Controller（P2-6）。
 *
 * <p>返回运维 Dashboard 静态页面，使用 ECharts 按需引入方式渲染图表。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Controller
public class DashboardViewController {

  /** Dashboard 页面路径 */
  private static final String DASHBOARD_PAGE = "forward:/static/dashboard/index.html";

  /**
   * 返回 Dashboard 页面。
   *
   * @return 页面转发路径
   */
  @GetMapping("/cronjob/dashboard.html")
  public String index() {
    return DASHBOARD_PAGE;
  }
}
