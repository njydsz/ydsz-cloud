package com.njydsz.pmis.common.doc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档配置属性类
 *
 * <p>用于外部化配置 OpenAPI 与 Knife4j 相关参数，前缀 {@code ydsz.doc}。
 * 通过 {@code application.yml} 即可灵活调整文档模块的行为：
 * <ul>
 *   <li>基础信息：标题、描述、版本、联系方式、许可证</li>
 *   <li>分组策略：单分组 / 多分组模式</li>
 *   <li>导出配置：支持格式、输出目录</li>
 *   <li>安全控制：是否允许生产访问、Basic 认证</li>
 * </ul>
 *
 * <p><b>配置示例：</b>
 * <pre>{@code
 * ydsz:
 *   doc:
 *     enabled: true
 *     api-docs-path: /v3/api-docs
 *     knife4j-path: /doc.html
 *     info:
 *       title: 我的应用 API 文档
 *       version: 1.0.0
 *     groups:
 *       - name: default
 *         base-package: com.example.controller
 *     basic-auth:
 *       enabled: true
 *       username: admin
 *       password: your-secure-password
 * }</pre>
 *
 * <p><b>线程安全性：</b>本类由 Spring Boot 配置属性绑定机制管理，
 * 绑定完成后通常视为只读；若业务方在运行时修改属性需自行保证线程安全。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "ydsz.doc")
public class DocProperties {

    /**
     * 是否启用文档功能，默认为 false
     *
     * <p>出于安全考虑，文档模块默认不启用。需在 application.yml 中显式配置 {@code ydsz.doc.enabled=true} 才会加载
     * OpenAPI/Knife4j 相关 Bean。生产环境建议保持关闭或配合 {@code basicAuth} 认证保护。
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

    /**
     * 文档基础路径，默认为 {@code /v3/api-docs}
     */
    private String apiDocsPath = "/v3/api-docs";

    /**
     * Knife4j 文档访问路径，默认为 {@code /doc.html}
     */
    private String knife4jPath = "/doc.html";

    /**
     * 文档版本号
     *
     * <p>默认从应用版本注入，可通过配置显式覆盖。
     */
    private String docVersion;

    /**
     * OpenAPI 信息配置
     */
    private OpenApiInfo info = new OpenApiInfo();

    /**
     * 分组配置列表
     *
     * <p>为空时使用单分组模式，非空时使用多分组模式。
     */
    private List<GroupConfig> groups = new ArrayList<>();

    /**
     * 导出配置
     */
    private ExportConfig export = new ExportConfig();

    /**
     * OpenAPI 信息配置类
     *
     * <p>对应 OpenAPI 规范中的 {@code info} 对象，承载文档的基础元数据。
     *
     * @author Marvin Lee
     * @since 1.0.0
     */
    @Data
    public static class OpenApiInfo {

        /**
         * 文档标题
         */
        private String title = "REMI API 文档";

        /**
         * 文档描述
         */
        private String description = "REMI 公共框架 API 文档";

        /**
         * 文档版本
         */
        private String version = "1.0.0";

        /**
         * 服务条款 URL
         */
        private String termsOfService = "";

        /**
         * 联系人信息
         */
        private Contact contact = new Contact();

        /**
         * 许可证信息
         */
        private License license = new License();
    }

    /**
     * 联系人信息类
     *
     * @author Marvin Lee
     * @since 1.0.0
     */
    @Data
    public static class Contact {

        /**
         * 联系人姓名
         */
        private String name = "Marvin Lee";

        /**
         * 联系人邮箱
         */
        private String email = "limw1888@126.com";

        /**
         * 联系人 URL
         */
        private String url = "https://njydsz.pmis.com.cn";
    }

    /**
     * 许可证信息类
     *
     * @author Marvin Lee
     * @since 1.0.0
     */
    @Data
    public static class License {

        /**
         * 许可证名称
         */
        private String name = "Apache 2.0";

        /**
         * 许可证 URL
         */
        private String url = "https://www.apache.org/licenses/LICENSE-2.0";
    }

    /**
     * 分组配置类
     *
     * <p>用于在多分组模式下定义单个 API 分组，支持按包扫描或按路径匹配两种方式。
     *
     * @author Marvin Lee
     * @since 1.0.0
     */
    @Data
    public static class GroupConfig {

        /**
         * 分组名称
         */
        private String name = "default";

        /**
         * 分组标题
         */
        private String title;

        /**
         * 分组版本
         */
        private String version = "1.0.0";

        /**
         * 分组描述
         */
        private String description = "默认分组";

        /**
         * 基础包路径，用于扫描 Controller
         */
        private String basePackage = "";

        /**
         * 基础路径匹配规则
         */
        private String basePath = "/**";

        /**
         * 需要排除的路径
         */
        private List<String> excludePaths = new ArrayList<>();

        /**
         * 扫描的包路径列表（支持多包扫描）
         */
        private List<String> packages = new ArrayList<>();

        /**
         * 匹配的路径模式列表（支持多路径匹配）
         */
        private List<String> paths = new ArrayList<>();
    }

    /**
     * 导出配置类
     *
     * @author Marvin Lee
     * @since 1.0.0
     */
    @Data
    public static class ExportConfig {

        /**
         * 是否启用文档导出功能
         */
        private boolean enabled = true;

        /**
         * 默认导出格式 (json, yaml, html, markdown)
         */
        private String format = "json";

        /**
         * 导出目录
         */
        private String outputDir = "./api-docs";

        /**
         * 支持的导出格式
         */
        private List<String> formats = List.of("json", "yaml", "html", "markdown");
    }

    /**
     * Basic 认证配置类
     *
     * <p>用于在生产环境下对 API 文档入口进行简单的 HTTP Basic 认证保护。
     *
     * @author Marvin Lee
     * @since 1.0.0
     */
    @Data
    public static class BasicAuth {

        /**
         * 是否启用 Basic 认证
         */
        private boolean enabled = true;

        /**
         * API 文档访问用户名（必须配置，否则文档端点不可访问）
         */
        private String username;

        /**
         * API 文档访问密码（必须配置，否则文档端点不可访问）
         */
        private String password;
    }
}
