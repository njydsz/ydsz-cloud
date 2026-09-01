package com.njydsz.common.base.constant;

/**
 * OpenAPI 文档常量类
 *
 * <p>集中维护文档模块使用的所有字符串常量，包括 OpenAPI 版本、默认路径、格式标识、 配置属性前缀等。工具类不允许实例化。
 *
 * <p><b>线程安全性：</b>所有字段均为 {@code public static final}，线程安全。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class DocConstants {

  /**
   * 私有构造方法，工具类禁止实例化。
   *
   * @throws IllegalStateException 任何实例化尝试都会抛出
   */
  private DocConstants() {
    throw new IllegalStateException("Utility class");
  }

  /** OpenAPI 版本标识 */
  public static final String OPENAPI_VERSION = "3.0.3";

  /** 默认 API 文档路径 */
  public static final String DEFAULT_API_DOCS_PATH = "/v3/api-docs";

  /** 默认 Knife4j 文档访问路径 */
  public static final String DEFAULT_KNIFE4J_PATH = "/doc.html";

  /** 默认分组名称 */
  public static final String DEFAULT_GROUP_NAME = "default";

  /** 默认 API 版本 */
  public static final String DEFAULT_API_VERSION = "26.09.01";

  /** JSON 格式标识 */
  public static final String FORMAT_JSON = "json";

  /** YAML 格式标识 */
  public static final String FORMAT_YAML = "yaml";

  /** HTML 格式标识 */
  public static final String FORMAT_HTML = "html";

  /** Markdown 格式标识 */
  public static final String FORMAT_MARKDOWN = "markdown";

  /** YDSZ 文档配置属性前缀 */
  public static final String YDSZ_DOC_PREFIX = "ydsz.doc";
}
