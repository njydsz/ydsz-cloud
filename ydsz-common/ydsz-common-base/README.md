# ydsz-common-base

> YDSZ Web/App 公共 HTTP 基座模块（L6 应用层）

提供 CORS、时区、I18n、安全响应头、TraceId、请求日志、上下文清理、全局响应包装、OpenAPI/Knife4j 文档、文档导出、健康检查、模块指标基类、限流与幂等等共享能力，是 `common-web` 与 `common-app` 两个应用层入口的统一抽象基座。本模块的所有 MVC 配置类、Properties、Filter、Interceptor、Advice 均为抽象基类或接口，子模块通过继承并提供具体 `@ConfigurationProperties` 前缀实现差异化装配。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L6 应用层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供 Web/App 共享的 HTTP 基座：CORS、时区、I18n、安全头、请求体大小限制、TraceId、请求日志、上下文清理、全局响应包装、OpenAPI/Knife4j 文档、文档导出、健康检查、模块指标基类、限流与幂等 |
| **依赖** | common-core、common-util、common-exception、common-json、common-auth、common-safe；可选：spring-boot-actuator、spring-boot-health、springdoc-openapi、knife4j、micrometer-core |
| **版本** | 26.09.01-SNAPSHOT |

## 核心能力

### 1. MVC 配置基类

| 类 | 说明 |
|---|---|
| `BaseMvcConfiguration` | MVC 配置抽象基类（实现 `WebMvcConfigurer`），子类提供具体 `BaseCorsProperties` 实现并注册拦截器 / 过滤器 Bean；统一注册 `CorsFilter` 并在启动时执行 CORS 安全校验 |
| `BaseCorsProperties` | CORS 配置属性抽象基类，含 `validateSecurity()` 检测不安全组合（allowCredentials + `*`、来源为空、过度开放） |
| `BaseTraceProperties` | 请求追踪/日志配置属性抽象基类，提供链路追踪、请求日志、采样率、慢请求阈值等配置；`getSamplingRate()` 自动修正到 `[0.0, 1.0]` |
| `YdszSecurityHeadersProperties` | 安全响应头配置属性（`ydsz.base.security-headers`），控制 X-Content-Type-Options / X-Frame-Options / X-XSS-Protection / HSTS / CSP / Referrer-Policy |
| `YdszRequestProperties` | 请求体大小限制配置属性（`ydsz.base.request`），控制最大请求体大小（默认 10MB），超限返回 413 |
| `ConditionalOnPlatform` | 平台条件注解，支持按平台模式（WEB / APP）条件装配 |
| `PlatformCondition` / `PlatformMode` | 平台条件判断实现与模式枚举（WEB / APP，默认 WEB） |

### 2. 时区与国际化

| 类 | 说明 |
|---|---|
| `BaseTimezoneConfiguration` | 时区配置抽象基类，`@PostConstruct` 强制设置 JVM 默认时区（`Asia/Shanghai`，UTC+8）；通过 `ydsz.base.timezone` 自定义 |
| `BaseI18nConfiguration` | 国际化配置抽象基类，子类覆盖 `getBasenames()` 接入不同资源文件；默认 `Accept-Language` 解析，支持 `zh_CN` 和 `en_US`，缺失 key 回退到 code |
| `SpringMessageResolver` | Spring MessageSource 适配器，桥接到框架统一的消息体系 |
| `MessageResolverRegistry` | 消息解析器注册中心（按 Locale 路由） |
| `MessageResolverHolder` | 消息解析器静态持有者（供非 Spring 注入场景便捷访问 i18n 消息） |

### 3. 过滤器链

| 类 | 说明 |
|---|---|
| `TraceFilter` | 链路追踪过滤器，生成或提取 traceId 注入 MDC 和 RequestContext；对传入 traceId 做长度和字符集校验，防止日志注入 |
| `SecurityHeadersFilter` | 安全响应头过滤器（base 模块兜底实现），添加 CSP / HSTS / X-Frame-Options 等头部；通过 `@ConditionalOnMissingBean(name="securityHeaderFilter")` 与 web/app/safe 模块互斥 |
| `RequestBodySizeLimitFilter` | 请求体大小限制过滤器，Controller 之前检查 Content-Length，超限直接返回 413 |
| `RequestContextCleanupFilter` | 请求上下文清理过滤器，`finally` 块清理 RequestContext 和 MDC，防止 ThreadLocal 泄漏；以 `LOWEST_PRECEDENCE` 注册 |
| `AbstractContentCachingFilter` | 请求体缓存过滤器抽象基类，包装请求为 `ContentCachingRequestWrapper` 支持多次读取；跳过 multipart 请求 |
| `BaseRequestIdResponseFilter` | 请求 ID 响应头过滤器抽象基类，子类覆盖 `resolveRequestId()` 提供不同 ID 来源 |

### 4. 拦截器

| 类 | 说明 |
|---|---|
| `BaseRequestLogInterceptor` | 请求日志拦截器抽象基类，`preHandle` 输出入口日志（method/uri/ip/ua），`afterCompletion` 输出完成日志（status/time/error），慢请求升级为 WARN；支持采样率 |
| `RequestIdResolver` | 请求 ID 解析器接口，统一 `resolveRequestId(HttpServletRequest)` 方法签名 |

### 5. 全局响应包装

| 类 | 说明 |
|---|---|
| `BaseGlobalResponseAdvice` | 全局响应包装抽象基类（`ResponseBodyAdvice<Object>`），自动将非 `YdszResponse` 包装为 `YdszResponse.success()`；跳过 YdszResponse / void / ResponseEntity / HttpEntity / Resource / 流式类型；子类覆盖 `wrapStringBody()` 处理 String 差异 |

### 6. OpenAPI 文档配置

| 类 | 说明 |
|---|---|
| `BaseOpenApiConfiguration` | OpenAPI 文档配置抽象基类，子类覆盖 `getTitle()` / `getDescription()`；默认注册公共请求头、JWT Bearer Token 认证方案 |
| `OpenApiAutoConfiguration` | OpenAPI 自动配置，`@ConditionalOnClass(SpringDocConfiguration)` + `ydsz.doc.enabled=true`；支持单分组和多分组模式 |
| `Knife4jAutoConfiguration` | Knife4j 增强 UI 自动配置，提供离线文档导出、全局参数配置等 |
| `DocAutoConfiguration` | 文档模块入口配置，`@Import` 激活 `OpenApiAutoConfiguration` / `Knife4jAutoConfiguration` / `DefaultDocExporter` / `MarkdownDocExporter` |
| `DocProperties` | 文档配置属性（`ydsz.doc`），含 enabled / productionEnabled / basicAuth / apiDocsPath / knife4jPath / info 等 |
| `DocSecurityConfiguration` | 文档安全配置，生产环境通过 `ydsz.doc.production-enabled=true` 启用 Basic 认证；构造阶段检测 prod Profile 输出安全告警 |

### 7. 文档导出

| 类 | 说明 |
|---|---|
| `DocExporter` | 文档导出器 SPI 接口，定义 exportToHtml / exportToMarkdown / exportToYaml / exportToJson 等方法 |
| `AbstractDocExporter` | 文档导出器抽象基类，封装四种格式公共逻辑；含安全类型转换工具 |
| `DefaultDocExporter` | 默认导出器（简单格式） |
| `MarkdownDocExporter` | Markdown 增强导出器 |

### 8. 限流与幂等

| 类 | 说明 |
|---|---|
| `RateLimiter` / `InMemoryRateLimiter` | 限流器接口与内存实现（令牌桶算法） |
| `RateLimit` / `RateLimitInterceptor` | 限流注解与拦截器（按资源键限流） |
| `RateLimitAutoConfiguration` | 限流自动配置 |
| `IdempotentStore` / `InMemoryIdempotentStore` | 幂等存储接口与内存实现 |
| `Idempotent` / `IdempotentInterceptor` | 幂等注解与拦截器（自动去重） |
| `IdempotentException` | 幂等异常 |
| `IdempotentAutoConfiguration` | 幂等自动配置 |

### 9. 健康检查与 Actuator

| 类 | 说明 |
|---|---|
| `YdszHealthIndicator` | 健康指标，报告时区、安全响应头、文档功能、JVM 堆内存状态；安全头 frameOptions 为空或生产环境文档未配置 Basic 认证时标记 DOWN |
| `CoreHealthIndicator` | 核心模块健康指标（TraceId + i18n 状态） |
| `ConfigRegistryEndpoint` | Actuator 端点（`config-registry`），暴露 `GET /actuator/config-registry` 查看所有 `ydsz.*` 配置 |

### 10. 模块指标基类

| 类 | 说明 |
|---|---|
| `AbstractMetricsHolder` | 模块指标工具类，提供静态方法 `registerCounter` / `registerTimer` / `recordDuration`，统一管理 Micrometer 指标命名与实例缓存 |

### 11. 常量与上下文基类

| 类 | 说明 |
|---|---|
| `FilterOrder` | Servlet Filter 执行顺序常量 |
| `InterceptorOrder` | Spring MVC Interceptor 执行顺序常量 |
| `AdviceOrder` | ControllerAdvice 执行顺序常量 |
| `DocConstants` | OpenAPI 文档常量 |
| `HttpHeaderConstants` | HTTP 头部常量 |
| `BaseAuthInfo` | 认证上下文信息抽象基类 |

### 12. API 版本控制基类

| 类 | 说明 |
|---|---|
| `@ApiVersion` (api 包) | API 版本注解基类，可标注在 Controller 类或方法上 |
| `ApiVersionOpenApiCustomizer` | API 版本 OpenAPI 定制器，将版本信息注入文档 |
| `ApiVersionResolver` | API 版本解析器基类 |

### 横切点执行顺序

Filter 链（Order 由小到大）：

| 顺序 | Filter | 说明 |
|---|---|---|
| `HIGHEST_PRECEDENCE + 10` | `TraceFilter` | 生成/透传 traceId |
| `HIGHEST_PRECEDENCE + 5` | `RequestBodySizeLimitFilter` | 请求体大小限制 |
| `FilterOrder.SECURITY_HEADER_FILTER` | `SecurityHeadersFilter` | 安全响应头 |
| `LOWEST_PRECEDENCE` | `RequestContextCleanupFilter` | 清理 TTL / MDC |

Interceptor 与 Advice 顺序：

| 顺序 | 组件 | 说明 |
|---|---|---|
| 10 | `BaseRequestLogInterceptor` | 请求/响应日志 |
| 0 | `GlobalResponseAdvice` | 统一响应包装（最先） |

## 接入方式

### 1. POM 引入依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-base</artifactId>
</dependency>
```

> `spring-boot-actuator`、`spring-boot-health`、`springdoc-openapi`、`knife4j`、`micrometer-core` 均为 `<optional>true</optional>`，业务方按需引入对应 starter。

### 2. 启用方式

```yaml
ydsz:
  base:
    enabled: true                # 启用 base 模块（默认 true）
    timezone: Asia/Shanghai      # JVM 默认时区
```

### 3. 继承抽象基类（业务方直接使用 base 时）

业务方若不引入 `common-web` 或 `common-app`，需自行继承抽象基类并提供具体 `@ConfigurationProperties` 前缀：

```java
@ConfigurationProperties(prefix = "ydsz.custom.cors")
public class CustomCorsProperties extends BaseCorsProperties {
}

@ConfigurationProperties(prefix = "ydsz.custom.trace")
public class CustomTraceProperties extends BaseTraceProperties {
}
```

## 配置项

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.base.enabled` | `true` | 是否启用 base 模块 |
| `ydsz.base.timezone` | `Asia/Shanghai` | JVM 默认时区 |
| `ydsz.base.security-headers.enabled` | `true` | 是否启用安全响应头 |
| `ydsz.base.security-headers.xss-protection` | `1; mode=block` | XSS 防护头部 |
| `ydsz.base.security-headers.content-type-options` | `nosniff` | 内容类型选项头部 |
| `ydsz.base.security-headers.frame-options` | `DENY` | 帧选项头部 |
| `ydsz.base.security-headers.hsts` | `max-age=31536000; includeSubDomains` | 严格传输安全头部 |
| `ydsz.base.security-headers.csp` | `default-src 'self'...` | 内容安全策略头部 |
| `ydsz.base.security-headers.referrer-policy` | `strict-origin-when-cross-origin` | 引用策略头部 |
| `ydsz.base.security-headers.excludes` | `[]` | 排除路径列表 |
| `ydsz.base.request.enabled` | `true` | 是否启用请求体大小限制 |
| `ydsz.base.request.max-body-size` | `10485760`（10MB） | 最大请求体大小 |
| `ydsz.base.request.configure-container` | `true` | 是否自动配置嵌入式容器 maxPostSize |
| `ydsz.base.trace.enabled` | `true` | 是否启用链路追踪（子模块前缀为 `ydsz.web.trace` / `ydsz.app.trace`） |
| `ydsz.base.trace.response-header-enabled` | `true` | 是否在响应头输出请求 ID |
| `ydsz.base.trace.request-id-header-name` | `X-Request-Id` | 请求 ID 响应头名称 |
| `ydsz.base.trace.request-log-enabled` | `true` | 是否启用请求日志 |
| `ydsz.base.trace.request-log-format` | `detailed` | 请求日志格式（simple/detailed） |
| `ydsz.base.trace.log-request-params` | `true` | 是否记录请求参数 |
| `ydsz.base.trace.log-request-body` | `false` | 是否记录请求体 |
| `ydsz.base.trace.log-response-body` | `false` | 是否记录响应体 |
| `ydsz.base.trace.log-level` | `INFO` | 日志级别（INFO/DEBUG） |
| `ydsz.base.trace.sampling-rate` | `1.0` | 日志采样率 [0.0, 1.0] |
| `ydsz.base.trace.slow-request-threshold` | `3000` | 慢请求阈值（ms） |
| `ydsz.doc.enabled` | `false` | 是否启用文档功能（默认关闭） |
| `ydsz.doc.production-enabled` | `false` | 生产环境是否允许访问文档 |
| `ydsz.doc.api-docs-path` | `/v3/api-docs` | OpenAPI 文档 JSON 路径 |
| `ydsz.doc.knife4j-path` | `/doc.html` | Knife4j 文档访问路径 |
| `ydsz.doc.doc-version` | `26.09.01` | 文档版本号 |
| `ydsz.doc.info.title` | `API Documentation` | 文档标题 |
| `ydsz.doc.info.description` | `` | 文档描述 |
| `ydsz.doc.groups` | `[]` | 分组配置列表 |
| `ydsz.doc.export.enabled` | `true` | 是否启用文档导出 |
| `ydsz.doc.export.format` | `json` | 默认导出格式 |
| `ydsz.doc.basic-auth.enabled` | `false` | 是否启用 Basic 认证 |
| `ydsz.doc.basic-auth.username` | `admin` | 用户名 |
| `ydsz.doc.basic-auth.password` | `admin123` | 密码 |

## SPI 扩展点

| SPI 接口 | 用途 | 实现方 |
|---|---|---|
| `DocExporter` | 文档导出器 SPI，支持 HTML/Markdown/YAML/JSON 多种格式 | 内置 `DefaultDocExporter`、`MarkdownDocExporter`，业务可扩展 |
| `RequestIdResolver` | 请求 ID 解析器接口 | `BaseRequestIdResponseFilter`、`BaseRequestLogInterceptor` 实现 |
| `HealthIndicator` | Spring Boot 健康指标接口 | 内置 `YdszHealthIndicator`、`CoreHealthIndicator` |
| `RateLimiter` | 限流器接口 | 内置 `InMemoryRateLimiter`（令牌桶） |
| `IdempotentStore` | 幂等存储接口 | 内置 `InMemoryIdempotentStore` |

### 抽象扩展基类（子模块通过继承实现差异化）

| 抽象基类 | 扩展点 | 子模块实现 |
|---|---|---|
| `BaseMvcConfiguration` | MVC 配置（CORS、拦截器注册） | `common-web`（`WebMvcConfiguration`）、`common-app`（`AppMvcConfiguration`） |
| `BaseCorsProperties` | CORS 配置前缀 | `common-web`（`ydsz.web.cors`）、`common-app`（`ydsz.app.cors`） |
| `BaseTraceProperties` | Trace 配置前缀 | `common-web`（`ydsz.web.trace`）、`common-app`（`ydsz.app.trace`） |
| `BaseTimezoneConfiguration` | 时区配置 | `common-web`、`common-app` |
| `BaseI18nConfiguration` | i18n 资源 basename | `common-web`、`common-app`（`i18n/app-messages`） |
| `BaseOpenApiConfiguration` | OpenAPI 文档标题/描述 | `common-web`、`common-app` |
| `BaseGlobalResponseAdvice` | String 返回值包装差异 | `common-web`（`success(msg)`）、`common-app`（`successMsg(msg)`） |
| `AbstractContentCachingFilter` | 请求体缓存容量 | `common-web`、`common-app`（默认 2MB） |
| `BaseRequestIdResponseFilter` | 请求 ID 解析逻辑 | `common-web`、`common-app` |
| `BaseRequestLogInterceptor` | 请求 ID 解析与日志实例 | `common-web`、`common-app` |
| `BaseAuthInfo` | 服务类型编码 | `common-web`（"WEB"）、`common-app`（`ServiceType.APP_SERVICE`） |
| `AbstractMetricsHolder` | 模块指标工具 | 各业务模块 |
| `AbstractDocExporter` | HTML/Markdown 内容生成 | `DefaultDocExporter`、`MarkdownDocExporter` |

## 健康检查

| 端点 | 说明 | 触发条件 |
|---|---|---|
| `/actuator/health` | base 模块健康指标（`YdszHealthIndicator`），报告时区、安全响应头、文档功能、JVM 堆内存 | `spring-boot-health` + `ydsz.base.enabled=true` |
| `/actuator/health` | core 模块健康指标（`CoreHealthIndicator`），TraceId + i18n 状态 | `spring-boot-health` |
| `/actuator/config-registry` | 配置注册端点，查看所有 `ydsz.*` 配置项 | `spring-boot-actuator` |
| `/actuator/config-registry/{prefix}` | 查看指定前缀下的配置项 | `spring-boot-actuator` |

降级判定：

- 安全响应头启用但 `frameOptions` 为空 → DOWN
- 生产环境文档启用但 Basic 认证未开启 → DOWN
- 堆内存使用率超过 95% → DOWN（OOM 风险）

## 自动配置类

| 配置类 | 激活条件 |
|---|---|
| `YdszAutoConfiguration` | `@ConditionalOnWebApplication` + `ydsz.base.enabled=true`（默认 true）；注册 `RequestBodySizeLimitFilter` / `TraceFilter` / `SecurityHeadersFilter`（兜底）/ `RequestContextCleanupFilter` / `YdszHealthIndicator` / `CoreHealthIndicator` |
| `DocAutoConfiguration` | `ydsz.doc.enabled=true`（默认 false）；`@Import` 激活子配置类 |
| `DocSecurityConfiguration` | `ydsz.doc.enabled=true`；Servlet Web 应用 |
| `OpenApiAutoConfiguration` | `@ConditionalOnClass(SpringDocConfiguration)` + `ydsz.doc.enabled=true` |
| `Knife4jAutoConfiguration` | `@ConditionalOnClass(Knife4jOpenApiCustomizer)` + `ydsz.doc.enabled=true` |

注册入口：`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

```
com.njydsz.common.base.config.YdszAutoConfiguration
com.njydsz.common.base.config.DocAutoConfiguration
com.njydsz.common.base.config.DocSecurityConfiguration
com.njydsz.common.base.config.OpenApiAutoConfiguration
com.njydsz.common.base.config.Knife4jAutoConfiguration
com.njydsz.common.base.i18n.I18nAutoConfiguration
```

## 注意事项

1. **抽象基类设计**：本模块以抽象基类 / 接口为主，子模块通过继承实现差异化装配；同时内置可直接使用的具体组件（`TraceFilter` / `SecurityHeadersFilter` / `RequestContextCleanupFilter` 等）。
2. **`SecurityHeadersFilter` 兜底机制**：Bean 名统一为 `securityHeaderFilter`，通过 `@ConditionalOnMissingBean(name="securityHeaderFilter")` 保证互斥。
3. **`TraceFilter` MDC 清理策略**：不在 finally 中清理 MDC，统一由 `RequestContextCleanupFilter`（`LOWEST_PRECEDENCE`）在请求结束时调用 `MDC.clear()`。
4. **文档功能默认关闭**：`ydsz.doc.enabled` 默认为 `false`；生产环境或配合 `production-enabled=true` + `basic-auth.enabled=true`。
5. **CORS 安全校验**：`BaseCorsProperties.validateSecurity()` 在启动时检测不安全组合，输出 WARN 日志。
6. **`ConfigRegistryEndpoint` 安全**：暴露所有 `ydsz.*` 配置，生产环境应通过 `management.endpoint.config-registry.exposure` 控制访问。
7. **横切点顺序约定**：Filter / Interceptor / Advice 执行顺序值分别定义在 `FilterOrder` / `InterceptorOrder` / `AdviceOrder` 中；修改前请先更新 `docs/BASE_INTERCEPTOR_ORDER.md`。

## 变更记录

- **26.09.01**（2026-08-17）：补全 `YdszSecurityHeadersProperties` / `YdszRequestProperties` / `RequestBodySizeLimitFilter` 文档；补全 api 包（`ApiVersion` / `ApiVersionOpenApiCustomizer` / `ApiVersionResolver`）文档
- **26.09.01**（2026-08-17）：补全限流、幂等、i18n（`SpringMessageResolver` / `MessageResolverRegistry` / `MessageResolverHolder`）、`CoreHealthIndicator` 文档
- **26.09.01**（2026-08-02）：按 ydsz-common-jdbc 9 章节标准重构 README；补全横切点执行顺序表、SPI 扩展点、健康检查端点、注意事项；统一版本号
