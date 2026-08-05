# remi-common-core 变更记录

本文档记录模块的所有重要变更。格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)。

## [Unreleased]

### Changed
- **重构**：`BaseResponse` 的国际化逻辑抽取至独立的 `MessageResolverHolder` 类，职责更清晰
- **重构**：`CoreMetrics` 消除静态 volatile 实例的 this 逃逸问题，通过 `MetricsAccessor` 接口实现可测试性
- **重构**：`FilterIgnoreProperties` 将默认服务名列表内聚到配置属性中，消除 `FilterIgnoreConstant` 的硬编码业务模块名

## [1.7.0] - 2026-08-05

### Added
- 新增 `IExceptionResultCode` 契约接口，桥接异常 → 响应码（消除反射探测，性能提升 + 安全性提升）
- 新增 `ContextKeys` 强类型上下文常量库（`USER_ID` / `TENANT_ID` / `TRACE_ID` / `REQUEST_ID` / `LANGUAGE` / `TENANT_ISOLATION_SKIPPED`）
- 新增 `FilterIgnoreProperties` 配置类（`overrideMode` + `authFilterIgnoreServiceNames`），由 `CoreAutoConfiguration` 注册 Bean
- 新增 `CoreMetrics` Micrometer 指标门面（`incrementResponse` / `recordHoldTime`），无依赖时自动 no-op

### Changed
- `CoreHealthIndicator` 增加 `PageConstants.isInitialized()` 回退检测（返回 `UNKNOWN` 非 `DOWN`）
- `BaseResponse` 所有错误响应构建路径自动注入 MDC traceId（链路追踪贯通）
- `ProblemDetail` 类级 `@JsonInclude(NON_NULL)` 抑制 null 字段输出

### Removed
- 移除旧 `RESOLVER.findField()` 反射路径
- 移除 `CoreHealthIndicator` 内部 `PageConstantsRuntimeInfo` 类

### Fixed
- 补充 `additional-spring-configuration-metadata.json` 配置描述
- `BaseResponseTest` 添加 `@AfterEach` 清理 `RESOLVER` 静态状态

## [1.6.0] - 2026-08-04

### Added
- `UNKNOWN_CODE` 常量（替代废弃的 `ERROR`）
- `CoreHealthIndicator` 健康检查指示器
- `RequestContext.Builder` / `view()` / `newCleanupGuard(Duration)`
- `PageConstants.normalizePageSizeWithResult()`
- `PageResponse.successWithNormalization()`

### Fixed
- `ContextKey` 类型转换修复
- `DEFAULT_TENANT_ID` 文档修正

## [1.5.0] - 2026-08-03

### Added
- `TraceIdGenerator` 使用 `ThreadLocalRandom` + W3C Trace Context 支持
- `TraceIdPropagation` 纯 JDK 链路追踪传播工具
- `PageConstants` 分页归一化方法

### Removed
- 移除 Snowflake 策略，统一 UUID TraceId
- 移除零消费死代码 `TraceConstants` / `SecurityConstants`

## [1.1.0] - 2026-08-03

### Added
- TraceId 传播工具（`TraceIdPropagation`）
- 分页归一化方法

### Removed
- 移除无消费方的 `metrics` 包

## [1.0.0] - 2026-08-02

### Added
- 初始版本发布
- 统一响应模型、结果码体系、请求上下文、TraceId 生成等基础能力
