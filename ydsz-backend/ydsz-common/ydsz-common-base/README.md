# ydsz-common-base

PMIS HTTP 公共基座 — CORS、时区、I18n、安全头、TraceId、请求日志、全局响应包装、OpenAPI/Knife4j 文档、内容缓存过滤器。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L6 应用层 |
| **类型** | 公共依赖库（不独立部署） |
| **源文件数** | 27 |
| **被继承** | `common-web` 和 `common-app` 继承此模块 |

## 核心能力

### MVC 配置

| 类 | 说明 |
|---|---|
| `BaseMvcConfiguration` | MVC 配置（拦截器注册 / 消息转换器 / 参数解析器） |
| `BaseAutoConfiguration` | 总自动配置 |
| `BaseTimezoneConfiguration` | 时区配置（统一 `Asia/Shanghai`） |
| `BaseI18nConfiguration` | 国际化配置（MessageSource + LocaleResolver） |
| `BaseCorsProperties` | CORS 配置属性 |
| `BaseTraceProperties` | Trace 配置属性 |
| `BaseSecurityHeadersProperties` | 安全头配置属性 |

### 过滤器链

| 类 | 说明 |
|---|---|
| `TraceFilter` | TraceId 过滤器（生成 / 传递 / MDC 设置） |
| `SecurityHeadersFilter` | 安全响应头过滤器 |
| `BaseRequestIdResponseFilter` | 请求 ID 响应过滤器 |
| `RequestContextCleanupFilter` | 请求上下文清理过滤器 |
| `AbstractContentCachingFilter` | 请求 Body 缓存过滤器（允许重复读取） |

### 拦截器

| 类 | 说明 |
|---|---|
| `BaseRequestLogInterceptor` | 请求日志拦截器（结构化日志输出） |
| `BaseHttpInterceptor` | HTTP 通用拦截器 |

### 全局响应

| 类 | 说明 |
|---|---|
| `BaseGlobalResponseAdvice` | 全局响应包装（`@RestControllerAdvice` → 自动包装 `BaseResponse`） |

### 认证基础

| 类 | 说明 |
|---|---|
| `BaseAuthInfo` | 基础认证信息（userId / username / roles / tenantId） |

### 文档配置

| 类 | 说明 |
|---|---|
| `BaseOpenApiConfiguration` | OpenAPI 3.0 配置 |
| `OpenApiAutoConfiguration` | OpenAPI 自动配置 |
| `Knife4jAutoConfiguration` | Knife4j UI 自动配置 |
| `DocAutoConfiguration` / `DocProperties` | 文档自动配置 / 属性 |
| `DocSecurityConfiguration` | 文档安全配置（生产环境密码保护） |

### 文档导出

| 类 | 说明 |
|---|---|
| `DocExporter` / `DefaultDocExporter` / `MarkdownDocExporter` | 文档导出接口与实现（Markdown 格式） |
| `DocConstants` | 文档常量 |
| `BaseFilterOrders` | 过滤器顺序常量 |

## 配置项

```yaml
pmis:
  base:
    cors:
      enabled: true
      allowed-origins: ["https://pmis.njydsz.com"]
      allowed-methods: [GET, POST, PUT, DELETE, OPTIONS]
      allowed-headers: ["*"]
      allow-credentials: true
      max-age: 3600
    timezone: Asia/Shanghai
    trace:
      header-name: X-Trace-Id
      generate-if-missing: true
    security-headers:
      content-security-policy: "default-src 'self'"
      x-frame-options: DENY
      x-content-type-options: nosniff
    i18n:
      basename: messages
      default-locale: zh_CN
    doc:
      enabled: true
      title: "PMIS API"
      version: "1.0.0"
      knife4j:
        enabled: true
        basic-auth:
          enabled: false    # 生产环境开启
```

## 自动配置

| 配置类 | 激活条件 |
|---|---|
| `BaseAutoConfiguration` | 总是激活 |
| `OpenApiAutoConfiguration` / `Knife4jAutoConfiguration` | springdoc 可用时激活 |
| `DocAutoConfiguration` | 总是激活 |

## 依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-base</artifactId>
</dependency>
```
