# ydsz-common-core

> 统一响应/请求/上下文基座（L2 基础设施层）

提供统一响应模型（`YdszResponse` / `PageResponse`）、请求上下文（`RequestContext` / `TenantContext`）、链路追踪（`TraceIdGenerator` / `TraceIdPropagation`）、特性开关（`FeatureFlagService`）、国际化消息解析器、分页常量、Header 常量等基础能力，是所有 common 子模块的底层基座。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L2 基础设施层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供统一响应封装、请求上下文、链路追踪、特性开关、常量等基础能力 |
| **依赖** | ydsz-common-json、transmittable-thread-local、lombok、slf4j-api、jakarta.validation-api；可选 spring-boot-autoconfigure |
| **版本** | 2.0.0 |

## 核心能力

### 1. 统一响应模型

| 类 | 说明 |
|---|---|
| `YdszResponse<T>` | 统一响应封装（code / message / data / traceId），所有 API 返回值标准格式 |
| `PageResponse<T>` | 分页响应（继承 YdszResponse，增加 total / pageNum / pageSize） |
| `IResponse` | 响应接口（定义 getCode / getMessage / getData 标准约定） |
| `CurrentUser` | 当前用户上下文 DTO（userId / username / tenantId / roles） |

**响应约定**：

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "traceId": "abc123"
}
```

### 2. 请求上下文

| 类 | 说明 |
|---|---|
| `RequestContext` | 请求上下文（TTL 传播），存储请求级属性（attributes） |
| `TenantContext` / `TenantContextHolder` | 租户上下文（ThreadLocal + TTL 传播） |
| `RequestSnapshot` | 请求快照（进入时捕获，用于审计） |
| `BizContextKeys` / `ContextKey<T>` | 上下文 Key 常量定义 |

**线程池异步传播**：基于 `TransmittableThreadLocal`（TTL），RequestContext 和 TenantContext 可在父子线程和线程池任务间自动传递。

### 3. 链路追踪

| 类 | 说明 |
|---|---|
| `TraceIdGenerator` **SPI** | TraceId 生成策略接口（默认 UUID） |
| `TraceIdPropagation` | TraceId 传播工具（从请求 Header 提取 / 生成 / 回写到 Response） |
| `SkyWalkingAutoConfiguration` | SkyWalking 链路追踪自动配置 |
| `SkyWalkingProperties` | SkyWalking 配置（`ydsz.skywalking.*`） |

### 4. 特性开关

| 类 | 说明 |
|---|---|
| `FeatureFlagService` **SPI** | 特性开关服务接口（判断特性是否启用） |
| `ConfigDrivenFeatureFlagService` | 基于配置文件的特性开关实现（`ydsz.feature-flags.*`） |
| `FeatureFlagContext` | 特性开关上下文 |

### 5. 常量定义

| 类 | 说明 |
|---|---|
| `HeaderConstants` | HTTP Header 常量（X-Tenant-Id / X-Request-Id / X-Trace-Id / X-User-Id 等） |
| `SystemConstants` | 系统常量（缺省分页大小、最大分页大小、超级管理员 ID 等） |
| `PageConstants` | 分页常量（defaultPageNum / defaultPageSize / maxPageSize） |
| `DataScopeConstants` | 数据权限常量（数据范围类型枚举值） |

### 6. 国际化

| 类 | 说明 |
|---|---|
| `SpringMessageResolver` + package-info | Spring MessageSource 优先，ResourceBundle 兜底 |
| `MessageSourceAutoConfiguration` | 国际化自动配置 |

## 接入方式

### 1. POM 引入依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-core</artifactId>
</dependency>
```

### 2. 直接使用

```java
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.core.context.TenantContextHolder;

// 统一响应
@GetMapping("/user/{id}")
public YdszResponse<User> getUser(@PathVariable Long id) {
    return YdszResponse.success(userService.getById(id));
}

// 分页响应
public PageResponse<User> list(PageQuery query) {
    IPage<User> page = userService.page(query);
    return PageResponse.of(page);
}

// 请求上下文
RequestContext.setAttribute("key", "value");
String value = RequestContext.getAttribute("key");

// 租户上下文
TenantContextHolder.setTenantId("tenant_001");
String tenantId = TenantContextHolder.getTenantId();

// 特性开关
if (FeatureFlagService.isEnabled("new_checkout_flow")) {
    // 新逻辑
}
```

## 配置项

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.core.trace-id.header` | X-Trace-Id | TraceId Header 名称 |
| `ydsz.core.trace-id.response` | true | 是否回写 TraceId 到 Response Header |
| `ydsz.core.tenant.header` | X-Tenant-Id | 租户 ID Header 名称 |
| `ydsz.core.page.default-page-size` | 20 | 默认分页大小 |
| `ydsz.core.page.max-page-size` | 1000 | 最大分页大小（防深度分页） |
| `ydsz.core.feature-flags.*` | - | 开关配置（如 `ydsz.core.feature-flags.new-checkout-flow=true`） |
| `ydsz.core.message-source.basename` | messages | i18n 资源包位置 |
| `ydsz.core.message-source.fallback-to-system-locale` | true | 找不到资源时是否 fallback |

## SPI 扩展点

| SPI 接口 | 用途 | 注册方式 |
|---|---|---|
| `TraceIdGenerator` | TraceId 生成策略（默认 UUID v4） | `@Component` |
| `FeatureFlagService` | 特性开关后端（默认 ConfigDriven） | `@ConditionalOnMissingBean` |

## 自动配置类

| 类 | 触发条件 |
|---|---|
| `CoreAutoConfiguration`（META-INF.imports） | `ydsz-common-core` 在 classpath |
| `MessageSourceAutoConfiguration` | Spring MessageSource 存在 |
| `SkyWalkingAutoConfiguration` | SkyWalking 在 classpath |

## 注意事项

1. **TTL 传播**：使用线程池时必须用 TTL 包装（`TtlExecutors.getTtlExecutorService()`），否则子线程丢失 TenantContext。
2. **RequestContext 生命周期**：每次请求结束后自动清理（由 Filter / Interceptor 触发），无需手动清理。
3. **YdszResponse 全局包装**：`BaseGlobalResponseAdvice`（common-base）自动将非 YdszResponse 返回值包装。
4. **TraceId 优先级**：从请求 Header 提取 > 生成新 TraceId。

## 变更记录

- **2.0.0**（2026-09-01）：统一响应模型重构（YdszResponse / PageResponse / IResponse）；RequestContext 基于 TTL 传播；新增特性开关服务（FeatureFlagService + ConfigDrivenFeatureFlagService）；常量拆分（HeaderConstants / SystemConstants / PageConstants / DataScopeConstants）。
- **26.09.01**（2026-08-02）：初始版本。
