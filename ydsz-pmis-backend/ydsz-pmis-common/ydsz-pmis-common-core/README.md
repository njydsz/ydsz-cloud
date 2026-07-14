# ydsz-pmis-common-core

PMIS 公共底座核心模块 — 统一响应模型、请求上下文、TraceId、JobHandler、DAG 编排、特性开关、重试模板、线程池监控。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L1 基础设施层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 被 common 所有子模块及全部 10 个部署单元依赖 |
| **构建顺序** | 最先编译 |

## 核心能力

### 统一响应模型

| 类 | 说明 |
|---|---|
| `BaseResponse<T>` | 统一 API 响应体（code / message / data / traceId） |
| `PageResponse<T>` | 分页响应体（继承 BaseResponse，含 total / pages / list） |
| `ResultCode` / `StandardResultCode` | 错误码接口与标准实现 |
| `IResponse` | 响应标记接口 |
| `SpringMessageResolver` | Spring MessageSource 国际化解析 |

### 请求上下文

| 类 | 说明 |
|---|---|
| `RequestContext` | 基于 ThreadLocal 的请求上下文（traceId / userId / tenantId / authInfo） |
| `ReactiveRequestContext` | WebFlux 响应式上下文（基于 Reactor Context） |
| `ContextKey<T>` | 类型安全的上下文 Key 定义 |
| `RequestContextExecutor` | 上下文传播的 ExecutorService（TTL 包装） |
| `TtlTaskDecorator` | TTL 任务装饰器，异步线程上下文传播 |
| `TtlAsyncAutoConfiguration` | TTL 异步上下文自动配置 |

### 常量与枚举

| 类 | 说明 |
|---|---|
| `HeaderConstants` / `TokenConstants` / `CacheConstants` | HTTP 头、Token、缓存 Key 常量 |
| `SecurityConstants` / `ProtocolConstants` | 安全、协议常量 |
| `PageConstants` / `FilterIgnoreConstant` | 分页默认值、过滤器忽略路径 |
| `YesOrNo` / `TypeEnum` / `IdentityType` | 通用枚举 |
| `DataScopeType` / `ServiceType` | 数据范围、服务类型枚举 |

### DAG 有向无环图

| 类 | 说明 |
|---|---|
| `DagGraph` | DAG 图定义（节点 + 边 + 依赖关系） |
| `DagInstanceStatus` / `DagNodeStatus` | 实例/节点状态枚举 |
| `DagFailureStrategy` | 失败策略（CONTINUE / ABORT / RETRY） |
| `SpELConditionEvaluator` | SpEL 条件表达式求值器 |

### Job 调度框架

| 类 | 说明 |
|---|---|
| `JobHandler` | Job 处理接口 |
| `MapProcessor` / `MapReduceProcessor` | Map/MapReduce 分片处理 |
| `ShardingContext` / `MapContext` | 分片上下文 |
| `JobContextHolder` / `JobLoggerHolder` | Job 上下文与日志持有者 |
| `JobRunRecorder` / `ProcessResult` | 运行记录与处理结果 |

### 工程能力增强

| 类 | 说明 |
|---|---|
| `RetryTemplate` | 声明式重试模板（指数退避 + 异常过滤 + 最大重试次数） |
| `BulkheadManager` | 舱壁隔离管理器（信号量限流保护资源） |
| `FeatureFlagService` / `FeatureFlagManager` | 特性开关服务（静态配置 + 百分比灰度 + 白名单） |
| `FeatureFlag` / `FeatureFlagSnapshot` | 特性开关模型与快照 |
| `ThreadPoolRegistry` / `ThreadPoolRegistryAutoConfiguration` | 线程池注册中心（统一管理 + Micrometer 监控 + 优雅停机） |
| `AbstractModuleMetrics` | 模块级 Micrometer 指标基类 |
| `TraceIdGenerator` / `TraceIdSupplier` | TraceId 生成器 |

## 自动配置

| 配置类 | 激活条件 |
|---|---|
| `CoreAutoConfiguration` | 总是激活 |
| `TraceAutoConfiguration` | 总是激活 |
| `YdszSchedulingAutoConfiguration` | Spring Scheduling 可用时激活 |
| `TtlAsyncAutoConfiguration` | TTL 可用时激活 |
| `ThreadPoolRegistryAutoConfiguration` | Micrometer 可用时激活 |

## 配置项

```yaml
pmis:
  core:
    trace:
      header-name: X-Trace-Id        # TraceId 请求头名称
      generate-if-missing: true       # 缺失时自动生成
    thread-pool:
      monitor-enabled: true           # 线程池监控开关
      monitor-interval: 30s           # 采样间隔
  feature-flag:
    cache-ttl: 60s                    # 特性开关缓存过期时间
```

## 依赖

```xml
<dependency>
    <groupId>com.njydsz.pmis</groupId>
    <artifactId>ydsz-pmis-common-core</artifactId>
</dependency>
```
