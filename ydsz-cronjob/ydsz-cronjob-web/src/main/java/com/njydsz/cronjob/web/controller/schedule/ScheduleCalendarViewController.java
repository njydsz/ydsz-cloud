package com.njydsz.cronjob.web.controller.schedule;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 调度日历视图 Controller（P1-15：调度日历前端视图）。
 *
 * <p>返回调度日历静态页面，基于 Vue 3 + Element Plus 实现日历可视化。
 *
 * <h3>访问路径</h3>
 *
 * <ul>
 *   <li>{@code /cronjob/calendar.html} - 调度日历页面</li>
 * </ul>
 *
 * <h3>数据源</h3>
 *
 * <p>前端通过 REST API {@code /api/v1/cronjob/calendar/schedule} 获取调度数据，
 * 后端由 {@link ScheduleCalendarController} 提供。
 *
 * @author ydsz-team
 * @since 1.0.4
 */
@Hidden
@Controller
@RequestMapping("/cronjob")
public class ScheduleCalendarViewController {

  /** 调度日历页面路径 */
  private static final String CALENDAR_PAGE = "redirect:/calendar/index.html";

  /**
   * 调度日历视图入口。
   *
   * @return 重定向到日历静态页面
   */
  @GetMapping("/calendar.html")
  public String calendar() {
    return CALENDAR_PAGE;
  }
}
