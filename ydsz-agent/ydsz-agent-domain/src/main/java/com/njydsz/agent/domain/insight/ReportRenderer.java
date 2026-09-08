package com.njydsz.agent.domain.insight;

import java.util.List;

/**
 * 报告渲染器接口（领域网关）。
 *
 * <p>定义将报告实体与章节列表合成为最终输出格式（HTML/Markdown 等）的领域契约。
 * 实现位于 infra 层，可基于模板引擎、StringBuilder 拼接或第三方库。
 *
 * @author ydsz-team
 * @since 26.09.07
 */
public interface ReportRenderer {

  /**
   * 将报告元数据与章节列表渲染为字符串输出。
   *
   * @param report 报告实体（含标题、ID、创建时间等元数据）
   * @param sections 已排序的章节列表
   * @return 渲染后的字符串（HTML/Markdown 等，由实现类决定）
   */
  String render(InsightReport report, List<InsightSection> sections);
}
