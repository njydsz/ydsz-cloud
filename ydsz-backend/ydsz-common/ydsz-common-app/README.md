# ydsz-common-app

> YDSZ 移动端 App 后端服务基座模块（L6 应用层）

继承 `common-base` 的所有抽象能力，叠加 App 认证、请求体缓存、请求追踪、健康检查、指标采集、作用域隔离（`@AppApi`）等移动端特有配置，是移动端 App 后端服务的统一应用层入口。本模块与 `common-web` 是两个**平行**的应用层入口，后端微服务统一使用 `common-web`，`common-app` 仅用于未来移动端项目。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L6 应用层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 继承 `common-base` 提供 App 端特有能力：App 认证、请求体缓存、请求追踪、健康检查、Micrometer 指标采集、`@AppApi` 作用域隔离 |
| **依赖** | `common-base`（传递 common-core、common-util、common-exception、common-json）、common-exception、common-auth、common-safe、common-redis、common-json；可选依赖 spring-boot-actuator、spring-boot-health、micrometer-core、jakarta.validation-api |
| **版本** | 1.0.0 |

## 核心能力

### 1. MVC 核心配置

| 类 | 说明 |
|---|---|
| `AppMvcConfiguration` | App 端 MVC 核心配置（继承 `BaseMvcConfiguration`），`@AutoConfiguration` + `@AutoConfigureBefore({BaseAutoConfiguration.class, SafeConfiguration.class})`；集中注册过滤器链（`AppContentCachingFilter`、`AppAuthFilter`、`AppRequestIdResponseFilter`）、拦截器（`AppRequestLogInterceptor`、`BaseHttpInterceptor`）、`AppAuthHandler` Bean、`AppMetrics`、`AppHealthIndicator`；`@Import({AppGlobalResponseAdvice.class, AppExceptionHandler.class})` 装配作用域限定的 Advice |
| `AppTimezoneConfiguration` | 时区配置（继承 `BaseTimezoneConfiguration`），强制 JVM 默认时区为 `Asia/Shanghai` |
| `AppI18nConfiguration` | 国际化配置（继承 `BaseI18nConfiguration`），basename=`i18n/app-messages`，支持 `app-messages_zh_CN.properties`、`app-messages_en_US.properties` |
| `AppOpenApiConfiguration` | OpenAPI 配置（继承 `BaseOpenApiConfiguration`），标题=`YDSZ App API 文档`，描述带 HTML 样式 |
| `AppCorsProperties` | CORS 配置（继承 `BaseCorsProperties`），前缀 `ydsz.app.cors` |
| `AppTraceProperties` | Trace 配置（继承 `BaseTraceProperties`），前缀 `ydsz.app.trace` |
| `AppContentCacheProperties` | 请求体缓存配置（`ydsz.app.content-cache`），`@Validated` + `@Min(0)` 校验 `maxSize`，默认 2MB |

### 2. App 认证

| 类 | 说明 |
|---|---|
| `AppAuthFilter` | App 认证过滤器（继承 `BaseAuthFilter`），作为移动端请求入口过滤器；`doPreAuth` 生成或复用 RequestId 写入 `RequestHolder`；`resolveAuthInfo` 优先使用 `AuthenticationProvider`，为空时降级到 `AuthHandler`；认证成功/失败均通过 `AuthMetrics` 上报耗时指标；`getLogPrefix` 返回 `【App端】`；`resolveFailureReason` 从异常类型推断失败原因标签（missing_token/invalid_token/expired_token/revoked_token/signature_mismatch/unknown） |
| `AppAuthHandler` | App 认证处理器（继承 `AbstractAuthHandler`），通过模板方法模式仅提供 `AppAuthInfo` 实例创建，解析逻辑由基类统一处理；App 端不依赖浏览器 Cookie，通常基于 `X-App-Token` 等自定义请求头认证 |
| `AppAuthInfo` | App 认证上下文信息（继承 `BaseAuthInfo`），`getServiceTypeCode()` 固定返回 `ServiceType.APP_SERVICE` 的编码，与 Web 端、管理端区分；通过 `RequestHolder` 在请求线程内传递 |

### 3. 过滤器链

| 类 | 说明 |
|---|---|
| `AppContentCachingFilter` | 请求体缓存过滤器（继承 `AbstractContentCachingFilter`），通过 `AppContentCacheProperties` 注入最大缓存大小（字节），默认 2MB；配置值 ≤0 时回退到基类默认值 512KB |
| `AppRequestIdResponseFilter` | 请求 ID 响应头过滤器（继承 `BaseRequestIdResponseFilter`），优先复用鉴权阶段在 `RequestHolder` 中缓存的 RequestId，缺失时调用 `RequestIdGenerator.generateId()` 兜底生成 |
| `AppAuthFilter` | App 认证过滤器（详见 App 认证章节） |

> **API 签名验证**和**安全响应头**由 `ydsz-common-safe` 模块统一提供，通过 `ydsz.safe.api-signature.enabled` 和 `ydsz.safe.security-headers.enabled` 控制启用，本模块不再重复注册。`AppMvcConfiguration` 通过 `@AutoConfigureBefore(SafeConfiguration.class)` 保证装配顺序。

### 4. 拦截器

| 类 | 说明 |
|---|---|
| `AppRequestLogInterceptor` | 请求日志拦截器（继承 `BaseRequestLogInterceptor`，`@Component`），`resolveRequestId` 优先从 `RequestHolder` 获取上游过滤器写入的值，缺失时调用 `RequestIdGenerator.generateId()` 兜底；`getLogger` 返回当前类持有的 `Logger` 实例；由 `AppMvcConfiguration.addInterceptors()` 显式注册，执行顺序 `BaseFilterOrders.INTERCEPTOR_REQUEST_LOG` |
| `BaseHttpInterceptor` | HTTP 通用拦截器（来自 `common-base`），作为拦截器链末端占位拦截器 |

### 5. 全局响应包装与异常处理（作用域隔离）

| 类 | 说明 |
|---|---|
| `AppGlobalResponseAdvice` | App 端全局响应包装 Advice（继承 `BaseGlobalResponseAdvice`），`@RestControllerAdvice(annotations = AppApi.class)` + `@Order(HIGHEST_PRECEDENCE + 10)`；仅对标注 `@AppApi` 的控制器生效；`wrapStringBody` 调用 `BaseResponse.successMsg(msg)` 包装 String 返回值（与 Web 端 `success(msg)` 差异） |
| `AppExceptionHandler` | App 异常处理器（继承 `BaseExceptionHandler`），`@RestControllerAdvice(annotations = AppApi.class)` + `@Order(HIGHEST_PRECEDENCE + 20)`；仅处理 `@AppApi` 控制器抛出的异常；`getLogPrefix` 返回 `【App端】`；支持业务异常、文件上传超限、非法参数、数据绑定、约束违反、参数校验、请求体解析、缺少请求头/参数、方法不支持、系统异常等 11 种异常类型 |
| `@AppApi` | App 端 REST 控制器标记注解（`@RestController` 组合注解），标注后即等同 `@RestController`，无需重复标注；通过 `@RestControllerAdvice(annotations = AppApi.class)` 限定 Advice 作用范围 |

> **作用范围限定**：`AppExceptionHandler` 与 `AppGlobalResponseAdvice` 通过 `@RestControllerAdvice(annotations = AppApi.class)` 限定作用范围，仅对标注 `@AppApi` 的控制器生效，避免与 `common-web` 模块的 Advice 在同一 Spring 上下文中产生冲突（响应被重复包装、异常被重复处理）。

### 6. 健康检查与指标采集

| 类 | 说明 |
|---|---|
| `AppHealthIndicator` | App 模块健康检查指示器（`@ConditionalOnClass(HealthIndicator.class)` + `ydsz.app.enabled=true`），暴露 `/actuator/health/app` 端点；检测 API 签名验证配置状态（启用/密钥/容差/排除数）和指标采集状态；签名验证启用但密钥未配置时标记为 DOWN |
| `AppMetrics` | App 模块 Micrometer 指标采集（实现 `AuthMetrics` 接口），统一 App 端认证指标的采集契约；采集 `app.signature.verify.total`、`app.signature.verify.duration`、`app.auth.total`、`app.auth.duration` 等指标；使用 `ConcurrentHashMap` 缓存 Counter/Timer 实例；`MeterRegistry` 为 null 时降级为无指标采集 |

### 7. 工具类

| 类 | 说明 |
|---|---|
| `RequestIdGenerator` | 请求 ID 生成器（工具类，禁止实例化），委托 `SnowflakeUtils` 统一生成分布式唯一 ID；`generateId()` 返回雪花算法 long 值字符串；`generateId(prefix)` 返回带前缀的 ID（`prefix + snowflakeId`） |

### Filter 链顺序（Order 由小到大）

| 顺序 | Filter | 说明 |
|---|---|---|
| `HIGHEST_PRECEDENCE + 20` | `AppContentCachingFilter` | 包装 request body（跳过 multipart） |
| `[HIGHEST_PRECEDENCE + 30]` | `[SafeSecurityHeaderFilter]` | 安全响应头（由 safe 模块提供） |
| `[HIGHEST_PRECEDENCE + 40]` | `[SafeApiSignatureFilter]` | API 签名验证（由 safe 模块提供） |
| `HIGHEST_PRECEDENCE + 40` | `AppRequestIdResponseFilter` | 请求 ID 注入 response header |
| `HIGHEST_PRECEDENCE + 50` | `AppAuthFilter` | App 认证（Token 解析 → 上下文设置） |

## 接入方式

### 1. POM 引入依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-app</artifactId>
</dependency>
```

> 本模块会传递引入 `common-base`、`common-auth`、`common-safe`、`common-redis`、`common-json`、`common-exception`。`spring-boot-actuator`、`spring-boot-health`、`micrometer-core`、`jakarta.validation-api` 均为 `<optional>true</optional>`，业务方按需引入对应 starter。

### 2. 配置启用

```yaml
ydsz:
  app:
    enabled: true                    # 启用 app 模块（默认 true）
    content-cache:
      max-size: 2097152              # 请求体最大缓存（字节，默认 2MB）
  base:
    timezone: Asia/Shanghai          # JVM 默认时区
```

### 3. 标注 `@AppApi` 控制器

App 端控制器必须显式标注 `@AppApi`，无需再标 `@RestController`：

```java
import com.njydsz.common.app.annotation.AppApi;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@AppApi
@RequestMapping("/app/users")
public class AppUserController {

    @GetMapping("/{id}")
    public User getById(@PathVariable Long id) {
        // 返回值会被 AppGlobalResponseAdvice 自动包装为 BaseResponse
        return userService.getById(id);
    }
}
```

## 配置项

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.app.enabled` | true | 是否启用 app 模块 |
| `ydsz.app.content-cache.max-size` | `2097152`（2MB） | 请求体最大缓存字节数，`@Min(0)` 校验，超过此值的内容被截断丢弃 |
| `ydsz.app.cors.enabled` | true | 是否启用 CORS（继承 `BaseCorsProperties`） |
| `ydsz.app.cors.allow-credentials` | false | 是否允许发送 Cookie 等凭证 |
| `ydsz.app.cors.allowed-origin-patterns` | `[]` | 允许的跨域来源模式列表（推荐 `capacitor://localhost`、`http://localhost`） |
| `ydsz.app.cors.allowed-headers` | `["*"]` | 允许的 HTTP 请求头列表 |
| `ydsz.app.cors.allowed-methods` | `["*"]` | 允许的 HTTP 请求方法列表 |
| `ydsz.app.cors.exposed-headers` | `[]` | 允许暴露给客户端的响应头列表 |
| `ydsz.app.cors.max-age` | 3600 | 预检请求缓存时间（秒） |
| `ydsz.app.cors.path-pattern` | `/**` | CORS 配置生效的 URL 路径模式 |
| `ydsz.app.cors.order` | 0 | 过滤器注册顺序 |
| `ydsz.app.trace.enabled` | true | 是否启用链路追踪 |
| `ydsz.app.trace.response-header-enabled` | true | 是否在响应头输出请求 ID |
| `ydsz.app.trace.request-id-header-name` | `X-Request-Id` | 请求 ID 响应头名称 |
| `ydsz.app.trace.request-log-enabled` | true | 是否启用请求日志 |
| `ydsz.app.trace.log-level` | `INFO` | 日志级别（INFO/DEBUG） |
| `ydsz.app.trace.sampling-rate` | 1.0 | 日志采样率 [0.0, 1.0] |
| `ydsz.app.trace.slow-request-threshold` | 3000 | 慢请求阈值（ms） |
| `ydsz.safe.api-signature.enabled` | false | API 签名验证开关（由 safe 模块提供） |
| `ydsz.safe.api-signature.app-secret` | - | 签名密钥（建议 `${APP_SECRET}`） |
| `ydsz.safe.api-signature.timestamp-tolerance-seconds` | 300 | 时间戳容差（秒） |
| `ydsz.safe.api-signature.nonce-expire-seconds` | 600 | Nonce 过期时间（秒） |
| `ydsz.safe.api-signature.excludes` | `[]` | 签名验证排除路径（如 `/api/app/public/**`、`/api/app/login`） |
| `ydsz.safe.security-headers.enabled` | true | 安全响应头开关（由 safe 模块提供，默认启用） |

## 使用示例

### 1. App 端控制器开发

```java
import com.njydsz.common.app.annotation.AppApi;
import com.njydsz.common.core.response.BaseResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@AppApi
@RequestMapping("/app/users")
public class AppUserController {

    @PostMapping("/login")
    public String login(@RequestBody LoginDTO dto) {
        // String 返回值会被 AppGlobalResponseAdvice 包装为 BaseResponse.successMsg(msg)
        return "登录成功";
    }

    @GetMapping("/{id}")
    public BaseResponse<User> getById(@PathVariable Long id) {
        // 已是 BaseResponse 类型，不会被重复包装
        return BaseResponse.success(userService.getById(id));
    }
}
```

### 2. CORS 配置（Capacitor / 本地开发）

```yaml
ydsz:
  app:
    cors:
      enabled: true
      allow-credentials: true
      allowed-origin-patterns:
        - "capacitor://localhost"      # Ionic Capacitor 容器
        - "http://localhost"            # 本地 H5 调试
        - "http://localhost:8100"       # Ionic dev server
      allowed-methods:
        - GET
        - POST
        - PUT
        - DELETE
        - OPTIONS
      exposed-headers:
        - X-Request-Id
        - X-Total-Count
```

### 3. 请求体缓存大小调整

```yaml
ydsz:
  app:
    content-cache:
      max-size: 4194304     # 4MB，支持更大请求体（如 base64 图片）
```

### 4. API 签名验证配置（由 safe 模块提供）

```yaml
ydsz:
  safe:
    api-signature:
      enabled: true                           # 启用签名验证
      app-secret: ${APP_SECRET}              # 签名密钥（环境变量注入）
      timestamp-tolerance-seconds: 300       # 时间戳容差 5 分钟
      nonce-expire-seconds: 600              # Nonce 10 分钟防重放
      excludes:                              # 公开接口跳过签名验证
        - /api/app/public/**
        - /api/app/login
        - /api/app/register
```

### 5. 自定义认证处理器

```java
import com.njydsz.common.auth.handler.AbstractAuthHandler;
import com.njydsz.common.auth.model.AuthenticationProvider;
import com.njydsz.common.app.auth.AppAuthInfo;
import com.njydsz.common.util.auth.YdszAuthInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CustomAuthConfiguration {

    // 方式 1：自定义 AuthHandler（覆盖默认 AppAuthHandler）
    @Bean("appAuthHandler")
    public AbstractAuthHandler customAppAuthHandler() {
        return new AbstractAuthHandler() {
            @Override
            protected YdszAuthInfo createAuthInfo() {
                return new AppAuthInfo();
            }
            // 自定义解析逻辑（如从 X-App-Token 头解析）
        };
    }

    // 方式 2：提供 AuthenticationProvider（优先于 AuthHandler）
    @Bean
    public AuthenticationProvider authenticationProvider() {
        return new AuthenticationProvider() {
            @Override
            public YdszAuthInfo authenticate(HttpServletRequest request,
                                              HttpServletResponse response) {
                // 自定义认证逻辑（如调用 SSO、JWT 校验等）
                AppAuthInfo info = new AppAuthInfo();
                info.setUserId(parseUserId(request.getHeader("X-App-Token")));
                return info;
            }
        };
    }
}
```

## SPI 扩展点

| SPI 接口 | 用途 | 实现方 |
|---|---|---|
| `AuthHandler` | 认证处理器接口（来自 `common-auth`），定义 `getAuthInfo(HttpServletRequest, HttpServletResponse)` 方法 | 框架内置 `AppAuthHandler`（默认），业务可覆盖 |
| `AbstractAuthHandler` | 认证处理器抽象基类（来自 `common-auth`），模板方法模式封装解析逻辑，子类只需提供 `createAuthInfo()` | `AppAuthHandler` |
| `AuthenticationProvider` | 自定义认证提供者接口（来自 `common-auth`），优先于 `AuthHandler` 使用 | 业务模块实现，注入后覆盖默认 `AuthHandler` |
| `AuthMetrics` | 认证指标采集接口（来自 `common-auth`），定义 `recordAuthSuccess`、`recordAuthFailure`、`recordAuthSkip` 方法 | 框架内置 `AppMetrics` |
| `HealthIndicator` | Spring Boot 健康指标接口（来自 `spring-boot-health`） | 框架内置 `AppHealthIndicator`，业务可覆盖 |
| `RequestIdResolver` | 请求 ID 解析器接口（来自 `common-base`） | `AppRequestIdResponseFilter`、`AppRequestLogInterceptor` |

### 继承扩展点（来自 `common-base` 的抽象基类）

| 抽象基类 | App 模块实现 | 差异化点 |
|---|---|---|
| `BaseMvcConfiguration` | `AppMvcConfiguration` | 注册 App 特有过滤器链、拦截器、Bean |
| `BaseTimezoneConfiguration` | `AppTimezoneConfiguration` | 无差异（共享时区配置） |
| `BaseI18nConfiguration` | `AppI18nConfiguration` | basename=`i18n/app-messages` |
| `BaseOpenApiConfiguration` | `AppOpenApiConfiguration` | 标题=`YDSZ App API 文档` |
| `BaseCorsProperties` | `AppCorsProperties` | 前缀=`ydsz.app.cors` |
| `BaseTraceProperties` | `AppTraceProperties` | 前缀=`ydsz.app.trace` |
| `BaseGlobalResponseAdvice` | `AppGlobalResponseAdvice` | `wrapStringBody` 调用 `successMsg(msg)` |
| `BaseExceptionHandler` | `AppExceptionHandler` | `@RestControllerAdvice(annotations=AppApi.class)` 限定作用范围 |
| `AbstractContentCachingFilter` | `AppContentCachingFilter` | 可配置缓存容量（默认 2MB） |
| `BaseRequestIdResponseFilter` | `AppRequestIdResponseFilter` | 优先 `RequestHolder`，兜底 `RequestIdGenerator` |
| `BaseRequestLogInterceptor` | `AppRequestLogInterceptor` | 优先 `RequestHolder`，兜底 `RequestIdGenerator` |
| `BaseAuthInfo` | `AppAuthInfo` | `getServiceTypeCode()` 返回 `ServiceType.APP_SERVICE` |

## 健康检查

| 端点 | 说明 | 触发条件 |
|---|---|---|
| `/actuator/health/app` | App 模块健康指标，报告 API 签名配置状态、指标采集状态 | `spring-boot-health` 在类路径 + `ydsz.app.enabled=true` |
| `/actuator/health/safe` | 安全模块健康指标（由 `common-safe` 提供） | `spring-boot-health` 在类路径 + `ydsz.safe.enabled=true` |

`AppHealthIndicator` 暴露信息：

- `module` — 固定为 `app`
- `signature.enabled` — API 签名验证启用状态
- `signature.hasSecret` — 签名密钥是否已配置
- `signature.timestampToleranceSeconds` — 时间戳容差（秒）
- `signature.nonceExpireSeconds` — Nonce 过期时间（秒）
- `signature.excludesCount` — 排除路径数量
- `metrics` — 指标采集状态（`enabled` / `disabled`）

降级判定：

- API 签名验证启用但密钥未配置 → DOWN（`error=API 签名验证已启用但未配置密钥`）

### Micrometer 指标

| 指标 | 类型 | 说明 |
|---|---|---|
| `app.signature.verify.total` | Counter | 签名验证总次数（tag: `result`） |
| `app.signature.verify.duration` | Timer | 签名验证耗时分布（tag: `result`） |
| `app.auth.total` | Counter | 认证总次数（tag: `result`、`userType`、`reason`） |
| `app.auth.duration` | Timer | 认证耗时分布（tag: `result`、`userType`） |

`result` 标签取值：`success`、`failure`、`skip`、`missing_headers`、`invalid_timestamp`、`timestamp_expired`、`nonce_replay`、`no_secret`、`signature_mismatch`。

`reason` 标签取值（认证失败时）：`missing_token`、`invalid_token`、`expired_token`、`revoked_token`、`signature_mismatch`、`unknown`。

> 请求处理耗时由 Spring MVC 内置的 `http.server.requests` 指标覆盖，本模块不再重复采集，避免 URI 标签基数爆炸。

## 注意事项

1. **`@AppApi` 作用域隔离**：App 端控制器必须显式标注 `@AppApi`，`AppGlobalResponseAdvice` 与 `AppExceptionHandler` 通过 `@RestControllerAdvice(annotations = AppApi.class)` 限定作用范围，避免与 `common-web` 模块的 Advice 在同一 Spring 上下文中产生冲突（响应被重复包装、异常被重复处理）。
2. **`common-web` 与 `common-app` 平行关系**：两者是两个**平行**的应用层入口。后端微服务统一使用 `common-web`，`common-app` 仅用于未来移动端项目。若两者同时引入同一 Spring 上下文，`@AppApi` 标注的控制器走 `common-app` 链路，未标注的走 `common-web` 链路，互不冲突。
3. **API 签名验证与安全响应头**：由 `ydsz-common-safe` 模块统一提供，本模块不再重复注册。`AppMvcConfiguration` 通过 `@AutoConfigureBefore(SafeConfiguration.class)` 保证装配顺序。Redis 连通性和安全能力清单由 `SafeHealthIndicator` 统一报告，`AppHealthIndicator` 仅关注 App 模块特有状态，避免重复检测。
4. **`AppMetrics` 实现 `AuthMetrics` 接口**：统一 App 端认证指标的采集契约，`MeterRegistry` 为 null 时降级为无指标采集（不抛异常）。`AppMvcConfiguration` 通过 `ObjectProvider<AppMetrics>` 注入避免与 `AuthMetricsCollector`（同样实现 `AuthMetrics` 接口）产生 bean 歧义。
5. **认证失败原因分类**：`AppAuthFilter.resolveFailureReason` 从异常类名推断失败原因标签（`missing_token`/`invalid_token`/`expired_token`/`revoked_token`/`signature_mismatch`/`unknown`），用于指标 `reason` 标签的取值规范化，便于按原因聚合告警。
6. **`RequestIdGenerator` 委托 `SnowflakeUtils`**：底层雪花算法在分布式部署中应正确配置 workerId 以避免 ID 冲突。App 端 RequestId 优先复用鉴权阶段在 `RequestHolder` 中缓存的值，缺失时才调用 `RequestIdGenerator.generateId()` 兜底生成。
7. **请求体缓存跳过 multipart**：`AbstractContentCachingFilter` 自动跳过 `multipart/` 请求，避免大文件上传场景下的 OOM。默认缓存容量 2MB，超过此值的内容被截断丢弃（不会抛错）。
8. **`AppExceptionHandler` 装配方式**：由 `AppMvcConfiguration` 显式通过 `@Import` 加载，`@RestControllerAdvice` 会被 Spring MVC 自动发现为控制器增强。不要在此类上同时标注 `@AutoConfiguration`，避免与 Spring MVC 生命周期冲突。
9. **`AppOpenApiConfiguration` 描述字段**：返回带 HTML 样式的描述（`<div style='font-size:14px;color:#333;'>...</div>`），用于 Knife4j UI 展示美化。

## 变更记录

- **v1.0.0**（2026-08-02）：按 ydsz-common-jdbc 9 章节标准重构 README；补全 Filter 链顺序表、SPI 扩展点（含继承扩展点）、Micrometer 指标列表、注意事项；统一版本号为 1.0.0
