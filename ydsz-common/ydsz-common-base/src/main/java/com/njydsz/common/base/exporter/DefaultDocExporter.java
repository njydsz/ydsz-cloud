package com.njydsz.common.base.exporter;

import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;

import com.njydsz.common.base.config.DocProperties;

/**
 * 默认文档导出器实现
 *
 * <p>基于 OpenAPI JSON 规范文档，支持导出为 HTML、Markdown、YAML、JSON 四种格式。
 * HTML 模板采用内联 CSS，Markdown 导出为简单格式（原始 JSON）。
 * 如需结构化 Markdown 导出，请使用 {@link MarkdownDocExporter}。
 *
 * <p><b>激活条件：</b>同时满足以下两个条件时本 Bean 才会被注册：
 * <ul>
 *   <li>{@code ydsz.doc.export.enabled=true}（默认 true）</li>
 *   <li>{@code ydsz.doc.exporter=default}（默认 default）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Conditional(DefaultDocExporter.DefaultDocExporterCondition.class)
public class DefaultDocExporter extends AbstractDocExporter {

    /**
     * 构造默认文档导出器
     *
     * @param docProperties 文档配置属性
     */
    public DefaultDocExporter(DocProperties docProperties) {
        super(docProperties);
    }

    @Override
    protected String generateContent(String apiDocs, String format) {
        return switch (format) {
            case "html" -> generateHtml(apiDocs);
            case "markdown", "md" -> generateMarkdown(apiDocs);
            default -> throw new IllegalArgumentException("不支持的导出格式: " + format);
        };
    }

    /**
     * 生成 HTML 内容
     */
    private String generateHtml(String apiDocs) {
        ApiDocInfo docInfo = parseApiDocInfo(apiDocs);
        // 使用 StringBuilder 拼接，避免 String.format 遇到 JSON 中的 % 字符抛出 IllegalFormatException
        String escapedTitle = escapeHtml(docInfo.title());
        String escapedVersion = escapeHtml(docInfo.version());
        String escapedDescription = escapeHtml(docInfo.description());
        String escapedApiDocs = escapeHtml(apiDocs);

        StringBuilder html = new StringBuilder(4096);
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"zh-CN\">\n");
        html.append("<head>\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("    <title>").append(escapedTitle).append("</title>\n");
        html.append("    <style>\n");
        html.append("        body { font-family: Arial, sans-serif; margin: 20px; background-color: #f5f5f5; }\n");
        html.append("        .container { max-width: 1200px; margin: 0 auto; background-color: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }\n");
        html.append("        h1 { color: #333; border-bottom: 2px solid #007bff; padding-bottom: 10px; }\n");
        html.append("        pre { background-color: #f8f9fa; padding: 15px; border-radius: 4px; overflow-x: auto; }\n");
        html.append("        code { font-family: 'Courier New', monospace; }\n");
        html.append("        table { border-collapse: collapse; width: 100%; margin: 15px 0; }\n");
        html.append("        th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }\n");
        html.append("        th { background-color: #007bff; color: white; }\n");
        html.append("        tr:nth-child(even) { background-color: #f2f2f2; }\n");
        html.append("    </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("    <div class=\"container\">\n");
        html.append("        <h1>").append(escapedTitle).append("</h1>\n");
        html.append("        <p><strong>版本:</strong> ").append(escapedVersion).append("</p>\n");
        html.append("        <p><strong>描述:</strong> ").append(escapedDescription).append("</p>\n");
        html.append("        <pre><code>").append(escapedApiDocs).append("</code></pre>\n");
        html.append("    </div>\n");
        html.append("</body>\n");
        html.append("</html>");
        return html.toString();
    }

    /**
     * 生成 Markdown 内容
     */
    private String generateMarkdown(String apiDocs) {
        ApiDocInfo docInfo = parseApiDocInfo(apiDocs);
        StringBuilder md = new StringBuilder();
        md.append("# ").append(docInfo.title()).append("\n\n");
        md.append("**版本:** ").append(docInfo.version()).append("\n\n");
        md.append("**描述:** ").append(docInfo.description()).append("\n\n");
        md.append("## API 规范\n\n");
        md.append("```json\n").append(apiDocs).append("\n```\n");
        return md.toString();
    }

    /**
     * 默认文档导出器激活条件
     *
     * <p>同时满足以下两个条件：
     * <ul>
     *   <li>{@code ydsz.doc.export.enabled=true}（默认 true）</li>
     *   <li>{@code ydsz.doc.exporter=default}（默认 default）</li>
     * </ul>
     */
    static class DefaultDocExporterCondition extends AllNestedConditions {
        DefaultDocExporterCondition() {
            super(ConfigurationPhase.PARSE_CONFIGURATION);
        }

        /**
         * 导出开关条件：{@code ydsz.doc.export.enabled=true}（缺省按 true 处理）。
         *
         * @author ydsz-team
         * @since 1.0.0
         */
        @ConditionalOnProperty(prefix = "ydsz.doc.export", name = "enabled", havingValue = "true", matchIfMissing = true)
        static class OnExportEnabled {}

        /**
         * 导出器类型条件：{@code ydsz.doc.exporter=default}（缺省按 default 处理）。
         *
         * @author ydsz-team
         * @since 1.0.0
         */
        @ConditionalOnProperty(prefix = "ydsz.doc", name = "exporter", havingValue = "default", matchIfMissing = true)
        static class OnExporterType {}
    }
}
