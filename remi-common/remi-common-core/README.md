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
| `context` | `RequestContext`, `ContextKeys`, `ProblemDetail` | 请求上下文、RFC 7807 |
| `trace` | `TraceIdGenerator`, `TraceIdPropagation` | 链路追踪 |
| `constant` | `HeaderConstants`, `PageConstants`, `SystemConstants` | 全局常量 |
| `config` | `CoreAutoConfiguration`, `CoreProperties` | 自动配置与属性绑定 |
| `metrics` | `CoreHealthIndicator` | 健康检查与诊断 |

## v2.0 关键变更

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

### RequestContext 元数据传播
新增跨服务元数据传播能力：
```java
// 设置元数据（将透传到下游服务）
RequestContext.putMetadata("appId", "app-001");
RequestContext.putMetadata("businessLine", "retail");

// 导出为 HTTP 请求头
Map<String, String> headers = RequestContext.exportMetadata();

// 从上游请求导入
RequestContext.importMetadata(importedMetadata);
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

## 版本特性速查

| 版本 | 关键变更 |
|---|---|
| **v2.0.0** | 国际化入口统一；CursorResponse；RequestContext 元数据传播；ResultCode 业务域支持；健康检查增强 |
| v1.8.0 | 国际化逻辑抽取至 `MessageResolverHolder` |
| v1.7.0 | `ContextKeys`、`CoreHealthIndicator`、`ProblemDetail` RFC 7807 |
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
