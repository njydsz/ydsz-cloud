# ydsz-common-app

> YDSZ 移动端 App 后端服务基座模块（L6 应用层）

继承 `common-base` 的所有抽象能力，叠加 App 认证（`AppAuthFilter`）、请求体缓存（`AppContentCachingFilter`）、请求追踪（`AppRequestIdResponseFilter`）、健康检查（`AppHealthIndicator`）、指标采集（`AppMetrics`）以及作用域隔离（`@AppApi`）等移动端特有配置。本模块与 `common-web` 是两个**平行**的应用层入口，后端微服务**统一使用 `common-web`**，本模块**暂无消费方**，为未来移动端项目预留。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L6 应用层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 继承 `common-base` 提供 App 端特有能力：App 认证、请求体缓存、请求追踪、健康检查、Micrometer 指标采集、`@AppApi` 作用域隔离 |
| **依赖** | common-domain、common-base（传递 common-core、common-util、common-exception、common-json、common-auth、common-safe）、common-redis；可选：spring-boot-actuator、spring-boot-health、micrometer-core、jakarta.validation-api |
| **版本** | 26.09.01-SNAPSHOT |

## 核心能力

### 1. MVC 核心配置

| 类 | 说明 |
|---|---|
| `AppMvcConfiguration` | App 端 MVC 核心配置（继承 `BaseMvcConfiguration`），`@AutoConfiguration` + `@AutoConfigureBefore({YdszAutoConfiguration.class, SafeConfiguration.class})`；集中注册过滤器链、拦截器、`AppAuthHandler`、`AppMetrics`、`AppHealthIndicator` |
| `AppI18nConfiguration` | 国际化配置（继承 `BaseI18nConfiguration`），basename=`i18n/app-messages` |
| `AppOpenApiConfiguration` | OpenAPI 配置（继承 `BaseOpenApiConfiguration`），标题=`YDSZ App API 文档` |
| `AppCorsProperties` | CORS 配置（继承 `BaseCorsProperties`），前缀 `ydsz.app.cors` |
| `AppTraceProperties` | Trace 配置（继承 `BaseTraceProperties`），前缀 `ydsz.app.trace` |
| `AppContentCacheProperties` | 请求体缓存配置（`ydsz.app.content-cache`），`@Validated` + `@Min(0)` 校验，默认 2MB |
| `AppFilterOrder` | App 端 Filter 执行顺序常量 |
| `RequestIdGeneratorAutoConfiguration` | RequestId 生成器自动配置（委托 `SnowflakeUtils`） |

### 2. App 认证

| 类 | 说明 |
|---|---|
| `AppAuthFilter` | App 认证过滤器，作为移动端请求入口；`doPreAuth` 生成或复用 RequestId；`resolveAuthInfo` 优先使用 `AuthenticationProvider`，为空时降级到 `AuthHandler`；认证成功/失败均通过 `AuthMetrics` 上报指标 |
| `AppAuthHandler` | App 认证处理器（继承 `AbstractAuthHandler`），模板方法模式仅提供 `AppAuthInfo` 实例创建 |
| `AppAuthInfo` | App 认证上下文信息（继承 `BaseAuthInfo`），`getServiceTypeCode()` 返回 `ServiceType.APP_SERVICE` |

### 3. 过滤器链

| 类 | 说明 |
|---|---|
| `AppContentCachingFilter` | 请求体缓存过滤器（继承 `AbstractContentCachingFilter`），默认 2MB |
| `AppRequestIdResponseFilter` | 请求 ID 响应头过滤器（继承 `BaseRequestIdResponseFilter`），优先复用 `RequestHolder`，缺失时兜底 `RequestIdGenerator` |
| `AppAuthFilter` | App 认证过滤器（见上方 App 认证） |

> API 签名验证和安全响应头由 `ydsz-common-safe` 模块统一提供，本模块不再重复注册。

### 4. 拦截器

| 类 | 说明 |
|---|---|
| `AppRequestLogInterceptor` | 请求日志拦截器（继承 `BaseRequestLogInterceptor`），`resolveRequestId` 优先从 `RequestHolder` 获取；由 `AppMvcConfiguration.addInterceptors()` 显式注册 |

### 5. 全局响应包装与异常处理（作用域隔离）

| 类 | 说明 |
|---|---|
| `AppGlobalResponseAdvice` | App 端全局响应包装（继承 `BaseGlobalResponseAdvice`），`@RestControllerAdvice(annotations = AppApi.class)` + `@Order(HIGHEST_PRECEDENCE + 10)`；仅对标注 `@AppApi` 的控制器生效 |
| `AppExceptionHandler` | App 异常处理器（继承 `BaseExceptionHandler`），`@RestControllerAdvice(annotations = AppApi.class)` 限定作用范围 |
| `@AppApi` | App 端 REST 控制器标记注解（`@RestController` 组合注解） |

### 6. 健康检查与指标采集

| 类 | 说明 |
|---|---|
| `AppHealthIndicator` | App 模块健康检查（`@ConditionalOnClass(HealthIndicator.class)` + `ydsz.app.enabled=true`），检测 API 签名配置和指标采集状态 |
| `AppMetrics` | App 模块 Micrometer 指标采集（实现 `AuthMetrics` 接口） |

### 7. 工具类

| 类 | 说明 |
|---|---|
| `RequestIdGenerator` | 请求 ID 生成器（委托 `SnowflakeUtils`），`generateId()` 返回雪花 ID 字符串 |

### Filter 链顺序（Order 由小到大）

| 顺序 | Filter | 说明 |
|---|---|---|
| `HIGHEST_PRECEDENCE + 20` | `AppContentCachingFilter` | 包装 request body（跳过 multipart） |
| `[Safe]` | `[SafeApiSignatureFilter]` / `[SafeSecurityHeaderFilter]` | 由 safe 模块提供 |
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

> 传递引入 `common-base`、`common-auth`、`common-safe`、`common-redis`、`common-json`、`common-exception`、`common-domain`。

### 2. 启用方式

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
import org.springframework.web.bind.annotation.RequestMapping;

@AppApi
@RequestMapping("/app/users")
public class AppUserController {

    @GetMapping("/{id}")
    public User getById(@PathVariable Long id) {
        // 返回值会被 AppGlobalResponseAdvice 自动包装
        return userService.getById(id);
    }
}
```

## 配置项

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.app.enabled` | `true` | 是否启用 app 模块 |
| `ydsz.app.content-cache.max-size` | `2097152`（2MB） | 请求体最大缓存字节数 |
| `ydsz.app.cors.enabled` | `true` | 是否启用 CORS |
| `ydsz.app.cors.allow-credentials` | `false` | 是否允许发送 Cookie |
| `ydsz.app.cors.allowed-origin-patterns` | `[]` | 允许的跨域来源模式（如 `capacitor://localhost`） |
| `ydsz.app.cors.allowed-headers` | `["*"]` | 允许的 HTTP 请求头 |
| `ydsz.app.cors.allowed-methods` | `["*"]` | 允许的 HTTP 请求方法 |
| `ydsz.app.cors.max-age` | `3600` | 预检缓存秒数 |
| `ydsz.app.cors.path-pattern` | `/**` | CORS 路径模式 |
| `ydsz.app.trace.enabled` | `true` | 是否启用链路追踪 |
| `ydsz.app.trace.response-header-enabled` | `true` | 是否在响应头输出请求 ID |
| `ydsz.app.trace.request-log-enabled` | `true` | 是否启用请求日志 |
| `ydsz.app.trace.sampling-rate` | `1.0` | 日志采样率 |
| `ydsz.app.trace.slow-request-threshold` | `3000` | 慢请求阈值（ms） |

## SPI 扩展点

| SPI 接口 | 用途 | 实现方 |
|---|---|---|
| `AuthHandler` / `AbstractAuthHandler` | 认证处理器 | 内置 `AppAuthHandler`，业务可覆盖同名 Bean `appAuthHandler` |
| `AuthenticationProvider` | 自定义认证提供者（优先于 AuthHandler） | 业务模块实现 |
| `AuthMetrics` | 认证指标采集接口 | 内置 `AppMetrics` |
| `HealthIndicator` | Spring Boot 健康指标 | 内置 `AppHealthIndicator` |
| `RequestIdResolver` | 请求 ID 解析器 | `AppRequestIdResponseFilter` / `AppRequestLogInterceptor`（来自 base） |

### 继承扩展点（来自 `common-base` 的抽象基类）

| 抽象基类 | App 模块实现 | 差异化点 |
|---|---|---|
| `BaseMvcConfiguration` | `AppMvcConfiguration` | 注册 App 特有过滤器链、拦截器、Bean |
| `BaseI18nConfiguration` | `AppI18nConfiguration` | basename=`i18n/app-messages` |
| `BaseOpenApiConfiguration` | `AppOpenApiConfiguration` | 标题=`YDSZ App API 文档` |
| `BaseCorsProperties` | `AppCorsProperties` | 前缀=`ydsz.app.cors` |
| `BaseTraceProperties` | `AppTraceProperties` | 前缀=`ydsz.app.trace` |
| `BaseGlobalResponseAdvice` | `AppGlobalResponseAdvice` | `wrapStringBody` 调用 `successMsg(msg)` |
| `AbstractContentCachingFilter` | `AppContentCachingFilter` | 可配置缓存容量（默认 2MB） |
| `BaseRequestIdResponseFilter` | `AppRequestIdResponseFilter` | 优先 `RequestHolder`，兜底 `RequestIdGenerator` |
| `BaseRequestLogInterceptor` | `AppRequestLogInterceptor` | 优先 `RequestHolder`，兜底 `RequestIdGenerator` |
| `BaseAuthInfo` | `AppAuthInfo` | `getServiceTypeCode()` 返回 `ServiceType.APP_SERVICE` |

## 健康检查

| 端点 | 说明 | 触发条件 |
|---|---|---|
| `/actuator/health/app` | App 模块健康指标，报告 API 签名配置、指标采集状态 | `spring-boot-health` + `ydsz.app.enabled=true` |

`AppHealthIndicator` 暴露信息：

- `module` — 固定为 `app`
- `signature.enabled` / `signature.hasSecret` — 签名验证启用状态和密钥配置
- `signature.timestampToleranceSeconds` — 时间戳容差（秒）
- `signature.nonceExpireSeconds` — Nonce 过期时间（秒）
- `metrics` — 指标采集状态（enabled / disabled）

降级判定：

- API 签名验证启用但密钥未配置 → DOWN

### Micrometer 指标

| 指标 | 类型 | 说明 |
|---|---|---|
| `app.signature.verify.total` | Counter | 签名验证总次数（tag: `result`） |
| `app.signature.verify.duration` | Timer | 签名验证耗时分布 |
| `app.auth.total` | Counter | 认证总次数（tag: `result` / `userType` / `reason`） |
| `app.auth.duration` | Timer | 认证耗时分布 |

`result` 标签：`success` / `failure` / `skip` / `missing_headers` / `invalid_timestamp` / `timestamp_expired` / `nonce_replay` / `no_secret` / `signature_mismatch`

`reason` 标签（认证失败时）：`missing_token` / `invalid_token` / `expired_token` / `revoked_token` / `signature_mismatch` / `unknown`

## 自动配置类

| 配置类 | 激活条件 |
|---|---|
| `AppMvcConfiguration` | `@ConditionalOnWebApplication` + `@ConditionalOnPlatform(PlatformMode.APP)`；注册过滤器链、拦截器、Bean |
| `AppI18nConfiguration` | `@ConditionalOnPlatform(PlatformMode.APP)`；国际化配置 |
| `AppOpenApiConfiguration` | `@ConditionalOnPlatform(PlatformMode.APP)` + `ydsz.doc.enabled=true`；OpenAPI 配置 |
| `RequestIdGeneratorAutoConfiguration` | `@ConditionalOnPlatform(PlatformMode.APP)`；RequestId 生成器 |

注册入口：`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

```
com.njydsz.common.app.config.AppMvcConfiguration
com.njydsz.common.app.config.AppI18nConfiguration
com.njydsz.common.app.config.AppOpenApiConfiguration
com.njydsz.common.app.config.RequestIdGeneratorAutoConfiguration
```

## 注意事项

1. **`@AppApi` 作用域隔离**：App 端控制器必须显式标注 `@AppApi`，`AppGlobalResponseAdvice` 与 `AppExceptionHandler` 通过 `@RestControllerAdvice(annotations = AppApi.class)` 限定作用范围，避免与 `common-web` 的 Advice 在同一 Spring 上下文中产生冲突。
2. **平行关系**：`common-web` 与 `common-app` 是两个平行入口。后端微服务统一使用 `common-web`，`common-app` 仅用于未来移动端项目。两者同时引入时，`@AppApi` 标注的控制器走 App 链路，未标注的走 Web 链路，互不冲突。
3. **API 签名与安全响应头**：由 `ydsz-common-safe` 模块统一提供，本模块不重复注册。`AppMvcConfiguration` 通过 `@AutoConfigureBefore(SafeConfiguration.class)` 保证装配顺序。
4. **`AppMetrics` 实现 `AuthMetrics` 接口**：统一 App 端认证指标采集契约；`MeterRegistry` 为 null 时降级无指标采集。通过 `ObjectProvider<AppMetrics>` 注入避免 Bean 歧义。
5. **认证失败原因分类**：`AppAuthFilter.resolveFailureReason` 从异常类名推断失败原因标签，用于指标 `reason` 标签的取值规范化。
6. **`RequestIdGenerator` 委托 `SnowflakeUtils`**：分布式部署中应正确配置 workerId 以避免 ID 冲突。
7. **请求体缓存跳过 multipart**：自动跳过 `multipart/` 请求，避免大文件上传 OOM。默认缓存容量 2MB，超过截断丢弃。
8. **暂无消费方**：本模块为未来移动端项目预留，暂无业务微服务实际引入；如有移动端后端项目立项，可直接引入使用。

## 变更记录

- **26.09.01**（2026-08-17）：更新依赖说明，添加 `common-domain` 为直接依赖；补全 `AppFilterOrder` / `RequestIdGeneratorAutoConfiguration` 文档
- **26.09.01**（2026-08-02）：按 ydsz-common-jdbc 9 章节标准重构 README；补全 Filter 链顺序表、SPI 扩展点、Micrometer 指标列表、注意事项；统一版本号
