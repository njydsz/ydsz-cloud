package com.njydsz.agent.infra.insight;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.agent.domain.insight.InsightReport;
import com.njydsz.agent.domain.insight.InsightSection;
import com.njydsz.agent.domain.insight.ReportRenderer;

/**
 * HTML 格式洞察报告渲染器。
 *
 * <p>将 {@link InsightReport} 与其 {@link InsightSection} 列表合成为自包含的 HTML 字符串
 * （包含内联 CSS + ECharts CDN 引用），可直接在浏览器打开或进一步转 PDF。
 *
 * <p><b>设计约束</b>：
 * <ul>
 *   <li>不使用模板引擎（避免引入 Thymeleaf 等额外依赖），使用 Java StringBuilder 拼接</li>
 *   <li>HTML 为自包含：所有 CSS 内联，ECharts 通过 CDN 引用</li>
 *   <li>ECharts 图表基于 chapter.dataJson 动态生成</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.07
 */
@Slf4j
@Component
public class HtmlReportRenderer implements ReportRenderer {

  /** HTML 模板初始容量（字节估算） */
  private static final int HTML_INIT_CAPACITY = 2048;

  /** Markdown 风格标题 1 的字号 */
  private static final String H1_STYLE = "color:#1a1a2e; border-bottom: 2px solid #16213e; padding-bottom: 8px;";

  /** 图表容器 ID 前缀（避免章节间冲突） */
  private static final String CHART_PREFIX = "chart_";

  /**
   * 将报告 + 章节渲染为自包含 HTML 字符串。
   *
   * @param report 报告实体（含基础元数据）
   * @param sections 已排序的章节列表
   * @return HTML 字符串
   */
  public String render(InsightReport report, List<InsightSection> sections) {
    StringBuilder html = new StringBuilder(HTML_INIT_CAPACITY);

    html.append("<!DOCTYPE html>\n<html lang=\"zh-CN\"><head><meta charset=\"UTF-8\">");
    html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
    html.append("<title>").append(escapeHtml(report.getTitle())).append("</title>");
    html.append("<script src=\"https://cdn.jsdelivr.net/npm/echarts@5.5.0/dist/echarts.min.js\"></script>");
    html.append("<style>");
    appendInlineCss(html);
    html.append("</style></head><body>");

    // 报告标题
    html.append("<div class=\"report-header\">");
    html.append("<h1 style=\"").append(H1_STYLE).append("\">")
        .append(escapeHtml(report.getTitle())).append("</h1>");
    html.append("<div class=\"report-meta\">")
        .append("报告 ID: ").append(escapeHtml(report.getReportId()))
        .append(" | 生成时间: ").append(report.getCreatedAt())
        .append("</div></div>");

    // 各章节
    if (sections != null && !sections.isEmpty()) {
      for (int i = 0; i < sections.size(); i++) {
        appendSection(html, sections.get(i), i);
      }
    }

    html.append("</body></html>");
    return html.toString();
  }

  // ========================= 私有方法 =========================

  /**
   * 拼接章节 HTML。
   */
  private void appendSection(StringBuilder html, InsightSection section, int index) {
    html.append("<div class=\"section section-").append(section.sectionType()).append("\">");
    html.append("<h2>").append(escapeHtml(section.title())).append("</h2>");
    html.append("<div class=\"section-body\">");
    html.append(formatContent(section.content()));
    html.append("</div>");

    // chart 类型特别处理：渲染 div + JS
    if ("chart".equals(section.sectionType()) && !isEmpty(section.dataJson())
        && !section.dataJson().equals("{}")) {
      String chartId = CHART_PREFIX + index;
      html.append("<div id=\"").append(chartId).append("\" class=\"chart-container\"></div>");
      html.append("<script>");
      html.append("(function(){");
      html.append("var chart=echarts.init(document.getElementById('").append(chartId).append("'));");
      html.append("var data=").append(section.dataJson()).append(";");
      html.append(buildChartScript(chartId));
      html.append("})();");
      html.append("</script>");
    }

    html.append("</div>");
  }

  /**
   * 构建 ECharts 渲染脚本（基于 dataJson 数据）。
   */
  private String buildChartScript(String chartId) {
    return "if(data&&data.chartType&&data.labels){"
        + "var opt={};"
        + "if(data.chartType==='pie'){"
        + "opt.series=[{type:'pie',data:data.datasets}]"
        + "}else if(data.chartType==='bar'||data.chartType==='line'){"
        + "opt={xAxis:{type:'category',data:data.labels},"
        + "yAxis:{type:'value'},"
        + "series:(data.datasets||[]).map(function(d){return {type:data.chartType,label:{show:true},data:d.data}})}"
        + "}"
        + "chart.setOption(opt);"
        + "}";
  }

  /**
   * 基本文本格式化处理（换行符转 <br>）。
   */
  private String formatContent(String content) {
    if (content == null) {
      return "";
    }
    return escapeHtml(content).replace("\n", "<br>\n");
  }

  /**
   * HTML 转义（防 XSS）。
   */
  private String escapeHtml(String text) {
    if (text == null) {
      return "";
    }
    return text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }

  /**
   * 拼接内联 CSS 样式。
   */
  private void appendInlineCss(StringBuilder html) {
    html.append("""
        body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'Helvetica Neue',Arial,sans-serif;
        margin:0;padding:24px;background:#f8f9fa;color:#333;line-height:1.6;}
        .report-header{text-align:center;margin-bottom:32px;padding:24px;background:#fff;
        border-radius:8px;box-shadow:0 2px 8px rgba(0,0,0,0.06);}
        .report-meta{color:#666;font-size:13px;margin-top:8px;}
        .section{background:#fff;padding:24px;margin-bottom:16px;border-radius:8px;
        box-shadow:0 2px 8px rgba(0,0,0,0.06);}
        .section h2{color:#16213e;border-left:4px solid #0f3460;padding-left:12px;}
        .chart-container{width:100%;height:400px;margin-top:16px;}
        .section-chart .chart-container{border:1px solid #e9ecef;border-radius:6px;}
        .section-summary{background:linear-gradient(135deg,#667eea 0%,#764ba2 100%);color:#fff;}
        .section-summary h2{color:#fff;border-left-color:#fff;}
        """);
  }

  /**
   * 检查字符串是否为空白或空。
   */
  private boolean isEmpty(String text) {
    return text == null || text.isBlank();
  }
}
