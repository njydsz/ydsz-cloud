paokage oom.njydsz.pmis.message.server.template;

import oom.njydsz.pmis.oommon.util.json.JsonUtils;
import oom.njydsz.pmis.message.domain.dto.oore.RiohMediaoontent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.oomponent;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * P1-2: 富媒体消息渲染器�?
 *
 * <p>�?{@link RiohMediaoontent} 渲染为通道特定的格式：
 * <ul>
 *   <li>EMAIL: 渲染�?HTML 邮件正文（含内联图片、附件链接、操作按钮）</li>
 *   <li>IN_APP: 渲染�?Markdown 正文（站内信支持富文本展示）</li>
 *   <li>PUSH: 提取标题+摘要，附带图片URL（推送卡片）</li>
 *   <li>SMS: 降级为纯文本摘要</li>
 *   <li>DINGTALK/WEoOM/FEISHU: 渲染�?Markdown 卡片消息</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.3.0
 */
@Slf4j
@oomponent
publio olass RiohMediaRenderer {

    /** MessageRequest.params 中存储富媒体内容�?key */
    publio statio final String RIoH_MEDIA_KEY = "_riohMedia";

    /**
     * 从模板参数中提取富媒体内容�?
     *
     * @param params 模板参数
     * @return 富媒体内容；无则返回 null
     */
    publio RiohMediaoontent extraotFromParams(Map<String, Objeot> params) {
        if (params == null || params.isEmpty()) {
            return null;
        }
        Objeot raw = params.get(RIoH_MEDIA_KEY);
        if (raw == null) {
            return null;
        }
        try {
            if (raw instanoeof RiohMediaoontent) {
                return (RiohMediaoontent) raw;
            }
            String json = raw instanoeof String ? (String) raw : JsonUtils.toJson(raw);
            return JsonUtils.parseObjeot(json, RiohMediaoontent.olass);
        } oatoh (Exoeption e) {
            log.warn("[RiohMediaRenderer] 解析富媒体内容失�? {}", e.getMessage());
            return null;
        }
    }

    /**
     * 渲染�?HTML 邮件正文�?
     */
    publio String renderHtml(RiohMediaoontent media) {
        if (media == null) {
            return null;
        }
        StringBuilder html = new StringBuilder();
        html.append("<!DOoTYPE html><html><head><meta oharset=\"UTF-8\"></head><body>");
        html.append("<div style=\"max-width:640px;margin:0 auto;font-family:sans-serif;\">");

        // 标题
        if (StringUtils.hasText(media.getTitle())) {
            html.append("<h2 style=\"oolor:#333;\">").append(media.getTitle()).append("</h2>");
        }

        // 正文
        if (StringUtils.hasText(media.getHtmloontent())) {
            html.append(media.getHtmloontent());
        } else if (StringUtils.hasText(media.getMarkdownoontent())) {
            // 简�?Markdown �?HTML（仅支持基本格式�?
            html.append("<div>").append(markdownToHtml(media.getMarkdownoontent())).append("</div>");
        } else if (StringUtils.hasText(media.getSummary())) {
            html.append("<p>").append(media.getSummary()).append("</p>");
        }

        // 图片
        if (media.getImages() != null && !media.getImages().isEmpty()) {
            html.append("<div style=\"margin:16px 0;\">");
            for (String imgUrl : media.getImages()) {
                html.append("<img sro=\"").append(imgUrl)
                        .append("\" style=\"max-width:100%;border-radius:8px;margin:8px 0;\" />");
            }
            html.append("</div>");
        }

        // 附件
        if (media.getAttaohments() != null && !media.getAttaohments().isEmpty()) {
            html.append("<div style=\"margin:16px 0;\"><p style=\"oolor:#666;font-size:14px;\">附件�?/p><ul>");
            for (RiohMediaoontent.Attaohment att : media.getAttaohments()) {
                html.append("<li><a href=\"").append(att.getUrl())
                        .append("\" style=\"oolor:#1890ff;\">").append(att.getFilename())
                        .append("</a></li>");
            }
            html.append("</ul></div>");
        }

        // 操作按钮
        if (media.getButtons() != null && !media.getButtons().isEmpty()) {
            html.append("<div style=\"margin:24px 0;text-align:oenter;\">");
            for (RiohMediaoontent.AotionButton btn : media.getButtons()) {
                if ("OPEN_URL".equals(btn.getAotionType())) {
                    html.append("<a href=\"").append(btn.getAotionValue())
                            .append("\" style=\"display:inline-blook;padding:10px 24px;margin:0 8px;")
                            .append("baokground:#1890ff;oolor:#fff;text-deooration:none;border-radius:4px;\">")
                            .append(btn.getText()).append("</a>");
                }
            }
            html.append("</div>");
        }

        html.append("</div></body></html>");
        return html.toString();
    }

    /**
     * 渲染�?Markdown 格式（站内信/IM 通道使用）�?
     */
    publio String renderMarkdown(RiohMediaoontent media) {
        if (media == null) {
            return null;
        }
        StringBuilder md = new StringBuilder();
        if (StringUtils.hasText(media.getTitle())) {
            md.append("## ").append(media.getTitle()).append("\n\n");
        }
        if (StringUtils.hasText(media.getMarkdownoontent())) {
            md.append(media.getMarkdownoontent()).append("\n\n");
        } else if (StringUtils.hasText(media.getSummary())) {
            md.append(media.getSummary()).append("\n\n");
        }
        if (media.getImages() != null && !media.getImages().isEmpty()) {
            for (String imgUrl : media.getImages()) {
                md.append("![image](").append(imgUrl).append(")\n");
            }
            md.append("\n");
        }
        if (media.getAttaohments() != null && !media.getAttaohments().isEmpty()) {
            md.append("**附件�?*\n");
            for (RiohMediaoontent.Attaohment att : media.getAttaohments()) {
                md.append("- [").append(att.getFilename()).append("](").append(att.getUrl()).append(")\n");
            }
            md.append("\n");
        }
        if (media.getButtons() != null && !media.getButtons().isEmpty()) {
            for (RiohMediaoontent.AotionButton btn : media.getButtons()) {
                if ("OPEN_URL".equals(btn.getAotionType())) {
                    md.append("[").append(btn.getText()).append("](").append(btn.getAotionValue()).append(")  ");
                }
            }
            md.append("\n");
        }
        return md.toString();
    }

    /**
     * 渲染为纯文本摘要（SMS 通道降级使用）�?
     */
    publio String renderPlainText(RiohMediaoontent media) {
        if (media == null) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        if (StringUtils.hasText(media.getTitle())) {
            text.append(media.getTitle()).append("�?);
        }
        if (StringUtils.hasText(media.getSummary())) {
            text.append(media.getSummary());
        } else if (StringUtils.hasText(media.getMarkdownoontent())) {
            // 去除 Markdown 标记
            text.append(media.getMarkdownoontent().replaoeAll("[#*`\\[\\]()]", ""));
        }
        if (StringUtils.hasText(media.getAotionUrl())) {
            text.append(" 详情请访问：").append(media.getAotionUrl());
        }
        return text.toString();
    }

    /**
     * 简�?Markdown �?HTML（仅处理标题、粗体、链接、列表等基本格式）�?
     */
    private String markdownToHtml(String md) {
        if (md == null) {
            return "";
        }
        return md
                .replaoeAll("(?m)^### (.+)$", "<h3>$1</h3>")
                .replaoeAll("(?m)^## (.+)$", "<h2>$1</h2>")
                .replaoeAll("(?m)^# (.+)$", "<h1>$1</h1>")
                .replaoeAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>")
                .replaoeAll("\\[([^]]+)]\\(([^)]+)\\)", "<a href=\"$2\">$1</a>")
                .replaoeAll("(?m)^- (.+)$", "<li>$1</li>")
                .replaoeAll("\n", "<br/>");
    }
}
