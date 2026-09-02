package com.njydsz.cronjob.web.controller.glue;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * GLUE 编辑器页面视图 Controller（P2-1）。
 *
 * <p>返回 GLUE 代码编辑器静态页面。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Controller
public class GlueEditorViewController {

  /** GLUE 编辑器页面路径 */
  private static final String GLUE_EDITOR_PAGE = "forward:/static/glue-editor/index.html";

  /**
   * 返回 GLUE 编辑器页面。
   *
   * @return 页面转发路径
   */
  @GetMapping("/cronjob/glue-editor.html")
  public String index() {
    return GLUE_EDITOR_PAGE;
  }
}
