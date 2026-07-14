# ydsz-pmis-common-exception

PMIS 统一异常处理框架 — 异常层级体系、错误码管理、RFC 7807 ProblemDetail、国际化 i18n、全局异常处理器、异常构建器。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L3 基础服务层 |
| **类型** | 公共依赖库（不独立部署） |

## 核心能力

### 异常层级体系

```
RuntimeException
  └─ AbstractPmisException          ← PMIS 异常基类
       ├─ BusinessException          ← 业务异常（可预期）
       ├─ SystemException            ← 系统异常（不可预期）
       ├─ ValidationException        ← 参数校验异常
       ├─ AuthException              ← 认证异常
       ├─ PermissionDeniedException  ← 权限异常
       └─ ...                        ← 各模块自定义异常
```

### 核心类

| 类 | 说明 |
|---|---|
| `AbstractPmisException` | PMIS 异常抽象基类（错误码 + 消息 + 扩展数据 + 链式调用） |
| `YdszExceptionBuilder<T>` | 异常构建器（CRTP 模式，类型安全的链式构建） |
| `BusinessException` | 业务异常（HTTP 200 + 业务错误码） |
| `SystemException` | 系统异常（HTTP 500） |

### 异常特性

- **链式构建**：`new BusinessException("USER_NOT_FOUND").data("userId", 123).data("tenant", "acme")`
- **扩展数据**：`ConcurrentHashMap<String, Object>` 类型安全的附加数据
- **错误码体系**：`ResultCode` 接口 + `StandardResultCode` 标准实现
- **i18n 支持**：异常消息支持 `MessageSource` 国际化解析

### 全局异常处理

| 类 | 说明 |
|---|---|
| `GlobalExceptionHandler` | 全局异常处理器（`@RestControllerAdvice`） |
| 兼容处理 | `MethodArgumentNotValidException` / `ConstraintViolationException` / `HttpRequestMethodNotSupportedException` / `HttpMessageNotReadableException` / `MaxUploadSizeExceededException` / `NoHandlerFoundException` 等 |

### RFC 7807 ProblemDetail

支持 RFC 7807 HTTP Problem Details 标准格式输出：

```json
{
  "type": "https://pmis.njydsz.com/errors/business",
  "title": "Business Error",
  "status": 200,
  "detail": "用户不存在",
  "instance": "/api/v1/users/123",
  "traceId": "a1b2c3d4",
  "code": "USER_NOT_FOUND",
  "timestamp": "2026-07-14T10:30:00Z",
  "ext": {
    "userId": 123
  }
}
```

## 使用示例

```java
// 抛出业务异常
throw new BusinessException("USER_NOT_FOUND")
    .data("userId", userId)
    .data("tenant", tenantId);

// 构建系统异常
throw new SystemException("DATABASE_CONNECTION_FAILED")
    .data("dataSource", "master");
```

## 自动配置

异常处理器通过 `@RestControllerAdvice` 自动注册，无需额外配置。

## 依赖

```xml
<dependency>
    <groupId>com.njydsz.pmis</groupId>
    <artifactId>ydsz-pmis-common-exception</artifactId>
</dependency>
```
