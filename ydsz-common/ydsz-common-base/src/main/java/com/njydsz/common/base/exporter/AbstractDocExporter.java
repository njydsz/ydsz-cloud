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

import com.njydsz.common.base.config.DocProperties;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.type.JsonType;

/**
 * 文档导出器抽象基类
 *
 * <p>封装 HTML / Markdown / JSON 三种导出格式的公共逻辑， 子类只需覆盖 {@link #generateContent(String, String)} 方法， 根据
 * format 参数（"html" / "markdown"）提供差异化内容生成。
 *
 * <p><b>注册方式：</b>本类为抽象类，不标注 {@code @Component}， 由 {@code DocAutoConfiguration} 通过 {@code @Import}
 * 注册具体子类。
 *
 * <p><b>线程安全性：</b>无状态 Bean，{@link DocProperties} 与 {@code applicationVersion} 在初始化后即视为只读，线程安全。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public abstract class AbstractDocExporter implements DocExporter {

  private static final Logger LOG = LoggerFactory.getLogger(AbstractDocExporter.class);

  /** 支持的导出格式列表 */
  private static final List<String> SUPPORTED_FORMATS = List.of("html", "markdown", "json");

  /**
   * 应用版本号
   *
   * <p>从 {@code spring.application.version} 配置注入，用于文档版本回退。
   */
  @Value("${spring.application.version:}")
  private String applicationVersion;

  /** 文档配置属性 */
  protected final DocProperties docProperties;

  /**
   * 构造文档导出器
   *
   * @param docProperties 文档配置属性
   */
  protected AbstractDocExporter(DocProperties docProperties) {
    this.docProperties = docProperties;
  }

  // ==================== 模板方法（子类覆盖） ====================

  /**
   * 根据格式生成文档内容（核心扩展点）。
   *
   * <p>子类只需覆盖此方法，根据格式类型生成对应的内容字符串， 基类负责文件写入、目录创建、格式分发等通用逻辑。
   *
   * @param apiDocs OpenAPI 文档 JSON 字符串
   * @param format 导出格式（html / markdown / json，小写）
   * @return 生成的文档内容字符串
   */
  protected abstract String generateContent(String apiDocs, String format);

  // ==================== 公共导出方法 ====================

  @Override
  public File export(String apiDocs, String outputDir, String format) throws IOException {
    if (format == null || format.isEmpty()) {
      throw new IllegalArgumentException("导出格式不能为空");
    }
    ensureOutputDirectory(outputDir);

    String normalizedFormat = format.toLowerCase();
    String content;
    String fileName;

    switch (normalizedFormat) {
      case "html" -> {
        fileName = "api-documentation.html";
        content = generateContent(apiDocs, "html");
      }
      case "markdown", "md" -> {
        fileName = "api-documentation.md";
        content = generateContent(apiDocs, "markdown");
      }
      case "json" -> {
        fileName = "api-documentation.json";
        content = apiDocs;
      }
      default -> {
        fileName = "api-documentation." + normalizedFormat;
        content = generateContent(apiDocs, normalizedFormat);
      }
    }

    File outputFile = new File(outputDir, fileName);
    Files.writeString(outputFile.toPath(), content);
    LOG.info("{} 文档已导出: {}", format.toUpperCase(), outputFile.getAbsolutePath());
    return outputFile;
  }

  @Override
  public boolean isSupportedFormat(String format) {
    if (format == null || format.isBlank()) {
      return false;
    }
    return switch (format.toLowerCase()) {
      case "html", "markdown", "md", "json" -> true;
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
   * @param title 文档标题
   * @param version 文档版本
   * @param description 文档描述
   */
  protected record ApiDocInfo(String title, String version, String description) {}

  /**
   * 确保输出目录存在
   *
   * @param outputDir 输出目录路径
   * @throws IOException 如果创建目录失败
   */
  protected void ensureOutputDirectory(String outputDir) throws IOException {
    Path path = Paths.get(outputDir);
    if (!Files.exists(path)) {
      Files.createDirectories(path);
      LOG.debug("创建输出目录: {}", outputDir);
    }
  }

  /**
   * 从 OpenAPI JSON 文档中解析文档基本信息
   *
   * <p>优先使用 DocProperties 中的配置，若 JSON 文档中包含对应字段则覆盖。
   *
   * @param apiDocs OpenAPI 文档 JSON 字符串
   * @return 解析后的文档基本信息
   */
  protected ApiDocInfo parseApiDocInfo(String apiDocs) {
    String title = docProperties.getInfo().getTitle();
    String version = resolveVersion();
    String description = docProperties.getInfo().getDescription();

    try {
      Map<String, Object> root = YdszJson.fromJson(apiDocs, new JsonType<Map<String, Object>>() {});
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
      LOG.debug("解析API文档信息失败,使用默认值", e);
    }

    return new ApiDocInfo(
        title, version != null ? version : docProperties.getInfo().getVersion(), description);
  }

  /**
   * 解析文档版本号
   *
   * <p>优先级：DocProperties 配置 > applicationVersion > null
   *
   * @return 版本号字符串，未配置返回 null
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
   *
   * @param input 原始字符串
   * @return 转义后的安全字符串
   */
  protected String escapeHtml(String input) {
    if (input == null) {
      return "";
    }
    return input
        .replace("&", "&amp;")
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
      return YdszJson.fromJson(apiDocs, new JsonType<Map<String, Object>>() {});
    } catch (Exception e) {
      LOG.warn("解析 OpenAPI 文档失败: {}", e.getMessage());
      return new LinkedHashMap<>(0);
    }
  }

  // ==================== 安全类型转换工具（消除 unchecked cast） ====================

  /**
   * 安全转换为 Map<String, Object>
   *
   * @param value 待转换对象
   * @return 转换后的 Map，类型不匹配返回 null
   */
  protected static Map<String, Object> asMap(Object value) {
    if (value instanceof Map<?, ?> raw) {
      Map<String, Object> result = new LinkedHashMap<>(16);
      for (Map.Entry<?, ?> entry : raw.entrySet()) {
        result.put(String.valueOf(entry.getKey()), entry.getValue());
      }
      return result;
    }
    return null;
  }

  /**
   * 安全转换为 List<Map<String, Object>>
   *
   * @param value 待转换对象
   * @return 转换后的 List，类型不匹配返回 null
   */
  protected static List<Map<String, Object>> asMapList(Object value) {
    if (value instanceof List<?> raw) {
      List<Map<String, Object>> result = new ArrayList<>(16);
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
   *
   * @param value 待转换对象
   * @return 转换后的 List，类型不匹配返回 null
   */
  protected static List<String> asStringList(Object value) {
    if (value instanceof List<?> raw) {
      List<String> result = new ArrayList<>(16);
      for (Object item : raw) {
        result.add(String.valueOf(item));
      }
      return result;
    }
    return null;
  }

  /**
   * 安全获取 Boolean 值
   *
   * @param value 待转换对象
   * @return Boolean 值，类型不匹配返回 false
   */
  protected static boolean asBoolean(Object value) {
    return value instanceof Boolean b && b;
  }

  /**
   * 安全获取字符串值
   *
   * @param value 待转换对象
   * @param defaultValue 默认值
   * @return 字符串值，null 时返回默认值
   */
  protected static String asString(Object value, String defaultValue) {
    if (value == null) {
      return defaultValue;
    }
    return String.valueOf(value);
  }
}
