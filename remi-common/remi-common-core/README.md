# remi-common-core

> REMI 公共底座核心模块（L1 基础设施层）— 统一响应模型、结果码、请求上下文、TraceId、常量定义

## 快速接入

### 1. POM 引入依赖

```xml
<dependency>
    <groupId>com.remisoft</groupId>
    <artifactId>remi-common-core</artifactId>
</dependency>
```

### 2. 配置启用

```yaml
remi:
  core:
    enabled: true                          # 模块总开关（默认启用）
    max-page-size: 1000                    # 最大每页记录数上限（1-5000）
    default-page-size: 20                  # 默认每页记录数（1-5000）
    tenant-mdc-filter-order: -2147483647     # 租户 MDC 过滤器顺序（可选）
```

### 3. 基础使用

```java
import com.remisoft.common.core.response.BaseResponse;
import com.remisoft.common.core.code.BaseResultCode;

// 成功响应
return BaseResponse.success(user);

// 失败响应（使用 ResultCode 枚举）
return BaseResponse.error(BaseResultCode.NOT_FOUND);
```

## 核心 API

| 包 | 关键类 | 职责 |
|---|---|---|
| `response` | `IResponse`, `BaseResponse`, `PageResponse`, `CursorResponse` | 统一 API 响应模型 |
| `response` | `MessageResolverHolder` | 国际化消息解析 SPI |
| `code` | `ResultCode`, `BaseResultCode`, `IExceptionResultCode` | 结果码体系 |
| `adapter` | `ExceptionToResultCodeAdapter` | 异常 → 错误码响应适配器（v2.1.0 新增） |
| `context` | `RequestContext`, `RequestMetadata`, `ProblemDetail` | 请求上下文、跨服务元数据、RFC 7807 |
| `trace` | `TraceIdGenerator`, `TraceIdPropagation` | 链路追踪 |
| `constant` | `HeaderConstants`, `PageConstants`, `SystemConstants` | 全局常量 |
| `config` | `CoreAutoConfiguration`, `CoreProperties` | 自动配置与属性绑定 |
| `metrics` | `CoreHealthIndicator` | 健康检查与诊断 |

## v2.0 关键变更

### 异常响应统一适配器（v2.1.0 新增）
- **新增** `ExceptionToResultCodeAdapter` 类，统一处理异常到错误码响应的桥接逻辑
- `BaseResponse.error(Throwable)` / `error(Throwable, URI)` 内部委托给适配器，职责更清晰
- 异常适配逻辑可独立复用和测试

### 请求上下文职责拆分（v2.1.0 新增）
- **新增** `RequestMetadata` 类，承载跨服务元数据传播能力
- `RequestContext` 中的元数据方法（`putMetadata/getMetadata/exportMetadata/importMetadata`）标记为 `@Deprecated`，委托给 `RequestMetadata`
- `RequestContext.clear()` 自动同步清理 `RequestMetadata`

### PageResponse 能力补全（v2.1.0 新增）
- **新增** `error(ResultCode)` / `error(ResultCode, String)` / `errorWithDetail(...)` 系列方法
- API 与 `BaseResponse` 对齐，消除分页场景的能力缺失

### 架构精简
- **移除** `ContextKey<T>` / `ContextKeys` 类型安全键抽象与 `RequestContext.STRING_KEY` 双轨制（死代码清理）
- **移除** `ResultCode.getDomain()` 默认方法（无业务实现类覆盖）
- **废弃** `ProblemDetail.of(String, String, int, String)` 和 `ProblemDetail.of(URI, String, int, String)` 工厂方法（使用 Builder 替代）
- **废弃** `BaseResponse.ERROR` 常量（使用 `UNKNOWN_CODE` 替代，标记 `forRemoval = true`）

### 国际化入口统一
- **移除** `BaseResponse.setResolverIfAbsent()`、`BaseResponse.MessageResolver` 等废弃 API
- **统一** 通过 `MessageResolverHolder` 管理国际化消息解析器
- 自动配置类 `CoreAutoConfiguration` 仅注册到 `MessageResolverHolder`

### 分页归一化增强
- `PageResponse.successWithNormalization` 现在始终携带完整的归一化调试信息
- 新增 `PageResponse.successWithFullNormalization()` 支持 pageNum 和 pageSize 同时归一化
- 响应 `extensions` 中包含：`pageSizeNormalized`、`requestedPageSize`、`actualPageSize`、`maxPageSize`、`defaultPageSize`

### 游标分页支持
新增 `CursorResponse<T>` 支持基于游标的分页（适合大数据量深度分页）：
```java
// 无限滚动加载场景
CursorResponse<List<Item>> resp = CursorResponse.success(items, "next-cursor-token", 20);
// 获取下一页时使用 resp.getNextCursor()
```

### RequestContext 与 RequestMetadata
- **v2.1.0** 新增 `RequestMetadata` 类，承载跨服务元数据传播能力
- 旧 API `RequestContext.putMetadata()`/`exportMetadata()` 等标记为 `@Deprecated`，委托给 `RequestMetadata`
- 推荐使用 `RequestMetadata.put()`、`RequestMetadata.export()` 等新 API
- `RequestContext.clear()` 自动同步清理 `RequestMetadata`

```java
// 设置元数据（将透传到下游服务）- 新 API
RequestMetadata.put("appId", "app-001");
RequestMetadata.put("businessLine", "retail");

// 导出为 HTTP 请求头 - 新 API
Map<String, String> headers = RequestMetadata.export();

// 从上游请求导入 - 新 API
RequestMetadata.importMetadata(importedMetadata);
```

### ProblemDetail 自动注入增强
- `BaseResponse.error(Throwable, URI)` 现在自动注入 `traceId` 和 `requestId` 到 ProblemDetail
- 需要在 MDC 中设置 `traceId` 和 `requestId`（由框架过滤器自动完成）

### ResultCode 业务域支持
新增 `ResultCode.getDomain()` 默认方法，支持错误码业务域分类：
```java
public enum OrderResultCode implements ResultCode {
    ORDER_NOT_FOUND("B02001", "订单不存在", 404);

    @Override
    public String getDomain() {
        return "order";  // 业务域标识
    }
    // ...
}
```

### 健康检查增强
`/actuator/health` 现在输出更多诊断信息：
- `moduleVersion` — 模块版本号
- `uptimeSeconds` — 应用运行时间
- `pageConstantsInitialized` — 分页配置初始化状态

## 代码质量审计修复记录（2026-08-05）

### P1 高危修复
1. **BaseResultCodeTest 枚举数量断言修复**：测试硬编码从 46 → 47（包含 SUCCESS 枚举值），防止新增枚举时 CI 失败
2. **废弃 API 清理**：`BaseResponse.error()` 和 `error(String)` 内部从废弃常量 `ERROR` 改为 `UNKNOWN_CODE`
3. **JavaDoc 格式修复**：`HeaderConstants` 中 `X_IDENTITY_TYPE` 和 `X_SERVICE_TYPE` 注释块格式问题修复

### P2 优化改进
1. **i18n 异常日志增强**：`SpringMessageResolver` 增加 DEBUG 级别异常日志，便于排查"为何消息未国际化"问题
2. **集成测试 Docker 可用性前置检查**：`AbstractIntegrationTest` 增加 `isDockerAvailable()` 前置检查，本地无 Docker 时优雅降级而非硬失败
3. **ProblemDetail 工厂方法统一**：`BaseResponse.error(Throwable, URI)` 内部改用 `ProblemDetail.of(resultCode, detail, instance)` 工厂方法，减少模板代码

### P3 长期建议（待实施）
- native-image 反射配置自动生成（使用 GraalVM Agent）
- i18n 热加载能力
- `extractResultCode()` 方法命名语义优化（→ `resolveResultCode()`）

## 版本特性速查

| 版本 | 关键变更 |
|---|---|
| **v2.1.0** | 异常适配器独立（ExceptionToResultCodeAdapter）；请求上下文职责拆分（RequestMetadata）；PageResponse 能力补全；架构精简（移除 ContextKey 双轨制、ResultCode.getDomain） |
| v2.0.0 | 国际化入口统一；CursorResponse；RequestContext 元数据传播；健康检查增强 |
| v1.8.0 | 国际化逻辑抽取至 `MessageResolverHolder` |
| v1.7.0 | `CoreHealthIndicator`、`ProblemDetail` RFC 7807 |
| v1.6.0 | `PageResponse.successWithNormalization()`、`PageConstants.normalizePageSizeWithResult()` |
| v1.5.0 | `TraceIdGenerator` W3C Trace Context 支持 |

## 国际化支持

模块自带 `i18n/messages.properties`（英文默认）和 `i18n/messages_zh_CN.properties`（中文）资源文件。
消息 key 格式为 `error.{ENUM_NAME}`，与 `ResultCode.getMessageKey()` 默认实现一致。

使用 `MessageResolverHolder` 管理解析器：
```java
// 检查解析器状态
boolean registered = MessageResolverHolder.isResolverRegistered();
```

## 分页使用指南

### Offset 分页（传统）
```java
// 标准分页响应
PageResponse<List<User>> resp = PageResponse.success(total, pageNum, pageSize, users);

// 带归一化标记
PageResponse<List<User>> resp = PageResponse.successWithNormalization(
    total, pageNum, pageSize, rawPageSize, users);
```

### 游标分页（推荐用于深分页）
```java
// 首次请求
CursorResponse<List<Item>> first = CursorResponse.success(items, nextToken, 20);

// 后续请求（使用上一次返回的 nextCursor）
CursorResponse<List<Item>> next = CursorResponse.success(items, nextNextToken, 20);
```

## 相关模块

| 能力 | 所在模块 |
|---|---|
| 多租户隔离 | `remi-common-tenant` |
| Web 层过滤器 | `remi-common-base` / `remi-common-web` |
| 敏感数据脱敏 | `remi-common-safe` |
| 认证授权 | `remi-common-auth` |

## 注意事项

1. **RequestContext 必须显式清理**：基于 TransmittableThreadLocal 的上下文必须在请求结束时调用 `clear()`，建议使用 `RequestContext.newCleanupGuard()` try-with-resources 模式
2. **业务模块自定义错误码请实现 `ResultCode` 接口**：不应放入 `BaseResultCode`
3. **配置校验 fail-fast**：`CoreProperties` 使用 JSR-303 校验，配置非法时应用启动失败
4. **国际化解析器**：v2.0 起统一通过 `MessageResolverHolder`，不再使用 `BaseResponse.setResolverIfAbsent()`

---

完整变更记录见 [CHANGELOG.md](./CHANGELOG.md)
