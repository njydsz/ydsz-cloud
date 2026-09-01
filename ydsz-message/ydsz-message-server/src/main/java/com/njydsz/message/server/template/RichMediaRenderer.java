package com.njydsz.message.server.template;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.njydsz.common.json.YdszJson;
import com.njydsz.message.domain.dto.RichMediaContentDTO;

/**
 * P1-2: 富媒体消息渲染器。
 *
 * <p>将 {@link RichMediaContentDTO} 渲染为通道特定的格式：
 *
 * <ul>
 *   <li>EMAIL: 渲染为 HTML 邮件正文（含内联图片、附件链接、操作按钮）
 *   <li>IN_APP: 渲染为 Markdown 正文（站内信支持富文本展示）
 *   <li>PUSH: 提取标题+摘要，附带图片URL（推送卡片）
 *   <li>SMS: 降级为纯文本摘要
 *   <li>DINGTALK/WECOM/FEISHU: 渲染为 Markdown 卡片消息
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
public class RichMediaRenderer {

  /** MessageRequest.params 中存储富媒体内容的 key */
  public static final String RICH_MEDIA_KEY = "_richMedia";

  /**
   * 从模板参数中提取富媒体内容。
   *
   * @param params 模板参数
   * @return 富媒体内容；无则返回 null
   */
  public RichMediaContentDTO extractFromParams(Map<String, Object> params) {
    if (params == null || params.isEmpty()) {
      return null;
    }
    Object raw = params.get(RICH_MEDIA_KEY);
    if (raw == null) {
      return null;
    }
    try {
      if (raw instanceof RichMediaContentDTO) {
        return (RichMediaContentDTO) raw;
      }
      String json = raw instanceof String ? (String) raw : YdszJson.toJson(raw);
      return YdszJson.fromJson(json, RichMediaContentDTO.class);
    } catch (Exception e) {
      log.warn("[RichMediaRenderer] 解析富媒体内容失败: {}", e.getMessage(), e);
      return null;
    }
  }

  /**
   * 渲染为 HTML 邮件正文。
   *
   * @param media 富媒体内容对象（含标题/正文/图片/附件/按钮等素材）
   * @return HTML 格式的邮件正文；若 media 为 null 则返回 null
   */
  public String renderHtml(RichMediaContentDTO media) {
    if (media == null) {
      return null;
    }
    StringBuilder html = new StringBuilder();
    html.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head><body>");
    html.append("<div style=\"max-width:640px;margin:0 auto;font-family:sans-serif;\">");

    // 标题
    if (StringUtils.hasText(media.getTitle())) {
      html.append("<h2 style=\"color:#333;\">").append(media.getTitle()).append("</h2>");
    }

    // 正文
    if (StringUtils.hasText(media.getHtmlContent())) {
      html.append(media.getHtmlContent());
    } else if (StringUtils.hasText(media.getMarkdownContent())) {
      // 简易 Markdown 转 HTML（仅支持基本格式）
      html.append("<div>").append(markdownToHtml(media.getMarkdownContent())).append("</div>");
    } else if (StringUtils.hasText(media.getSummary())) {
      html.append("<p>").append(media.getSummary()).append("</p>");
    }

    // 图片
    if (media.getImages() != null && !media.getImages().isEmpty()) {
      html.append("<div style=\"margin:16px 0;\">");
      for (String imgUrl : media.getImages()) {
        html.append("<img src=\"")
            .append(imgUrl)
            .append("\" style=\"max-width:100%;border-radius:8px;margin:8px 0;\" />");
      }
      html.append("</div>");
    }

    // 附件
    if (media.getAttachments() != null && !media.getAttachments().isEmpty()) {
      html.append(
          "<div style=\"margin:16px 0;\"><p style=\"color:#666;font-size:14px;\">附件：</p><ul>");
      for (RichMediaContentDTO.Attachment att : media.getAttachments()) {
        html.append("<li><a href=\"")
            .append(att.getUrl())
            .append("\" style=\"color:#1890ff;\">")
            .append(att.getFilename())
            .append("</a></li>");
      }
      html.append("</ul></div>");
    }

    // 操作按钮
    if (media.getButtons() != null && !media.getButtons().isEmpty()) {
      html.append("<div style=\"margin:24px 0;text-align:center;\">");
      for (RichMediaContentDTO.ActionButton btn : media.getButtons()) {
        if ("OPEN_URL".equals(btn.getActionType())) {
          html.append("<a href=\"")
              .append(btn.getActionValue())
              .append("\" style=\"display:inline-block;padding:10px 24px;margin:0 8px;")
              .append("background:#1890ff;color:#fff;text-decoration:none;border-radius:4px;\">")
              .append(btn.getText())
              .append("</a>");
        }
      }
      html.append("</div>");
    }

    html.append("</div></body></html>");
    return html.toString();
  }

  /**
   * 渲染为 Markdown 格式（站内信/IM 通道使用）。
   *
   * @param media 富媒体内容对象
   * @return Markdown 格式的字符串；若 media 为 null 则返回 null
   */
  public String renderMarkdown(RichMediaContentDTO media) {
    if (media == null) {
      return null;
    }
    StringBuilder md = new StringBuilder();
    if (StringUtils.hasText(media.getTitle())) {
      md.append("## ").append(media.getTitle()).append("\n\n");
    }
    if (StringUtils.hasText(media.getMarkdownContent())) {
      md.append(media.getMarkdownContent()).append("\n\n");
    } else if (StringUtils.hasText(media.getSummary())) {
      md.append(media.getSummary()).append("\n\n");
    }
    if (media.getImages() != null && !media.getImages().isEmpty()) {
      for (String imgUrl : media.getImages()) {
        md.append("![image](").append(imgUrl).append(")\n");
      }
      md.append("\n");
    }
    if (media.getAttachments() != null && !media.getAttachments().isEmpty()) {
      md.append("**附件：**\n");
      for (RichMediaContentDTO.Attachment att : media.getAttachments()) {
        md.append("- [").append(att.getFilename()).append("](").append(att.getUrl()).append(")\n");
      }
      md.append("\n");
    }
    if (media.getButtons() != null && !media.getButtons().isEmpty()) {
      for (RichMediaContentDTO.ActionButton btn : media.getButtons()) {
        if ("OPEN_URL".equals(btn.getActionType())) {
          md.append("[")
              .append(btn.getText())
              .append("](")
              .append(btn.getActionValue())
              .append(")  ");
        }
      }
      md.append("\n");
    }
    return md.toString();
  }

  /**
   * 渲染为纯文本摘要（SMS 通道降级使用）。
   *
   * @param media 富媒体内容对象
   * @return 纯文本摘要字符串；若 media 为 null 则返回 null
   */
  public String renderPlainText(RichMediaContentDTO media) {
    if (media == null) {
      return null;
    }
    StringBuilder text = new StringBuilder();
    if (StringUtils.hasText(media.getTitle())) {
      text.append(media.getTitle()).append("：");
    }
    if (StringUtils.hasText(media.getSummary())) {
      text.append(media.getSummary());
    } else if (StringUtils.hasText(media.getMarkdownContent())) {
      // 去除 Markdown 标记
      text.append(media.getMarkdownContent().replaceAll("[#*`\\[\\]()]", ""));
    }
    if (StringUtils.hasText(media.getActionUrl())) {
      text.append(" 详情请访问：").append(media.getActionUrl());
    }
    return text.toString();
  }

  /**
   * 简易 Markdown 转 HTML（仅处理标题、粗体、链接、列表等基本格式）。
   *
   * @param md Markdown 原始文本
   * @return 转换后的 HTML 字符串；若 md 为 null 则返回空字符串
   */
  private String markdownToHtml(String md) {
    if (md == null) {
      return "";
    }
    return md.replaceAll("(?m)^### (.+)$", "<h3>$1</h3>")
        .replaceAll("(?m)^## (.+)$", "<h2>$1</h2>")
        .replaceAll("(?m)^# (.+)$", "<h1>$1</h1>")
        .replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>")
        .replaceAll("\\[([^]]+)]\\(([^)]+)\\)", "<a href=\"$2\">$1</a>")
        .replaceAll("(?m)^- (.+)$", "<li>$1</li>")
        .replaceAll("\n", "<br/>");
  }
}
