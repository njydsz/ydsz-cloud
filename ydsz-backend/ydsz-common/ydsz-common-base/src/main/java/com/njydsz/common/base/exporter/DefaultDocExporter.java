package com.njydsz.common.base.exporter;

import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

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
@Component
@Conditional(DefaultDocExporter.DefaultDocExporterCondition.class)
public class DefaultDocExporter extends AbstractDocExporter {

    /** HTML 文档模板，使用 String.format 占位符：标题、标题、版本、描述、内容 */
    private static final String HTML_TEMPLATE = """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>%s</title>
                <style>
                    body { font-family: Arial, sans-serif; margin: 20px; background-color: #f5f5f5; }
                    .container { max-width: 1200px; margin: 0 auto; background-color: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
                    h1 { color: #333; border-bottom: 2px solid #007bff; padding-bottom: 10px; }
                    h2 { color: #555; margin-top: 30px; }
                    pre { background-color: #f8f9fa; padding: 15px; border-radius: 4px; overflow-x: auto; }
                    code { font-family: 'Courier New', monospace; }
                    .endpoint { background-color: #e7f3ff; padding: 10px; margin: 10px 0; border-radius: 4px; border-left: 4px solid #007bff; }
                    .method { font-weight: bold; color: #007bff; }
                    .path { font-family: monospace; color: #333; }
                    .description { color: #666; margin-top: 5px; }
                    table { border-collapse: collapse; width: 100%%; margin: 15px 0; }
                    th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
                    th { background-color: #007bff; color: white; }
                    tr:nth-child(even) { background-color: #f2f2f2; }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>%s</h1>
                    <p><strong>版本:</strong> %s</p>
                    <p><strong>描述:</strong> %s</p>
                    %s
                </div>
            </body>
            </html>
            """;

    /**
     * 构造默认文档导出器
     *
     * @param docProperties 文档配置属性
     */
    public DefaultDocExporter(DocProperties docProperties) {
        super(docProperties);
    }

    @Override
    protected String generateHtmlContent(ApiDocInfo docInfo, String apiDocs) {
        String content = "<pre><code>" + escapeHtml(apiDocs) + "</code></pre>";
        return String.format(HTML_TEMPLATE, docInfo.title(), docInfo.title(),
                docInfo.version(), docInfo.description(), content);
    }

    @Override
    protected String generateMarkdownContent(String apiDocs) {
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

        @ConditionalOnProperty(prefix = "ydsz.doc.export", name = "enabled", havingValue = "true", matchIfMissing = true)
        static class OnExportEnabled {}

        @ConditionalOnProperty(prefix = "ydsz.doc", name = "exporter", havingValue = "default", matchIfMissing = true)
        static class OnExporterType {}
    }
}
