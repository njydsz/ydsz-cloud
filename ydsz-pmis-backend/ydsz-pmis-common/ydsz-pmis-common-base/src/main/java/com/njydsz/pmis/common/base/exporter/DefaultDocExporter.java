ackage com.njydsz.pmis.common.base.exporter;

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
import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import com.njydsz.pmis.common.json.type.YdszJsonType;
import com.njydsz.pmis.common.base.config.DocProperties;
import com.njydsz.pmis.common.json.YdszJson;
import com.njydsz.pmis.common.util.json.YamlUtils;

import lombok.RequiredArgsConstructor;

/**
 * 默认文档导出器实现
 *
 * <p>基于 OpenAPI JSON 规范文档，支持导出为 HTML、Markdown、YAML、JSON 四种格式。
 * 默认的 HTML 模板采用内联 CSS，提供开箱即用的 API 文档展示。
 *
 * <p><b>激活条件：</b>同时满足以下两个条件时本 Bean 才会被注册：
 * <ul>
 *   <li>{@code ydsz.doc.export.enabled=true}（默认 true）</li>
 *   <li>{@code ydsz.doc.exporter=default}（默认 default）</li>
 * </ul>
 *
 * <p><b>版本号注入：</b>
 * 文档版本优先从 {@code ydsz.doc.doc-version} 配置读取，
 * 若未配置则自动从 {@code ${spring.application.version}} 或项目打包版本注入。
 *
 * <p><b>线程安全性：</b>无状态 Bean，{@link DocProperties} 与 {@code applicationVersion}
 * 在初始化后即视为只读，因此导出器本身线程安全。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@Component
@RequiredArgsConstructor
@Conditional(DefaultDocExporter.DefaultDocExporterCondition.class)
public class DefaultDocExporter implements DocExporter {

    private static final Logger logger = LoggerFactory.getLogger(DefaultDocExporter.class);

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
        String content = generateHtmlContent(apiDocs);

        String htmlContent = String.format(HTML_TEMPLATE, docInfo.title(), docInfo.title(), docInfo.version(), docInfo.description(), content);
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

        String markdown = generateMarkdownContent(apiDocs);
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
        String version = resolveVersion(apiDocs);
        String description = docProperties.getInfo().getDescription();

        try {
            Map<String, Object> root = YdszJson.toObject(apiDocs, new YdszJsonType<Map<String, Object>>() {});
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
     * @param apiDocs OpenAPI 文档 JSON 字符串（当前未使用，保留扩展）
     * @return 版本号字符串，无法确定时返回 null
     */
    private String resolveVersion(String apiDocs) {
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
     * @param apiDocs OpenAPI 文档 JSON 字符串
     * @return HTML 内容字符串
     */
    private String generateHtmlContent(String apiDocs) {
        return "<pre><code>" + escapeHtml(apiDocs) + "</code></pre>";
    }

    /**
     * 生成 Markdown 内容
     *
     * @param apiDocs OpenAPI 文档 JSON 字符串
     * @return Markdown 内容字符串
     */
    private String generateMarkdownContent(String apiDocs) {
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

    /**
     * 默认文档导出器激活条件
     *
     * <p>同时满足以下两个条件：
     * <ul>
     *   <li>{@code ydsz.doc.export.enabled=true}（默认 true）</li>
     *   <li>{@code ydsz.doc.exporter=default}（默认 default）</li>
     * </ul>
     *
     * @author Marvin Lee
     * @since 1.0.0
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
