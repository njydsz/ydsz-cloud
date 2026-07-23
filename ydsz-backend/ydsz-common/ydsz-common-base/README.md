# ydsz-common-base

YDSZ HTTP 公共基座 — CORS、时区、I18n、安全头、TraceId、请求日志、全局响应包装、OpenAPI/Knife4j 文档、内容缓存过滤器、健康检查。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L6 应用层 |
| **类型** | 公共依赖库（不独立部署） |
| **源文件数** | 29 |
| **被继承** | `common-web` 和 `common-app` 继承此模块 |

## 核心能力

### MVC 配置

| 类 | 说明 |
|---|---|
| `BaseMvcConfiguration` | MVC 配置（拦截器注册 / CORS 过滤器） |
| `BaseAutoConfiguration` | 总自动配置（TraceFilter / SecurityHeadersFilter / HealthIndicator） |
| `BaseTimezoneConfiguration` | 时区配置（可通过 `ydsz.base.timezone` 自定义，默认 `Asia/Shanghai`） |
| `BaseI18nConfiguration` | 国际化配置（MessageSource + LocaleResolver） |
| `BaseCorsProperties` | CORS 配置属性（含安全校验） |
| `BaseTraceProperties` | Trace 配置属性（含慢请求阈值） |
| `BaseSecurityHeadersProperties` | 安全头配置属性 |

### 过滤器链

| 类 | 说明 |
|---|---|
| `TraceFilter` | TraceId 过滤器（生成 / 传递 / MDC 设置 / 安全校验） |
| `SecurityHeadersFilter` | 安全响应头过滤器 |
| `BaseRequestIdResponseFilter` | 请求 ID 响应过滤器 |
| `RequestContextCleanupFilter` | 请求上下文清理过滤器 |
| `AbstractContentCachingFilter` | 请求 Body 缓存过滤器（允许重复读取） |

### 拦截器

| 类 | 说明 |
|---|---|
| `BaseRequestLogInterceptor` | 请求日志拦截器（结构化日志 / 慢请求标记） |
| `BaseHttpInterceptor` | HTTP 通用拦截器 |
| `RequestIdResolver` | 请求 ID 解析器接口（Filter / Interceptor 共享） |

### 全局响应

| 类 | 说明 |
|---|---|
| `BaseGlobalResponseAdvice` | 全局响应包装（跳过 void/ResponseEntity/Resource） |

### 健康检查

| 类 | 说明 |
|---|---|
| `BaseHealthIndicator` | 健康指标（时区/安全头/文档状态报告） |

### 文档配置

| 类 | 说明 |
|---|---|
| `BaseOpenApiConfiguration` | OpenAPI 3.0 配置基类 |
| `OpenApiAutoConfiguration` | OpenAPI 自动配置（多分组支持） |
| `Knife4jAutoConfiguration` | Knife4j UI 自动配置 |
| `DocAutoConfiguration` / `DocProperties` | 文档自动配置 / 属性 |
| `DocSecurityConfiguration` | 文档安全配置（Basic 认证 + 生产环境检测） |

### 文档导出

| 类 | 说明 |
|---|---|
| `AbstractDocExporter` | 文档导出器抽象基类（公共逻辑 + 安全类型转换） |
| `DefaultDocExporter` | 默认导出器（简单格式） |
| `MarkdownDocExporter` | Markdown 增强导出器（结构化 Markdown） |
| `DocExporter` | 文档导出器 SPI 接口 |
| `DocConstants` | 文档常量 |
| `BaseFilterOrders` | 过滤器顺序常量 |

## 配置项

```yaml
ydsz:
  base:
    enabled: true                          # 是否启用 base 模块
    timezone: Asia/Shanghai                # JVM 默认时区
    security-headers:
      enabled: true                        # 是否启用安全响应头
      xss-protection: "1; mode=block"
      content-type-options: nosniff
      frame-options: DENY
      hsts: "max-age=31536000; includeSubDomains"
      csp: "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'"
      referrer-policy: strict-origin-when-cross-origin
      excludes: []                         # 排除路径
    trace:
      enabled: true                        # 是否启用链路追踪
      response-header-enabled: true        # 是否在响应头输出请求 ID
      request-id-header-name: X-Request-Id
      request-log-enabled: true            # 是否启用请求日志
      log-level: INFO                      # 日志级别（INFO/DEBUG）
      sampling-rate: 1.0                   # 采样率 [0.0, 1.0]
      slow-request-threshold: 3000         # 慢请求阈值（ms）
      log-request-body: false
      log-response-body: false
  doc:
    enabled: false                         # 是否启用文档功能（默认关闭）
    production-enabled: false              # 生产环境是否允许访问
    api-docs-path: /v3/api-docs
    knife4j-path: /doc.html
    doc-version: 1.0.0                     # 文档版本
    info:
      title: "YDSZ API 文档"
      description: "YDSZ 公共框架 API 文档"
      version: 1.0.0
    groups: []                             # 分组配置
    export:
      enabled: true
      format: json
      output-dir: ./api-docs
    basic-auth:
      enabled: true
      username: admin                      # 建议使用环境变量 ${DOC_USERNAME}
      password: ${DOC_PASSWORD}            # 建议使用环境变量引用或 Jasypt 加密
```

## 自动配置

| 配置类 | 激活条件 |
|---|---|
| `BaseAutoConfiguration` | `ydsz.base.enabled=true`（默认 true） |
| `DocAutoConfiguration` | `ydsz.doc.enabled=true`（默认 false） |
| `DocSecurityConfiguration` | `ydsz.doc.enabled=true`（默认 false） |
| `OpenApiAutoConfiguration` | `ydsz.doc.enabled=true` + springdoc 可用 |
| `Knife4jAutoConfiguration` | `ydsz.doc.enabled=true` + knife4j 可用 |

## 依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-base</artifactId>
</dependency>
```
