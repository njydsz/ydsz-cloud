package com.njydsz.common.base.exporter;

import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;

import com.njydsz.common.base.config.DocProperties;
import com.njydsz.common.json.YdszJson;

/**
 * 多格式文档导出器实现（Markdown 增强版）
 *
 * <p>基于 OpenAPI JSON 规范文档，导出为 HTML、Markdown、YAML、JSON 四种格式。
 * 相比 {@link DefaultDocExporter}，本实现的 Markdown 导出器会解析
 * {@code paths} / {@code components} 等节点，生成包含接口路径、方法、参数、
 * 请求体、响应码以及数据模型等结构化信息的 Markdown 文档。
 *
 * <p><b>激活条件：</b>
 * <ul>
 *   <li>{@code ydsz.doc.export.enabled=true}（默认 true）</li>
 *   <li>{@code ydsz.doc.exporter=markdown}（显式指定）</li>
 * </ul>
 *
 * <p><b>降级策略：</b>当 OpenAPI 文档解析失败时，自动降级为简单格式（仅输出标题与原始 JSON）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Conditional(MarkdownDocExporter.MarkdownDocExporterCondition.class)
public class MarkdownDocExporter extends AbstractDocExporter {

    /**
     * 构造 Markdown 增强文档导出器
     *
     * @param docProperties 文档配置属性
     */
    public MarkdownDocExporter(DocProperties docProperties) {
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
        StringBuilder html = new StringBuilder();
        html.append("<html lang=\"zh-CN\"><head><meta charset=\"UTF-8\">");
        html.append("<style>body{font-family:Arial,sans-serif;margin:20px;}</style></head><body>");
        html.append("<h1>").append(escapeHtml(docInfo.title())).append("</h1>");
        html.append("<p><strong>版本:</strong> ").append(escapeHtml(docInfo.version())).append("</p>");
        html.append("<p><strong>描述:</strong> ").append(escapeHtml(docInfo.description())).append("</p>");
        html.append("<pre><code>").append(escapeHtml(apiDocs)).append("</code></pre>");
        html.append("</body></html>");
        return html.toString();
    }

    /**
     * 生成 Markdown 内容
     */
    private String generateMarkdown(String apiDocs) {
        ApiDocInfo docInfo = parseApiDocInfo(apiDocs);
        StringBuilder md = new StringBuilder();

        // 文档头部
        md.append("# ").append(docInfo.title()).append("\n\n");
        md.append("> **版本:** ").append(docInfo.version()).append("  \n");
        md.append("> **描述:** ").append(docInfo.description()).append("\n\n");
        md.append("---\n\n");

        Map<String, Object> root = parseOpenApiJson(apiDocs);
        if (root.isEmpty()) {
            md.append("## API 规范\n\n");
            md.append("```json\n").append(apiDocs).append("\n```\n");
            return md.toString();
        }

        // 服务器信息
        List<Map<String, Object>> servers = asMapList(root.get("servers"));
        if (servers != null && !servers.isEmpty()) {
            md.append("## 服务器\n\n");
            for (Map<String, Object> server : servers) {
                md.append("- `").append(server.get("url")).append("`");
                if (server.containsKey("description")) {
                    md.append(" - ").append(server.get("description"));
                }
                md.append("\n");
            }
            md.append("\n");
        }

        // 接口路径
        Map<String, Object> paths = asMap(root.get("paths"));
        if (paths != null && !paths.isEmpty()) {
            md.append("## API 接口列表\n\n");
            for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
                String path = pathEntry.getKey();
                Map<String, Object> methods = asMap(pathEntry.getValue());
                if (methods == null) {
                    continue;
                }
                for (Map.Entry<String, Object> methodEntry : methods.entrySet()) {
                    String method = methodEntry.getKey().toUpperCase();
                    if (!isHttpMethod(method)) {
                        continue;
                    }
                    Map<String, Object> operation = asMap(methodEntry.getValue());
                    if (operation != null) {
                        appendEndpointMd(md, method, path, operation);
                    }
                }
            }
        }

        // 数据模型
        Map<String, Object> components = asMap(root.get("components"));
        if (components != null) {
            Map<String, Object> schemas = asMap(components.get("schemas"));
            if (schemas != null && !schemas.isEmpty()) {
                md.append("\n## 数据模型\n\n");
                for (Map.Entry<String, Object> schemaEntry : schemas.entrySet()) {
                    String schemaName = schemaEntry.getKey();
                    Map<String, Object> schema = asMap(schemaEntry.getValue());
                    if (schema != null) {
                        appendSchemaMd(md, schemaName, schema, 0);
                    }
                }
            }
        }

        return md.toString();
    }

    /**
     * 判断是否为有效的 HTTP 方法
     */
    private boolean isHttpMethod(String method) {
        return switch (method) {
            case "GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS" -> true;
            default -> false;
        };
    }

    /**
     * 将单个接口信息追加到 Markdown
     */
    private void appendEndpointMd(StringBuilder md, String method, String path, Map<String, Object> operation) {
        String summary = asString(operation.get("summary"), "");
        String description = asString(operation.get("description"), "");

        md.append("### `").append(method).append("` ").append(path).append("\n\n");

        if (!summary.isEmpty()) {
            md.append("**").append(summary).append("**\n\n");
        }
        if (!description.isEmpty()) {
            md.append(description).append("\n\n");
        }

        // 参数
        List<Map<String, Object>> parameters = asMapList(operation.get("parameters"));
        if (parameters != null && !parameters.isEmpty()) {
            md.append("**请求参数:**\n\n");
            md.append("| 参数名 | 位置 | 类型 | 必填 | 说明 |\n");
            md.append("|--------|------|------|------|------|\n");
            for (Map<String, Object> param : parameters) {
                String name = asString(param.get("name"), "");
                String in = asString(param.get("in"), "");
                Map<String, Object> schema = asMap(param.get("schema"));
                String type = schema != null && schema.containsKey("type")
                        ? asString(schema.get("type"), "string") : "string";
                boolean required = asBoolean(param.get("required"));
                String desc = asString(param.get("description"), "");
                md.append("| ").append(name).append(" | ").append(in).append(" | ").append(type)
                        .append(" | ").append(required ? "是" : "否").append(" | ").append(desc).append(" |\n");
            }
            md.append("\n");
        }

        // 请求体
        Map<String, Object> requestBody = asMap(operation.get("requestBody"));
        if (requestBody != null) {
            md.append("**请求体:**\n\n");
            Map<String, Object> content = asMap(requestBody.get("content"));
            if (content != null && content.containsKey("application/json")) {
                Map<String, Object> jsonContent = asMap(content.get("application/json"));
                Map<String, Object> schema = jsonContent != null ? asMap(jsonContent.get("schema")) : null;
                if (schema != null) {
                    md.append("```json\n");
                    md.append(YdszJson.format(schema));
                    md.append("\n```\n\n");
                }
            }
        }

        // 响应
        Map<String, Object> responses = asMap(operation.get("responses"));
        if (responses != null && !responses.isEmpty()) {
            md.append("**响应:**\n\n");
            for (Map.Entry<String, Object> respEntry : responses.entrySet()) {
                String statusCode = respEntry.getKey();
                Map<String, Object> resp = asMap(respEntry.getValue());
                String desc = resp != null ? asString(resp.get("description"), "") : "";
                md.append("- **").append(statusCode).append("**: ").append(desc).append("\n");
            }
            md.append("\n");
        }

        md.append("---\n\n");
    }

    /**
     * 将数据模型信息追加到 Markdown
     *
     * <p>递归渲染 OpenAPI {@code components.schemas} 中的数据模型，深度最多 3 层以避免循环引用。
     */
    private void appendSchemaMd(StringBuilder md, String name, Map<String, Object> schema, int depth) {
        if (depth > 3) {
            return;
        }

        String indent = "  ".repeat(depth);
        String description = asString(schema.get("description"), "");

        md.append("### ").append(indent).append("`").append(name).append("`");
        if (!description.isEmpty()) {
            md.append(" - ").append(description);
        }
        md.append("\n\n");

        Map<String, Object> properties = asMap(schema.get("properties"));
        List<String> required = asStringList(schema.get("required"));

        if (properties != null && !properties.isEmpty()) {
            md.append("| 字段名 | 类型 | 必填 | 说明 |\n");
            md.append("|--------|------|------|------|\n");

            for (Map.Entry<String, Object> propEntry : properties.entrySet()) {
                String propName = propEntry.getKey();
                Map<String, Object> propSchema = asMap(propEntry.getValue());
                String type = propSchema != null && propSchema.containsKey("type")
                        ? asString(propSchema.get("type"), "object") : "object";
                boolean isRequired = required != null && required.contains(propName);
                String propDesc = propSchema != null
                        ? asString(propSchema.get("description"), "") : "";
                md.append("| ").append(propName).append(" | ").append(type).append(" | ")
                        .append(isRequired ? "是" : "否").append(" | ").append(propDesc).append(" |\n");
            }
            md.append("\n");
        }
    }

    /**
     * Markdown 文档导出器激活条件
     *
     * <p>同时满足以下两个条件：
     * <ul>
     *   <li>{@code ydsz.doc.export.enabled=true}（默认 true）</li>
     *   <li>{@code ydsz.doc.exporter=markdown}（显式指定）</li>
     * </ul>
     */
    static class MarkdownDocExporterCondition extends AllNestedConditions {
        MarkdownDocExporterCondition() {
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
         * 导出器类型条件：{@code ydsz.doc.exporter=markdown}（需显式指定，缺省不激活）。
         *
         * @author ydsz-team
         * @since 1.0.0
         */
        @ConditionalOnProperty(prefix = "ydsz.doc", name = "exporter", havingValue = "markdown")
        static class OnExporterType {}
    }
}
