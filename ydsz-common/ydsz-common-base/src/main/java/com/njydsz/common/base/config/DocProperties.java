package com.njydsz.common.base.config;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import com.njydsz.common.base.constant.DocConstants;

/**
 * 文档配置属性类
 *
 * <p>用于外部化配置 OpenAPI 与 Knife4j 相关参数，前缀 {@code ydsz.doc}。 通过 {@code application.yml} 即可灵活调整文档模块的行为：
 *
 * <ul>
 *   <li>基础信息：标题、描述、版本、联系方式、许可证
 *   <li>分组策略：单分组 / 多分组模式
 *   <li>导出配置：支持格式、输出目录
 *   <li>安全控制：是否允许生产访问、Basic 认证
 * </ul>
 *
 * <p><b>配置示例：</b>
 *
 * <pre>{@code
 * ydsz:
 *   doc:
 *     enabled: true
 *     api-docs-path: /v3/api-docs
 *     knife4j-path: /doc.html
 *     info:
 *       title: 我的应用 API 文档
 *       version: 26.09.01
 *     groups:
 *       - name: default
 *         base-package: com.example.controller
 *     basic-auth:
 *       enabled: true
 *       username: admin
 *       password: your-secure-password
 * }</pre>
 *
 * <p><b>线程安全性：</b>本类由 Spring Boot 配置属性绑定机制管理， 绑定完成后通常视为只读；若业务方在运行时修改属性需自行保证线程安全。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@ConfigurationProperties(prefix = "ydsz.doc")
public class DocProperties {

  /**
   * 是否启用文档功能，默认为 false
   *
   * <p>出于安全考虑，文档模块默认不启用。需在 application.yml 中显式配置 {@code ydsz.doc.enabled=true} 才会加载 OpenAPI/Knife4j
   * 相关 Bean。生产环境建议保持关闭或配合 {@code basicAuth} 认证保护。
   */
  private boolean enabled = false;

  /**
   * 生产环境是否允许访问文档，默认为 false（生产环境默认关闭）
   *
   * <p>开启后需配合 basicAuth 配置进行认证保护。
   */
  private boolean productionEnabled = false;

  /**
   * Basic 认证配置
   *
   * <p>开启文档访问控制时生效，用于对 Swagger/Knife4j 入口进行简单密码保护。
   */
  private BasicAuth basicAuth = new BasicAuth();

  /** 文档基础路径，默认为 {@code /v3/api-docs} */
  private String apiDocsPath = DocConstants.DEFAULT_API_DOCS_PATH;

  /** Knife4j 文档访问路径，默认为 {@code /doc.html} */
  private String knife4jPath = DocConstants.DEFAULT_KNIFE4J_PATH;

  /**
   * 文档版本号
   *
   * <p>默认从应用版本注入，可通过配置显式覆盖。
   */
  private String docVersion;

  /** OpenAPI 信息配置 */
  private OpenApiInfo info = new OpenApiInfo();

  /**
   * 分组配置列表
   *
   * <p>为空时使用单分组模式，非空时使用多分组模式。
   */
  private List<GroupConfig> groups = new ArrayList<>(4);

  /** 导出配置 */
  private ExportConfig export = new ExportConfig();

  /** 导出格式列表（默认 markdown, html） */
  private List<String> exportFormats = Arrays.asList("markdown", "html");

  /** 导出输出目录（默认系统临时目录下的 doc-export 子目录） */
  // CHECKSTYLE.OFF: RegexpSinglelineJava — JDK 系统属性名含 java.io 前缀，为字符串常量非代码引用
  private String outputDir =
      System.getProperty("java.io.tmpdir") + "/doc-export";
  // CHECKSTYLE.ON: RegexpSinglelineJava

  // ====================== 嵌套配置类 ======================

  /** Basic 认证配置 */
  @Data
  public static class BasicAuth {

    /** 是否启用 Basic 认证 */
    private boolean enabled = false;

    /** Basic 认证用户名 */
    private String username = "admin";

    /** Basic 认证密码 */
    private String password = "admin123";
  }

  /** OpenAPI 信息配置 */
  @Data
  public static class OpenApiInfo {

    /** 文档标题 */
    private String title = "API Documentation";

    /** 文档描述 */
    private String description = "";

    /** 文档版本 */
    private String version = DocConstants.DEFAULT_API_VERSION;

    /** 服务条款 URL */
    private String termsOfService = "";

    /** 联系人信息 */
    private Contact contact = new Contact();

    /** 许可证信息 */
    private License license = new License();
  }

  /** 联系人信息配置 */
  @Data
  public static class Contact {

    /** 联系人姓名 */
    private String name = "ydsz-team";

    /** 联系人邮箱 */
    private String email = "devops@ydsz.example.com";

    /** 联系人 URL */
    private String url = "https://ydszsoft.ydsz.com.cn";
  }

  /** 许可证信息配置 */
  @Data
  public static class License {

    /** 许可证名称 */
    private String name = "Apache 2.0";

    /** 许可证 URL */
    private String url = "https://www.apache.org/licenses/LICENSE-2.0";
  }

  /** 分组配置 */
  @Data
  public static class GroupConfig {

    /** 分组名称 */
    private String name = "default";

    /** 分组标题（为空时使用全局 title） */
    private String title;

    /** 分组版本（为空时使用全局 version） */
    private String version = DocConstants.DEFAULT_API_VERSION;

    /** 分组描述 */
    private String description = "默认分组";

    /** 扫描的基础包路径 */
    private String basePackage = "";

    /** 基础路径匹配规则 */
    private String basePath = "/**";

    /** 需要排除的路径 */
    private List<String> excludePaths = new ArrayList<>(16);

    /** 扫描的包路径列表（支持多包扫描） */
    private List<String> packages = new ArrayList<>(16);

    /** 匹配的路径模式列表（支持多路径匹配） */
    private List<String> paths = new ArrayList<>(16);
  }

  /** 导出配置 */
  @Data
  public static class ExportConfig {

    /** 是否启用文档导出功能 */
    private boolean enabled = true;

    /** 默认导出格式 (json, yaml, html, markdown) */
    private String format = DocConstants.FORMAT_JSON;

    /** 导出目录 */
    private String outputDir = "./api-docs";
  }
}
