package com.njydsz.cronjob.web.controller.topology;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 任务全局拓扑页面视图 Controller（P2-3）。
 *
 * <p>返回任务全局拓扑可视化静态页面，展示所有任务节点及其依赖关系。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Controller
public class TopologyViewController {

  /** 全局拓扑页面路径 */
  private static final String TOPOLOGY_PAGE = "forward:/static/topology/index.html";

  /**
   * 返回任务全局拓扑页面。
   *
   * @return 页面转发路径
   */
  @GetMapping("/cronjob/topology.html")
  public String index() {
    return TOPOLOGY_PAGE;
  }
}
