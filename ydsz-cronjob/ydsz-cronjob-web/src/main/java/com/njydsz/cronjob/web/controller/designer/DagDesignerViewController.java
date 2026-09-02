package com.njydsz.cronjob.web.controller.designer;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * DAG 设计器页面视图 Controller（P2-2）。
 *
 * <p>返回 DAG 可视化设计器静态页面。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Controller
public class DagDesignerViewController {

  /** DAG 设计器页面路径 */
  private static final String DAG_DESIGNER_PAGE = "forward:/static/dag-designer/index.html";

  /**
   * 返回 DAG 设计器页面。
   *
   * @return 页面转发路径
   */
  @GetMapping("/cronjob/dag-designer.html")
  public String index() {
    return DAG_DESIGNER_PAGE;
  }
}
