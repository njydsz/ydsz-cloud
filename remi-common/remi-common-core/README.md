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
| `response` | `IResponse`, `BaseResponse`, `PageResponse` | 统一 API 响应模型 |
| `response` | `MessageResolverHolder` | 国际化消息解析 SPI |
| `code` | `ResultCode`, `BaseResultCode`, `IExceptionResultCode` | 结果码体系 |
| `context` | `RequestContext`, `ContextKeys`, `ProblemDetail` | 请求上下文、RFC 7807 |
| `trace` | `TraceIdGenerator`, `TraceIdPropagation` | 链路追踪 |
| `constant` | `HeaderConstants`, `PageConstants`, `SystemConstants` | 全局常量 |
| `config` | `CoreAutoConfiguration`, `CoreProperties` | 自动配置与属性绑定 |
| `metrics` | `CoreMetrics` | Micrometer 指标注册 |

## 版本特性速查

| 版本 | 关键变更 |
|---|---|
| v1.8.0 | 国际化逻辑抽取至 `MessageResolverHolder`；`CoreMetrics` 静态单例安全化；`FilterIgnoreProperties` 默认值内聚 |
| v1.7.0 | `ContextKeys`、`FilterIgnoreProperties`、`CoreMetrics`、`CoreHealthIndicator` |
| v1.6.0 | `PageResponse.successWithNormalization()`、`PageConstants.normalizePageSizeWithResult()` |
| v1.5.0 | `TraceIdGenerator` W3C Trace Context 支持 |

## 国际化支持

模块自带 `i18n/messages.properties`（英文默认）和 `i18n/messages_zh_CN.properties`（中文）资源文件。
消息 key 格式为 `error.{ENUM_NAME}`，与 `ResultCode.getMessageKey()` 默认实现一致。

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

---

完整变更记录见 [CHANGELOG.md](./CHANGELOG.md)
