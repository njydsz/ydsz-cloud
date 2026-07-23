package com.njydsz.common.base.exporter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.njydsz.common.base.config.DocProperties;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.type.YdszJsonType;
import com.njydsz.common.util.yaml.YamlUtils;

import lombok.RequiredArgsConstructor;

/**
 * 文档导出器抽象基类
 *
 * <p>封装 HTML / Markdown / YAML / JSON 四种导出格式的公共逻辑，
 * 子类只需覆盖 {@link #generateHtmlContent(ApiDocInfo, String)} 和
 * {@link #generateMarkdownContent(String)} 方法提供差异化内容生成。
 *
 * <p><b>线程安全性：</b>无状态 Bean，{@link DocProperties} 与 {@code applicationVersion}
 * 在初始化后即视为只读，线程安全。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Component
@RequiredArgsConstructor
public abstract class AbstractDocExporter implements DocExporter {

    private static final Logger logger = LoggerFactory.getLogger(AbstractDocExporter.class);

    /**
     * 支持的导出格式列表
     */
    private static final List<String> SUPPORTED_FORMATS = List.of("html", "markdown", "yaml", "json");

    /**
     * 应用版本号
     *
     * <p>从 {@code spring.application.version} 配置注入，用于文档版本回退。
     */
    @Value("${spring.application.version:}")
    private String applicationVersion;

    /** 文档配置属性 */
    protected final DocProperties docProperties;

    // ==================== 抽象方法 ====================

    /**
     * 生成 HTML 内容
     *
     * @param docInfo 文档基本信息
     * @param apiDocs OpenAPI 文档 JSON 字符串
     * @return HTML 内容字符串
     */
    protected abstract String generateHtmlContent(ApiDocInfo docInfo, String apiDocs);

    /**
     * 生成 Markdown 内容
     *
     * @param apiDocs OpenAPI 文档 JSON 字符串
     * @return Markdown 内容字符串
     */
    protected abstract String generateMarkdownContent(String apiDocs);

    // ==================== 公共导出方法 ====================

    @Override
    public File exportToHtml(String apiDocs, String outputDir) throws IOException {
        ensureOutputDirectory(outputDir);
        File outputFile = new File(outputDir, "api-documentation.html");
        ApiDocInfo docInfo = parseApiDocInfo(apiDocs);
        String htmlContent = generateHtmlContent(docInfo, apiDocs);
        Files.writeString(outputFile.toPath(), htmlContent);
        logger.info("HTML 文档已导出: {}", outputFile.getAbsolutePath());
        return outputFile;
    }

    @Override
    public File exportToMarkdown(String apiDocs, String outputDir) throws IOException {
        ensureOutputDirectory(outputDir);
        File outputFile = new File(outputDir, "api-documentation.md");
        String markdown = generateMarkdownContent(apiDocs);
        Files.writeString(outputFile.toPath(), markdown);
        logger.info("Markdown 文档已导出: {}", outputFile.getAbsolutePath());
        return outputFile;
    }

    @Override
    public File exportToYaml(String apiDocs, String outputDir) throws IOException {
        ensureOutputDirectory(outputDir);
        File outputFile = new File(outputDir, "api-documentation.yaml");
        String yamlContent = YamlUtils.jsonToYaml(apiDocs);
        Files.writeString(outputFile.toPath(), yamlContent);
        logger.info("YAML 文档已导出: {}", outputFile.getAbsolutePath());
        return outputFile;
    }

    @Override
    public File exportToJson(String apiDocs, String outputDir) throws IOException {
        ensureOutputDirectory(outputDir);
        File outputFile = new File(outputDir, "api-documentation.json");
        Files.writeString(outputFile.toPath(), apiDocs);
        logger.info("JSON 文档已导出: {}", outputFile.getAbsolutePath());
        return outputFile;
    }

    @Override
    public File export(String apiDocs, String outputDir, String format) throws IOException {
        if (format == null || format.isEmpty()) {
            throw new IllegalArgumentException("导出格式不能为空");
        }
        String fmt = format.toLowerCase();
        return switch (fmt) {
            case "html" -> exportToHtml(apiDocs, outputDir);
            case "markdown", "md" -> exportToMarkdown(apiDocs, outputDir);
            case "yaml", "yml" -> exportToYaml(apiDocs, outputDir);
            case "json" -> exportToJson(apiDocs, outputDir);
            default -> throw new IllegalArgumentException("不支持的导出格式: " + fmt);
        };
    }

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

    @Override
    public String[] getSupportedFormats() {
        return SUPPORTED_FORMATS.toArray(new String[0]);
    }

    // ==================== 公共工具方法 ====================

    /**
     * API 文档基本信息记录
     *
     * @param title       文档标题
     * @param version     文档版本
     * @param description 文档描述
     */
    protected record ApiDocInfo(String title, String version, String description) {}

    /**
     * 确保输出目录存在
     */
    protected void ensureOutputDirectory(String outputDir) throws IOException {
        Path path = Paths.get(outputDir);
        if (!Files.exists(path)) {
            Files.createDirectories(path);
            logger.debug("创建输出目录: {}", outputDir);
        }
    }

    /**
     * 从 OpenAPI JSON 文档中解析文档基本信息
     *
     * <p>优先使用 DocProperties 中的配置，若 JSON 文档中包含对应字段则覆盖。
     */
    protected ApiDocInfo parseApiDocInfo(String apiDocs) {
        String title = docProperties.getInfo().getTitle();
        String version = resolveVersion();
        String description = docProperties.getInfo().getDescription();

        try {
            Map<String, Object> root = YdszJson.toObject(apiDocs, new YdszJsonType<Map<String, Object>>() {});
            Map<String, Object> info = asMap(root.get("info"));
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
     * <p>优先级：DocProperties 配置 > applicationVersion > null
     */
    protected String resolveVersion() {
        if (docProperties.getDocVersion() != null && !docProperties.getDocVersion().isBlank()) {
            return docProperties.getDocVersion();
        }
        if (applicationVersion != null && !applicationVersion.isBlank()) {
            return applicationVersion;
        }
        return null;
    }

    /**
     * 转义 HTML 特殊字符
     */
    protected String escapeHtml(String input) {
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
     * 解析 OpenAPI JSON 文档为 Map
     *
     * @param apiDocs OpenAPI 文档 JSON 字符串
     * @return 解析后的 Map，解析失败返回空 Map
     */
    protected Map<String, Object> parseOpenApiJson(String apiDocs) {
        try {
            return YdszJson.toObject(apiDocs, new YdszJsonType<Map<String, Object>>() {});
        } catch (Exception e) {
            logger.warn("解析 OpenAPI 文档失败: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    // ==================== 安全类型转换工具（消除 unchecked cast） ====================

    /**
     * 安全转换为 Map<String, Object>
     */
    protected static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        return null;
    }

    /**
     * 安全转换为 List<Map<String, Object>>
     */
    protected static List<Map<String, Object>> asMapList(Object value) {
        if (value instanceof List<?> raw) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : raw) {
                Map<String, Object> map = asMap(item);
                if (map != null) {
                    result.add(map);
                }
            }
            return result;
        }
        return null;
    }

    /**
     * 安全转换为 List<String>
     */
    protected static List<String> asStringList(Object value) {
        if (value instanceof List<?> raw) {
            List<String> result = new ArrayList<>();
            for (Object item : raw) {
                result.add(String.valueOf(item));
            }
            return result;
        }
        return null;
    }

    /**
     * 安全获取 Boolean 值
     */
    protected static boolean asBoolean(Object value) {
        return value instanceof Boolean b && b;
    }

    /**
     * 安全获取字符串值
     */
    protected static String asString(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return String.valueOf(value);
    }
}
