# ydsz-common-base

> YDSZ Web/App 公共 HTTP 基座模块（L6 应用层）

提供 CORS、时区、I18n、安全响应头、TraceId、请求日志、上下文清理、全局响应包装、OpenAPI/Knife4j 文档、文档导出、健康检查、模块指标基类等共享能力，是 `common-web` 与 `common-app` 两个应用层入口的统一抽象基座。本模块的所有 MVC 配置类、Properties、Filter、Interceptor、Advice 均为抽象基类或接口，子模块通过继承并提供具体 `@ConfigurationProperties` 前缀实现差异化装配。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L6 应用层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供 Web/App 共享的 HTTP 基座：CORS、时区、I18n、安全头、TraceId、请求日志、上下文清理、全局响应包装、OpenAPI/Knife4j 文档、文档导出、健康检查、模块指标基类 |
| **依赖** | common-core、common-util、common-exception、common-json；可选依赖 spring-boot-actuator、spring-boot-health、springdoc-openapi、knife4j、spring-boot-starter-webflux、micrometer-core |
| **版本** | 1.0.0 |

## 核心能力

### 1. MVC 配置基类

| 类 | 说明 |
|---|---|
| `BaseMvcConfiguration` | MVC 配置抽象基类（实现 `WebMvcConfigurer`），子类提供具体的 `BaseCorsProperties` 和 `BaseTraceProperties` 实现并注册自己的拦截器和过滤器 Bean；统一注册 `CorsFilter` 并在启动时执行 CORS 安全校验 |
| `BaseCorsProperties` | CORS 配置属性抽象基类（`@ConfigurationProperties`），子类通过 `prefix` 指定具体前缀；含 `validateSecurity()` 方法检测不安全组合（`allowCredentials=true` 且 `*`、来源为空、过度开放等） |
| `BaseTraceProperties` | 请求追踪/日志配置属性抽象基类，提供链路追踪、请求日志、采样率、慢请求阈值、响应头等通用配置项；`getSamplingRate()` 自动修正到 `[0.0, 1.0]` 范围 |

### 2. 时区与国际化

| 类 | 说明 |
|---|---|
| `BaseTimezoneConfiguration` | 时区配置抽象基类，`@PostConstruct` 强制将 JVM 默认时区设置为配置值（默认 `Asia/Shanghai`，UTC+8），保证全局时间一致性；通过 `ydsz.base.timezone` 自定义 |
| `BaseI18nConfiguration` | 国际化配置抽象基类，子类覆盖 `getBasenames()` 接入不同 i18n 资源文件；默认 `Accept-Language` 解析，支持 `zh_CN` 和 `en_US`，缺失 key 时回退到 code 而非抛异常 |

### 3. 过滤器链

| 类 | 说明 |
|---|---|
| `TraceFilter` | 链路追踪过滤器，生成或提取 traceId 注入 MDC 和 `RequestContext`，并在响应头返回；对传入的 traceId 进行长度（≤64）和字符集（`[a-zA-Z0-9_-]`）校验，防止日志注入；不在 finally 清理 MDC，统一由 `RequestContextCleanupFilter` 清理 |
| `SecurityHeadersFilter` | 安全响应头过滤器（base 模块兜底实现），添加 `X-Content-Type-Options`、`X-Frame-Options`、`X-XSS-Protection`、`Strict-Transport-Security`、`Content-Security-Policy`、`Referrer-Policy` 等头部；通过 `@ConditionalOnMissingBean(name="securityHeaderFilter")` 保证与 web/app/safe 模块的同名过滤器互斥 |
| `RequestContextCleanupFilter` | 请求上下文清理过滤器，在 `finally` 块中清理 `RequestContext` 和 `MDC`，防止 ThreadLocal 内存泄漏；以 `LOWEST_PRECEDENCE` 注册，保证在所有 Filter 之后执行 |
| `AbstractContentCachingFilter` | 请求体缓存过滤器抽象基类，包装请求为 `ContentCachingRequestWrapper` 支持多次读取；跳过 multipart 请求避免大文件上传 OOM；默认缓存容量 512KB，可由子类构造器自定义 |
| `BaseRequestIdResponseFilter` | 请求 ID 响应头过滤器抽象基类，子类覆盖 `resolveRequestId(HttpServletRequest)` 提供不同 ID 来源；通过 `BaseTraceProperties.isResponseHeaderEnabled()` 控制是否生效 |

### 4. 拦截器

| 类 | 说明 |
|---|---|
| `BaseRequestLogInterceptor` | 请求日志拦截器抽象基类，子类覆盖 `resolveRequestId(HttpServletRequest)` 和 `getLogger()`；`preHandle` 输出入口日志（method/uri/ip/ua），`afterCompletion` 输出完成日志（status/time/error），慢请求升级为 WARN；支持采样率跳过 |
| `RequestIdResolver` | 请求 ID 解析器接口，统一定义 `resolveRequestId(HttpServletRequest)` 方法签名，供 Filter 和 Interceptor 共享 |

### 5. 全局响应包装

| 类 | 说明 |
|---|---|
| `BaseGlobalResponseAdvice` | 全局响应包装抽象基类（实现 `ResponseBodyAdvice<Object>`），自动将非 `YdszResponse` 类型返回值包装为 `YdszResponse.success(Object)`；跳过 `YdszResponse`、`void`、`ResponseEntity`、`HttpEntity`、`Resource`；子类覆盖 `wrapStringBody(String)` 处理 String 返回值差异（Web 端用 `success(msg)`，App 端用 `successMsg(msg)`） |

### 6. 自动配置

| 类 | 说明 |
|---|---|
| `BaseAutoConfiguration` | 总自动配置（`@AutoConfiguration`），激活条件：`@ConditionalOnWebApplication` + `ydsz.base.enabled=true`（默认 true）；注册 `TraceFilter`、`SecurityHeadersFilter`（兜底）、`RequestContextCleanupFilter`、`BaseHealthIndicator`（需 actuator 依赖） |

### 7. OpenAPI 文档配置

| 类 | 说明 |
|---|---|
| `BaseOpenApiConfiguration` | OpenAPI 文档配置抽象基类，子类覆盖 `getTitle()` 和 `getDescription()`；默认注册所有公共请求头（X-User-Id、X-Tenant-Id、X-Access-Token 等）、JWT Bearer Token 认证方案、统一联系信息；激活条件 `ydsz.doc.enabled=true` |
| `OpenApiAutoConfiguration` | OpenAPI 自动配置，`@ConditionalOnClass(SpringDocConfiguration)` + `ydsz.doc.enabled=true`；支持单分组（默认匹配 `/**`）和多分组模式（按 `packages` 或 `paths` 列表扫描）；当业务模块通过继承 `BaseOpenApiConfiguration` 提供自定义 `OpenAPI` Bean 时，本 Bean 自动退出 |
| `Knife4jAutoConfiguration` | Knife4j 增强 UI 自动配置，`@ConditionalOnClass(Knife4jOpenApiCustomizer)` + `ydsz.doc.enabled=true`；提供离线文档导出、全局参数配置、增强搜索分组等能力 |
| `DocAutoConfiguration` | 文档模块入口配置，激活条件 `ydsz.doc.enabled=true`（默认 false）；`@Import` 激活 `OpenApiAutoConfiguration`、`Knife4jAutoConfiguration`、`DefaultDocExporter`、`MarkdownDocExporter` |
| `DocProperties` | 文档配置属性（`ydsz.doc`），包含 `enabled`、`productionEnabled`、`basicAuth`、`apiDocsPath`、`knife4jPath`、`docVersion`、`info`（OpenApiInfo）、`groups`（GroupConfig 列表）、`export`（ExportConfig） |
| `DocSecurityConfiguration` | 文档安全配置，生产环境通过 `ydsz.doc.production-enabled=true` 启用 Basic 认证保护；构造阶段检测 Profile 包含 `prod`/`production` 时输出安全告警；`DocBasicAuthFilter` 使用 `MessageDigest.isEqual` 恒定时间比较防止时序攻击 |

### 8. 文档导出

| 类 | 说明 |
|---|---|
| `DocExporter` | 文档导出器 SPI 接口，定义 `exportToHtml`、`exportToMarkdown`、`exportToYaml`、`exportToJson`、`export(format)`、`isSupportedFormat`、`getSupportedFormats` 方法 |
| `AbstractDocExporter` | 文档导出器抽象基类，封装 HTML/Markdown/YAML/JSON 四种格式的公共逻辑；子类覆盖 `generateHtmlContent` 和 `generateMarkdownContent`；包含安全类型转换工具（`asMap`/`asMapList`/`asStringList`/`asBoolean`/`asString`）消除 unchecked cast |
| `DefaultDocExporter` | 默认导出器（简单格式） |
| `MarkdownDocExporter` | Markdown 增强导出器（结构化 Markdown） |

### 9. 健康检查与 Actuator 端点

| 类 | 说明 |
|---|---|
| `BaseHealthIndicator` | 健康指标，报告时区配置、安全响应头状态、文档功能状态；当安全响应头启用但 `frameOptions` 为空、或生产环境文档启用但 Basic 认证未开启时标记为 DOWN |
| `ConfigRegistryEndpoint` | Actuator 端点（`@Endpoint(id="config-registry")`），暴露 `GET /actuator/config-registry` 查看所有 `ydsz.*` 配置，`GET /actuator/config-registry/{prefix}` 查看指定前缀下的配置 |

### 10. 模块指标基类

| 类 | 说明 |
|---|---|
| `AbstractMetricsHolder` | 模块指标工具类，提供静态方法 `registerCounter` / `registerTimer` / `recordDuration`，统一管理 Micrometer 指标命名与实例缓存 |

### 11. 常量与认证上下文基类

| 类 | 说明 |
|---|---|
| `FilterOrder` | Servlet Filter 执行顺序常量，定义各 Filter 的 `order` 值（基于 `Ordered.HIGHEST_PRECEDENCE` 体系）；所有数字与 `docs/BASE_INTERCEPTOR_ORDER.md` 保持一致 |
| `InterceptorOrder` | Spring MVC Interceptor 执行顺序常量，定义各 Interceptor 的 `order` 值（自然数体系） |
| `AdviceOrder` | ControllerAdvice 执行顺序常量，定义各 Advice 的 `order` 值（自然数体系） |
| `DocConstants` | OpenAPI 文档常量，集中维护 `OPENAPI_VERSION`（3.0.3）、`DEFAULT_API_DOCS_PATH`、`DEFAULT_KNIFE4J_PATH`、`DEFAULT_GROUP_NAME`、`DEFAULT_API_VERSION`（1.0.0）、格式标识、配置属性前缀等 |
| `BaseAuthInfo` | 认证上下文信息抽象基类（继承 `YdszAuthInfo`），子类覆盖 `getServiceTypeCode()` 返回具体服务类型编码（"WEB"/"APP"/"API"），用于业务层区分请求来源 |

### 横切点执行顺序

Filter 链（Order 由小到大）：

| 顺序 | Filter | 说明 |
|---|---|---|
| `HIGHEST_PRECEDENCE + 10` | `TraceFilter` / `RequestIdResponseFilter` | 生成/透传 traceId |
| `HIGHEST_PRECEDENCE + 20` | `ContentCachingFilter` | 包装 request body |
| `HIGHEST_PRECEDENCE + 30` | `SecurityHeaderFilter` | 安全响应头 |
| `HIGHEST_PRECEDENCE + 40` | `TraceIdResponseFilter` | traceId 注入 response header |
| `HIGHEST_PRECEDENCE + 50` | `AuthFilter` | JWT/Session 鉴权 |
| `LOWEST_PRECEDENCE` | `RequestContextCleanupFilter` | 清理 TTL |

Interceptor 与 Advice 顺序：

| 顺序 | 组件 | 说明 |
|---|---|---|
| 10 | `BaseRequestLogInterceptor` | 请求/响应日志 |
| 0 | `GlobalResponseAdvice` | 统一响应包装（最先） |
| 10 | `BaseExceptionHandler` | 业务异常 |
| 20 | `MvcExceptionHandler` | MVC 框架异常 |
| 30 | `ValidationExceptionHandler` | 参数校验异常 |

## 接入方式

### 1. POM 引入依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-base</artifactId>
</dependency>
```

> 本模块的 `spring-boot-actuator`、`spring-boot-health`、`springdoc-openapi`、`knife4j`、`spring-boot-starter-webflux`、`micrometer-core` 均为 `<optional>true</optional>`，业务方按需引入对应 starter 即可激活相应能力。

### 2. 配置启用

```yaml
ydsz:
  base:
    enabled: true                # 启用 base 模块（默认 true）
    timezone: Asia/Shanghai      # JVM 默认时区
```

### 3. 继承抽象基类（业务方直接使用 base 模块时）

业务方若不引入 `common-web` 或 `common-app`，需自行继承抽象基类并提供具体 `@ConfigurationProperties` 前缀：

```java
import com.njydsz.common.base.config.BaseCorsProperties;
import com.njydsz.common.base.config.BaseTraceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ydsz.custom.cors")
public class CustomCorsProperties extends BaseCorsProperties {
}

@ConfigurationProperties(prefix = "ydsz.custom.trace")
public class CustomTraceProperties extends BaseTraceProperties {
}
```

> 推荐做法：直接使用 `common-web` 或 `common-app` 子模块，无需手动继承。本模块的 `BaseAutoConfiguration` 已为直接使用场景提供 `SecurityHeadersFilter` 兜底实现。

## 配置项

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.base.enabled` | true | 是否启用 base 模块 |
| `ydsz.base.timezone` | `Asia/Shanghai` | JVM 默认时区 |
| `ydsz.base.security-headers.enabled` | true | 是否启用安全响应头 |
| `ydsz.base.security-headers.xss-protection` | `1; mode=block` | XSS 防护头部 |
| `ydsz.base.security-headers.content-type-options` | `nosniff` | 内容类型选项头部 |
| `ydsz.base.security-headers.frame-options` | `DENY` | 帧选项头部（DENY/SAMEORIGIN） |
| `ydsz.base.security-headers.hsts` | `max-age=31536000; includeSubDomains` | 严格传输安全头部 |
| `ydsz.base.security-headers.csp` | `default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'` | 内容安全策略头部 |
| `ydsz.base.security-headers.referrer-policy` | `strict-origin-when-cross-origin` | 引用策略头部 |
| `ydsz.base.security-headers.excludes` | `[]` | 排除路径列表（Ant 风格） |
| `ydsz.base.trace.enabled` | true | 是否启用链路追踪（子模块前缀为 `ydsz.web.trace` / `ydsz.app.trace`） |
| `ydsz.base.trace.response-header-enabled` | true | 是否在响应头输出请求 ID |
| `ydsz.base.trace.request-id-header-name` | `X-Request-Id` | 请求 ID 响应头名称 |
| `ydsz.base.trace.request-log-enabled` | true | 是否启用请求日志 |
| `ydsz.base.trace.request-log-format` | `detailed` | 请求日志格式（simple/detailed） |
| `ydsz.base.trace.log-request-params` | true | 是否记录请求参数 |
| `ydsz.base.trace.log-request-body` | false | 是否记录请求体 |
| `ydsz.base.trace.log-response-body` | false | 是否记录响应体 |
| `ydsz.base.trace.log-level` | `INFO` | 日志级别（INFO/DEBUG） |
| `ydsz.base.trace.sampling-rate` | 1.0 | 日志采样率 [0.0, 1.0]，超范围自动修正 |
| `ydsz.base.trace.slow-request-threshold` | 3000 | 慢请求阈值（ms） |
| `ydsz.doc.enabled` | false | 是否启用文档功能（默认关闭） |
| `ydsz.doc.production-enabled` | false | 生产环境是否允许访问文档 |
| `ydsz.doc.api-docs-path` | `/v3/api-docs` | OpenAPI 文档 JSON 路径 |
| `ydsz.doc.knife4j-path` | `/doc.html` | Knife4j 文档访问路径 |
| `ydsz.doc.doc-version` | 1.0.0 | 文档版本号 |
| `ydsz.doc.info.title` | `YDSZ API 文档` | 文档标题 |
| `ydsz.doc.info.description` | `YDSZ 公共框架 API 文档` | 文档描述 |
| `ydsz.doc.info.version` | 1.0.0 | API 版本 |
| `ydsz.doc.groups` | `[]` | 分组配置列表（空时单分组模式） |
| `ydsz.doc.export.enabled` | true | 是否启用文档导出 |
| `ydsz.doc.export.format` | `json` | 默认导出格式（json/yaml/html/markdown） |
| `ydsz.doc.export.output-dir` | `./api-docs` | 导出目录 |
| `ydsz.doc.basic-auth.enabled` | true | 是否启用 Basic 认证 |
| `ydsz.doc.basic-auth.username` | - | API 文档访问用户名（建议 `${DOC_USERNAME}`） |
| `ydsz.doc.basic-auth.password` | - | API 文档访问密码（建议 `${DOC_PASSWORD}` 或 Jasypt 加密） |

## 使用示例

### 1. 时区自定义

```yaml
ydsz:
  base:
    timezone: America/New_York    # 切换为纽约时区
```

### 2. 安全响应头配置

```yaml
ydsz:
  base:
    security-headers:
      enabled: true
      frame-options: SAMEORIGIN   # 允许同源嵌入
      csp: "default-src 'self'; script-src 'self' 'unsafe-inline'; img-src 'self' data:"
      excludes:
        - /actuator/**
        - /error
```

### 3. 开启 OpenAPI 文档（开发环境）

```yaml
ydsz:
  doc:
    enabled: true
    info:
      title: 我的应用 API 文档
      description: 业务模块接口说明
      version: 1.0.0
    groups:
      - name: user
        title: 用户服务
        packages:
          - com.njydsz.user.controller
      - name: order
        title: 订单服务
        paths:
          - /api/v1/order/**
```

### 4. 生产环境文档访问控制

```yaml
ydsz:
  doc:
    enabled: true
    production-enabled: true      # 显式开启生产环境访问
    basic-auth:
      enabled: true
      username: ${DOC_USERNAME}    # 通过环境变量注入
      password: ${DOC_PASSWORD}    # 通过环境变量注入或 Jasypt 加密
```

### 5. 自定义文档导出器

```java
import com.njydsz.common.base.exporter.AbstractDocExporter;
import com.njydsz.common.base.config.DocProperties;
import org.springframework.stereotype.Component;

@Component
public class PdfDocExporter extends AbstractDocExporter {

    public PdfDocExporter(DocProperties docProperties) {
        super(docProperties);
    }

    @Override
    protected String generateHtmlContent(ApiDocInfo docInfo, String apiDocs) {
        // 自定义 HTML 模板
        return "<html>...</html>";
    }

    @Override
    protected String generateMarkdownContent(String apiDocs) {
        // 自定义 Markdown 结构
        return "# API 文档\n...";
    }
}
```

### 6. 自定义模块指标

```java
import com.njydsz.common.base.metrics.AbstractMetricsHolder;
import io.micrometer.core.instrument.MeterRegistry;

// 静态方法注册指标（计数器/计时器），自动管理命名与实例缓存
AbstractMetricsHolder.registerCounter(registry, "ydsz_flow_", "eval_total");
AbstractMetricsHolder.registerTimer(registry, "ydsz_flow_", "eval_duration_ms");
AbstractMetricsHolder.recordDuration(registry, "ydsz_flow_", "eval_duration_ms", durationMs);
```

## SPI 扩展点

| SPI 接口 | 用途 | 实现方 |
|---|---|---|
| `DocExporter` | 文档导出器 SPI，支持 HTML/Markdown/YAML/JSON 多种格式扩展 | 框架内置 `DefaultDocExporter`、`MarkdownDocExporter`，业务可扩展 |
| `RequestIdResolver` | 请求 ID 解析器接口，统一定义 `resolveRequestId` 方法签名，供 Filter 和 Interceptor 共享 | `BaseRequestIdResponseFilter`、`BaseRequestLogInterceptor` 抽象基类实现 |
| `HealthIndicator` | Spring Boot 健康指标接口，base 模块提供 `BaseHealthIndicator` 实现 | 框架内置，业务可覆盖 |

### 抽象扩展基类（子模块通过继承实现差异化）

| 抽象基类 | 扩展点 | 子模块实现 |
|---|---|---|
| `BaseMvcConfiguration` | MVC 配置（CORS、拦截器注册） | `common-web`（`WebMvcConfiguration`）、`common-app`（`AppMvcConfiguration`） |
| `BaseCorsProperties` | CORS 配置前缀 | `common-web`（`ydsz.web.cors`）、`common-app`（`ydsz.app.cors`） |
| `BaseTraceProperties` | Trace 配置前缀 | `common-web`（`ydsz.web.trace`）、`common-app`（`ydsz.app.trace`） |
| `BaseTimezoneConfiguration` | 时区配置 | `common-web`、`common-app`（`AppTimezoneConfiguration`） |
| `BaseI18nConfiguration` | i18n 资源 basename | `common-web`、`common-app`（basename=`i18n/app-messages`） |
| `BaseOpenApiConfiguration` | OpenAPI 文档标题/描述 | `common-web`、`common-app`（标题=`YDSZ App API 文档`） |
| `BaseGlobalResponseAdvice` | String 返回值包装差异 | `common-web`（`success(msg)`）、`common-app`（`successMsg(msg)`） |
| `AbstractContentCachingFilter` | 请求体缓存容量 | `common-web`、`common-app`（可配置 2MB） |
| `BaseRequestIdResponseFilter` | 请求 ID 解析逻辑 | `common-web`、`common-app`（优先 `RequestHolder`，兜底 `RequestIdGenerator`） |
| `BaseRequestLogInterceptor` | 请求 ID 解析与日志实例 | `common-web`、`common-app` |
| `BaseAuthInfo` | 服务类型编码 | `common-web`（"WEB"）、`common-app`（`ServiceType.APP_SERVICE`） |
| `AbstractMetricsHolder` | 模块指标工具（静态方法） | 各业务模块（如前缀=`ydsz_flow_`） |
| `AbstractDocExporter` | HTML/Markdown 内容生成 | `DefaultDocExporter`、`MarkdownDocExporter` |

## 健康检查

| 端点 | 说明 | 触发条件 |
|---|---|---|
| `/actuator/health/base` | base 模块健康指标，报告时区、安全响应头、文档功能状态 | `spring-boot-health` 在类路径 + `ydsz.base.enabled=true` |
| `/actuator/config-registry` | 配置注册端点，查看所有 `ydsz.*` 配置项 | `spring-boot-actuator` 在类路径 + 端点暴露配置 |
| `/actuator/config-registry/{prefix}` | 查看指定前缀下的配置项 | 同上 |

`BaseHealthIndicator` 暴露信息：

- `timezone` — 当前 JVM 时区
- `timezone.expected` — 期望时区（`ydsz.base.timezone`）
- `timezone.warning` — 期望时区与实际时区不一致时输出警告
- `securityHeaders.enabled` / `frameOptions` / `csp` — 安全响应头状态
- `doc.enabled` / `productionEnabled` / `basicAuth.enabled` / `apiDocsPath` / `knife4jPath` — 文档功能状态

降级判定：

- 安全响应头启用但 `frameOptions` 为空 → DOWN（`warning=安全响应头已启用但 frameOptions 为空`）
- 生产环境文档启用但 Basic 认证未开启 → DOWN（`warning=生产环境文档已启用但 Basic 认证未开启`）

## 注意事项

1. **抽象基类设计**：本模块以抽象基类 / 接口为主（MVC 配置类、CORS/时区/I18n 等），子模块（`common-web`/`common-app`）通过继承并提供具体 `@ConfigurationProperties` 前缀实现差异化装配；同时内置若干可直接使用的具体组件（`TraceFilter` / `SecurityHeadersFilter` / `RequestContextCleanupFilter` / `ConfigRegistryEndpoint` / `DocExporter` 系列 / 健康指示器 / 幂等与限流拦截器）。
2. **`SecurityHeadersFilter` 兜底机制**：Bean 名统一为 `securityHeaderFilter`，通过 `@ConditionalOnMissingBean(name="securityHeaderFilter")` 保证当项目中已存在 web/app/safe 模块注册的同名安全头过滤器时，本兜底实现自动退出，避免重复注册。
3. **`TraceFilter` MDC 清理策略**：本 Filter 不在 finally 中清理 MDC，统一由 `RequestContextCleanupFilter`（`LOWEST_PRECEDENCE`，最外层 Filter）在请求结束时调用 `MDC.clear()` 清理，确保后续 Filter 的后处理日志仍能使用 traceId。
4. **文档功能默认关闭**：出于安全考虑，`ydsz.doc.enabled` 默认为 `false`。生产环境建议保持关闭，或配合 `ydsz.doc.production-enabled=true` + `ydsz.doc.basic-auth.enabled=true` 进行认证保护。
5. **CORS 安全校验**：`BaseCorsProperties.validateSecurity()` 在启动时检测不安全组合（`allowCredentials=true` 且 `*`、来源为空、过度开放），输出 WARN 日志。生产环境建议显式指定允许的域名、方法、头。
6. **`ConfigRegistryEndpoint` 安全**：此端点暴露所有 `ydsz.*` 配置信息，生产环境应通过 `management.endpoint.config-registry.exposure` 控制访问权限，建议仅限内网访问。
7. **横切点顺序约定**：所有 Filter 的 `order` 值定义在 `FilterOrder` 常量类中，Interceptor 的 `order` 值定义在 `InterceptorOrder` 中，Advice 的 `order` 值定义在 `AdviceOrder` 中；修改任何数字前请先更新 `docs/BASE_INTERCEPTOR_ORDER.md` 文档。
8. **`BaseAuthFilter` 清理职责**：`RequestHolder.remove()` 由 `BaseAuthFilter.doFilterInternal()` 的 finally 块负责清理。
9. **`AbstractMetricsHolder` 指标工具**：通过静态方法注册指标，传入模块前缀（如 `ydsz_flow_`）自动统一命名，避免各模块硬编码重复字符串。

## 变更记录

- **v1.0.0**（2026-08-02）：按 ydsz-common-jdbc 9 章节标准重构 README；补全横切点执行顺序表、SPI 扩展点（含抽象基类扩展点）、健康检查端点、注意事项；统一版本号为 1.0.0
