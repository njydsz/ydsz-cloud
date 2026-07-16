package com.njydsz.common.base.exporter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.njydsz.common.json.type.JsonType;
import com.njydsz.common.base.config.DocProperties;
import com.njydsz.common.json.Json;
import com.njydsz.common.util.yaml.YamlUtils;

import lombok.RequiredArgsConstructor;

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
 * <p><b>线程安全性：</b>无状态 Bean，线程安全。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @since 1.0.0
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ydsz.doc.export", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "ydsz.doc", name = "exporter", havingValue = "markdown")
public class MarkdownDocExporter implements DocExporter {

    private static final Logger logger = LoggerFactory.getLogger(MarkdownDocExporter.class);

    /**
     * 支持的导出格式列表
     *
     * <p>返回给 {@link DocExporter#getSupportedFormats()} 的标准格式名称集合。
     */
    private static final List<String> SUPPORTED_FORMATS = List.of("html", "markdown", "yaml", "json");

    /**
     * 应用版本号
     *
     * <p>从 {@code spring.application.version} 配置注入，用于文档版本回退（无 {@code ydsz.doc.doc-version} 时使用）。
     */
    @Value("${spring.application.version:}")
    private String applicationVersion;

    /** 文档配置属性 */
    private final DocProperties docProperties;

    /**
     * 导出为 HTML 格式
     *
     * @param apiDocs OpenAPI 文档 JSON 字符串
     * @param outputDir 输出目录
     * @return 导出的 HTML 文件对象
     * @throws IOException 如果导出过程中发生 IO 异常
     */
    @Override
    public File exportToHtml(String apiDocs, String outputDir) throws IOException {
        ensureOutputDirectory(outputDir);

        String fileName = "api-documentation.html";
        File outputFile = new File(outputDir, fileName);

        ApiDocInfo docInfo = parseApiDocInfo(apiDocs);
        String htmlContent = generateHtmlContent(docInfo, apiDocs);
        Files.writeString(outputFile.toPath(), htmlContent);

        logger.info("HTML 文档已导出: {}", outputFile.getAbsolutePath());
        return outputFile;
    }

    /**
     * 导出为 Markdown 格式
     *
     * @param apiDocs OpenAPI 文档 JSON 字符串
     * @param outputDir 输出目录
     * @return 导出的 Markdown 文件对象
     * @throws IOException 如果导出过程中发生 IO 异常
     */
    @Override
    public File exportToMarkdown(String apiDocs, String outputDir) throws IOException {
        ensureOutputDirectory(outputDir);

        String fileName = "api-documentation.md";
        File outputFile = new File(outputDir, fileName);

        String markdown = generateRichMarkdownContent(apiDocs);
        Files.writeString(outputFile.toPath(), markdown);

        logger.info("Markdown 文档已导出: {}", outputFile.getAbsolutePath());
        return outputFile;
    }

    /**
     * 导出为 YAML 格式
     *
     * @param apiDocs OpenAPI 文档 JSON 字符串
     * @param outputDir 输出目录
     * @return 导出的 YAML 文件对象
     * @throws IOException 如果导出过程中发生 IO 异常
     */
    @Override
    public File exportToYaml(String apiDocs, String outputDir) throws IOException {
        ensureOutputDirectory(outputDir);

        String fileName = "api-documentation.yaml";
        File outputFile = new File(outputDir, fileName);

        String yamlContent = YamlUtils.jsonToYaml(apiDocs);
        Files.writeString(outputFile.toPath(), yamlContent);

        logger.info("YAML 文档已导出: {}", outputFile.getAbsolutePath());
        return outputFile;
    }

    /**
     * 导出为 JSON 格式
     *
     * @param apiDocs OpenAPI 文档 JSON 字符串
     * @param outputDir 输出目录
     * @return 导出的 JSON 文件对象
     * @throws IOException 如果导出过程中发生 IO 异常
     */
    @Override
    public File exportToJson(String apiDocs, String outputDir) throws IOException {
        ensureOutputDirectory(outputDir);

        String fileName = "api-documentation.json";
        File outputFile = new File(outputDir, fileName);

        Files.writeString(outputFile.toPath(), apiDocs);

        logger.info("JSON 文档已导出: {}", outputFile.getAbsolutePath());
        return outputFile;
    }

    /**
     * 根据格式类型导出
     *
     * @param apiDocs OpenAPI 文档 JSON 字符串
     * @param outputDir 输出目录
     * @param format 导出格式 (html, markdown, yaml, json)
     * @return 导出的文件对象
     * @throws IOException 如果导出过程中发生 IO 异常
     * @throws IllegalArgumentException 如果格式不支持
     */
    @Override
    public File export(String apiDocs, String outputDir, String format) throws IOException {
        if (format == null || format.isEmpty()) {
            throw new IllegalArgumentException("导出格式不能为空");
        }

        format = format.toLowerCase();

        return switch (format) {
            case "html" -> exportToHtml(apiDocs, outputDir);
            case "markdown", "md" -> exportToMarkdown(apiDocs, outputDir);
            case "yaml", "yml" -> exportToYaml(apiDocs, outputDir);
            case "json" -> exportToJson(apiDocs, outputDir);
            default -> throw new IllegalArgumentException("不支持的导出格式: " + format);
        };
    }

    /**
     * 检查是否支持指定的导出格式
     *
     * @param format 格式名称
     * @return 如果支持返回 true,否则返回 false
     */
    @Override
    public boolean isSupportedFormat(String format) {
        if (format == null || format.isBlank()) {
            return false;
        }
        return switch (format.toLowerCase()) {
            case "html", "markdown", "md", "yaml", "yml", "json" -> true;
            default -> false;
        };
    }

    /**
     * 获取所有支持的导出格式
     *
     * @return 支持的格式列表
     */
    @Override
    public String[] getSupportedFormats() {
        return SUPPORTED_FORMATS.toArray(new String[0]);
    }

    /**
     * 确保输出目录存在
     *
     * @param outputDir 输出目录路径
     * @throws IOException 如果创建目录失败
     */
    private void ensureOutputDirectory(String outputDir) throws IOException {
        Path path = Paths.get(outputDir);
        if (!Files.exists(path)) {
            Files.createDirectories(path);
            logger.debug("创建输出目录: {}", outputDir);
        }
    }

    /**
     * API 文档基本信息记录
     *
     * @param title       文档标题
     * @param version     文档版本
     * @param description 文档描述
     */
    private record ApiDocInfo(String title, String version, String description) {}

    /**
     * 从 OpenAPI JSON 文档中解析文档基本信息
     *
     * <p>优先使用 DocProperties 中的配置，若 JSON 文档中包含对应字段则覆盖。</p>
     *
     * @param apiDocs OpenAPI 文档 JSON 字符串
     * @return 解析后的 API 文档基本信息
     */
        private ApiDocInfo parseApiDocInfo(String apiDocs) {
        String title = docProperties.getInfo().getTitle();
        String version = resolveVersion();
        String description = docProperties.getInfo().getDescription();

        try {
            Map<String, Object> root = Json.toObject(apiDocs, new JsonType<Map<String, Object>>() {});
            Map<String, Object> info = (Map<String, Object>) root.get("info");
            if (info != null) {
                if (info.containsKey("title")) {
                    title = String.valueOf(info.get("title"));
                }
                if (info.containsKey("description")) {
                    description = String.valueOf(info.get("description"));
                }
                if (version == null && info.containsKey("version")) {
                    version = String.valueOf(info.get("version"));
                }
            }
        } catch (Exception e) {
            logger.debug("解析API文档信息失败,使用默认值", e);
        }

        return new ApiDocInfo(title, version != null ? version : docProperties.getInfo().getVersion(), description);
    }

    /**
     * 解析文档版本号
     *
     * <p>优先级：DocProperties 配置 > applicationVersion > null</p>
     *
     * @return 版本号字符串，无法确定时返回 null
     */
    private String resolveVersion() {
        if (docProperties.getDocVersion() != null && !docProperties.getDocVersion().isBlank()) {
            return docProperties.getDocVersion();
        }
        if (applicationVersion != null && !applicationVersion.isBlank()) {
            return applicationVersion;
        }
        return null;
    }

    /**
     * 生成 HTML 内容
     *
     * <p>组装完整的 HTML 文档字符串，标题、版本、描述以及 JSON 源数据均经过 HTML 转义，
     * 防止 XSS 注入。
     *
     * @param docInfo 文档基本信息
     * @param apiDocs OpenAPI 文档 JSON 字符串
     * @return HTML 内容字符串
     */
    private String generateHtmlContent(ApiDocInfo docInfo, String apiDocs) {
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
     * 生成结构化的 Markdown 内容
     *
     * 解析 OpenAPI 文档中的 paths、components 等信息,
     * 生成包含接口路径、方法、参数、响应等详细信息的 Markdown 文档
     *
     * @param apiDocs OpenAPI 文档 JSON 字符串
     * @return Markdown 内容字符串
     */
        private String generateRichMarkdownContent(String apiDocs) {
        ApiDocInfo docInfo = parseApiDocInfo(apiDocs);
        StringBuilder md = new StringBuilder();

        // 文档头部
        md.append("# ").append(docInfo.title()).append("\n\n");
        md.append("> **版本:** ").append(docInfo.version()).append("  \n");
        md.append("> **描述:** ").append(docInfo.description()).append("\n\n");
        md.append("---\n\n");

        try {
            Map<String, Object> root = Json.toObject(apiDocs, new JsonType<Map<String, Object>>() {});

            // 服务器信息
            List<Map<String, Object>> servers = (List<Map<String, Object>>) root.get("servers");
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
            Map<String, Object> paths = (Map<String, Object>) root.get("paths");
            if (paths != null && !paths.isEmpty()) {
                md.append("## API 接口列表\n\n");

                for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
                    String path = pathEntry.getKey();
                    Map<String, Object> methods = (Map<String, Object>) pathEntry.getValue();

                    if (methods == null) {
                        continue;
                    }

                    for (Map.Entry<String, Object> methodEntry : methods.entrySet()) {
                        String method = methodEntry.getKey().toUpperCase();
                        if (!isHttpMethod(method)) {
                            continue;
                        }

                        Map<String, Object> operation = (Map<String, Object>) methodEntry.getValue();
                        appendEndpointMd(md, method, path, operation);
                    }
                }
            }

            // 数据模型
            Map<String, Object> components = (Map<String, Object>) root.get("components");
            if (components != null) {
                Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
                if (schemas != null && !schemas.isEmpty()) {
                    md.append("\n## 数据模型\n\n");
                    for (Map.Entry<String, Object> schemaEntry : schemas.entrySet()) {
                        String schemaName = schemaEntry.getKey();
                        Map<String, Object> schema = (Map<String, Object>) schemaEntry.getValue();
                        appendSchemaMd(md, schemaName, schema, 0);
                    }
                }
            }

        } catch (Exception e) {
            logger.warn("解析 OpenAPI 文档失败,降级为简单格式: {}", e.getMessage());
            md.append("## API 规范\n\n");
            md.append("```json\n").append(apiDocs).append("\n```\n");
        }

        return md.toString();
    }

    /**
     * 判断是否为有效的 HTTP 方法
     *
     * @param method HTTP 方法名称
     * @return 是有效的 HTTP 方法返回 true，否则返回 false
     */
    private boolean isHttpMethod(String method) {
        return switch (method) {
            case "GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS" -> true;
            default -> false;
        };
    }

    /**
     * 将单个接口信息追加到 Markdown
     *
     * @param md        Markdown 字符串构建器
     * @param method    HTTP 方法名称
     * @param path      接口路径
     * @param operation OpenAPI 操作对象
     */
        private void appendEndpointMd(StringBuilder md, String method, String path, Map<String, Object> operation) {
        String summary = operation.containsKey("summary") ? String.valueOf(operation.get("summary")) : "";
        String description = operation.containsKey("description") ? String.valueOf(operation.get("description")) : "";

        md.append("### `").append(method).append("` ").append(path).append("\n\n");

        if (!summary.isEmpty()) {
            md.append("**").append(summary).append("**\n\n");
        }
        if (!description.isEmpty()) {
            md.append(description).append("\n\n");
        }

        // 参数
        List<Map<String, Object>> parameters = (List<Map<String, Object>>) operation.get("parameters");
        if (parameters != null && !parameters.isEmpty()) {
            md.append("**请求参数:**\n\n");
            md.append("| 参数名 | 位置 | 类型 | 必填 | 说明 |\n");
            md.append("|--------|------|------|------|------|\n");
            for (Map<String, Object> param : parameters) {
                String name = param.containsKey("name") ? String.valueOf(param.get("name")) : "";
                String in = param.containsKey("in") ? String.valueOf(param.get("in")) : "";
                Map<String, Object> schema = (Map<String, Object>) param.get("schema");
                String type = schema != null && schema.containsKey("type") ? String.valueOf(schema.get("type")) : "string";
                boolean required = param.containsKey("required") && (Boolean) param.get("required");
                String desc = param.containsKey("description") ? String.valueOf(param.get("description")) : "";
                md.append("| ").append(name).append(" | ").append(in).append(" | ").append(type)
                        .append(" | ").append(required ? "是" : "否").append(" | ").append(desc).append(" |\n");
            }
            md.append("\n");
        }

        // 请求体
        Map<String, Object> requestBody = (Map<String, Object>) operation.get("requestBody");
        if (requestBody != null) {
            md.append("**请求体:**\n\n");
            Map<String, Object> content = (Map<String, Object>) requestBody.get("content");
            if (content != null && content.containsKey("application/json")) {
                Map<String, Object> jsonContent = (Map<String, Object>) content.get("application/json");
                Map<String, Object> schema = (Map<String, Object>) jsonContent.get("schema");
                if (schema != null) {
                    md.append("```json\n");
                    md.append(Json.format(schema));
                    md.append("\n```\n\n");
                }
            }
        }

        // 响应
        Map<String, Object> responses = (Map<String, Object>) operation.get("responses");
        if (responses != null && !responses.isEmpty()) {
            md.append("**响应:**\n\n");
            for (Map.Entry<String, Object> respEntry : responses.entrySet()) {
                String statusCode = respEntry.getKey();
                Map<String, Object> resp = (Map<String, Object>) respEntry.getValue();
                String desc = resp.containsKey("description") ? String.valueOf(resp.get("description")) : "";
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
     *
     * @param md     Markdown 字符串构建器
     * @param name   数据模型名称
     * @param schema OpenAPI Schema 对象
     * @param depth  当前递归深度（0 开始）
     */
        private void appendSchemaMd(StringBuilder md, String name, Map<String, Object> schema, int depth) {
        if (depth > 3) {
            return;
        }

        String indent = "  ".repeat(depth);
        String description = schema.containsKey("description") ? String.valueOf(schema.get("description")) : "";

        md.append("### ").append(indent).append("`").append(name).append("`");
        if (!description.isEmpty()) {
            md.append(" - ").append(description);
        }
        md.append("\n\n");

        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        List<String> required = (List<String>) schema.get("required");

        if (properties != null && !properties.isEmpty()) {
            md.append("| 字段名 | 类型 | 必填 | 说明 |\n");
            md.append("|--------|------|------|------|\n");

            for (Map.Entry<String, Object> propEntry : properties.entrySet()) {
                String propName = propEntry.getKey();
                Map<String, Object> propSchema = (Map<String, Object>) propEntry.getValue();
                String type = propSchema.containsKey("type") ? String.valueOf(propSchema.get("type")) : "object";
                boolean isRequired = required != null && required.contains(propName);
                String propDesc = propSchema.containsKey("description") ? String.valueOf(propSchema.get("description")) : "";
                md.append("| ").append(propName).append(" | ").append(type).append(" | ")
                        .append(isRequired ? "是" : "否").append(" | ").append(propDesc).append(" |\n");
            }
            md.append("\n");
        }
    }

    /**
     * 转义 HTML 特殊字符
     *
     * @param input 输入字符串
     * @return 转义后的字符串
     */
    private String escapeHtml(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#x27;");
    }
}
