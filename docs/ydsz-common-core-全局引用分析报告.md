# ydsz-common-core 全局引用分析报告

> **版本**：v1.0  
> **日期**：2026-08-04  
> **范围**：全项目 35 个模块对 core 的 6 个包的引用分析  
> **方法**：grep 实证 + 模块级交叉分析 + 架构贯通度评估

---

## 一、Core 六包使用热度矩阵

| 子包 | 核心类 | 使用模块数 | 引用文件数 | 热度 | 结论 |
|------|--------|-----------|-----------|------|------|
| **response** | BaseResponse, IResponse, PageResponse | 23 | ~180 | 🔥🔥🔥 | **核心高价值能力** |
| **code** | BaseResultCode, ResultCode | 14 | ~100 | 🔥🔥 | 异常链路 + gateway 高频 |
| **constant** | HeaderConstants, PageConstants 等 | 10 | ~37 | 🔥 | 中等但集中 |
| **context** | RequestContext, ContextKey, ProblemDetail | 6 | ~8 | ❄️ | **低使用，能力被绕过** |
| **config** | CoreProperties, CoreAutoConfiguration | 1 | 1 | ❄️❄️ | **仅 web 模块引用 CoreProperties** |
| **trace** | TraceIdGenerator, TraceIdPropagation | **0** | **0** | ❄️❄️❄️ | **全项目零外部引用** |

---

## 二、逐包详细分析

### 2.1 trace 包 — 全项目零外部引用 🔴

**现状**：`TraceIdGenerator`（高性能 TraceId 生成 + W3C TraceContext 支持）和 `TraceIdPropagation`（MDC/traceparent 传播）在 35 个依赖模块中**零外部引用**。

**根因**：
- `ydsz-common-base` 的 `TraceFilter` 自行实现 TraceId 生成和 MDC 写入逻辑，未复用 `TraceIdGenerator`
- `ydsz-common-base` 的 `RequestContextCleanupFilter` 自行管理上下文，未复用 `TraceIdPropagation`
- Feign 拦截器自行生成传播头，未调用 `TraceIdPropagation.traceHeaders()`

**影响**：
1. core 的高性能 `generateTraceId()`（ThreadLocalRandom，2.5x 于 UUID）未被消费，`TraceFilter` 可能仍用低效方式
2. W3C `traceparent` header 支持已就绪但无人消费，链路追踪标准化的价值未释放
3. 各模块各自实现 trace 传播，碎片化

**证据**：
```
$ grep -rn "TraceIdGenerator\|TraceIdPropagation" ydsz-common-base/src/  → 零匹配
$ grep -rn "TraceIdGenerator\|TraceIdPropagation" ydsz-common-web/src/   → 零匹配
$ grep -rn "TraceIdGenerator\|TraceIdPropagation" ydsz-common-feign/src/  → 零匹配
```

### 2.2 context 包 — 使用率仅 17%，ContextKey 零外部使用 🟡

**现状**：`RequestContext` 仅被 6 个模块使用（~8 文件），`ContextKey<T>` 类型安全 API **零外部引用**（刚创建，尚未推广），`ProblemDetail` 仅在 `ExceptionHandler` 中使用。

**使用分布**：
| 消费模块 | 使用方式 |
|----------|----------|
| ydsz-common-exception | ExceptionHandler 读 RequestContext 获取 traceId/requestId |
| ydsz-common-auth | AuthContext 从 RequestContext 读 tenantId/userId |
| ydsz-common-base | TraceFilter / RequestContextCleanupFilter 写入上下文 |
| ydsz-common-event | 事件处理中读上下文 |
| ydsz-common-audit | 审计记录中读上下文 |

**未使用 RequestContext 的模块（应该用但没用）**：
- `ydsz-common-feign`：FeignRequestInterceptor 直接用 `HttpServletRequest` 读取 header，而非从 `RequestContext` 获取已解析的值
- `ydsz-gateway`：Gateway Filter 自行解析 header 并传递，未写入 `RequestContext`（gateway 是 reactive 栈，TTL 不可用——但 gateway 可以用自己的 reactor context）
- `ydsz-common-jdbc`：SQL 拦截器从 `HttpServletRequest` header 读权限信息，而非 `RequestContext`

### 2.3 config 包 — 仅 1 个外部引用 🟡

**现状**：`CoreAutoConfiguration` 由 Spring SPI 自动加载，外部无需显式引用。但 `CoreProperties` 全项目仅 `ydsz-common-web` 的 `WebCoreAutoConfiguration` 引用了一次。

**正当理由**：所有模块通过 `@EnableConfigurationProperties(CoreProperties.class)` + Spring 自动绑定即可获得配置，无需显式 import CoreProperties。这是 Spring Boot 的标准使用模式，**不算问题**。

但 `CoreProperties` 目前只有分页参数（`maxPageSize/defaultPageSize`），如果未来有更多配置项，各模块可能需要直接注入 CoreProperties。

### 2.4 constant 包 — 部分常量低使用 🟡

**使用分解**（按类）：

| 常量类 | 引用文件数 | 使用热度 |
|--------|-----------|----------|
| `HeaderConstants` | ~25（auth/feign/web/jdbc/base/gateway） | 🔥🔥🔥 |
| `PageConstants` | ~8（domain/PageQuery + 各 server 分页逻辑） | 🔥🔥 |
| `SystemConstants` | ~5（auth/tenant/literule/message） | 🔥 |
| `FilterIgnoreConstant` | ~2（仅 auth/web 模块） | ❄️ |
| `TokenConstants` | ~1（仅 util/ServletUtils） | ❄️❄️ |

**问题**：
- `TokenConstants` 几乎无人使用（`SUPPLY_AUTHORIZATION` 别名是上一轮遗留问题），其余常量如 `AUTHENTICATION = "Authorization"` 是标准 HTTP 头，不应放在 TokenConstants 中
- `FilterIgnoreConstant` 的服务名清单硬编码在 core，消费方仅有 auth/web 两个模块

### 2.5 完全零引用 core 的依赖模块 🟠

以下模块在 pom.xml 中声明了 `ydsz-common-core` 依赖，但**主代码零引用任何 core 包**：

| 模块 | 文件数 | 风险 |
|------|--------|------|
| `ydsz-common-redis` | 42 | 冗余依赖，编译期无意义 |
| `ydsz-common-cache` | 68 | 冗余依赖，核心 68 个文件不用 core |
| `ydsz-common-safe` | **116** | 最大零引用模块（上一轮移入的 @Sensitive 注解是其唯一桥梁） |
| `ydsz-common-lock` | 36 | 仅 1 个文件引 code + 1 引 response（均为测试/边际代码） |
| `ydsz-system-domain` | 25 | 仅通过异常链间接触发，不直接引用 |
| `ydsz-workflow-domain` | 86 | 同上 |
| `ydsz-project-domain` | **178** | 同上 |
| `ydsz-cronjob-domain` | 63 | 同上 |
| `ydsz-system-server` | 19 | 同上 |
| `ydsz-project-server` | 77 | 同上 |

**分析**：
- domain 层通过 `BusinessException` 间接消费 `ResultCode`，不直接 import core 包——这是 DDD 的隔离设计，合理
- 但 `ydsz-common-redis`（42）、`ydsz-common-cache`（68）、`ydsz-common-safe`（116）作为**公共模块**完全不用 core——这些依赖是冗余的
- `ydsz-common-safe` 在上一轮移入了 `@Sensitive` 注解后有了唯一的 core 依赖桥梁，但其余 113+ 个文件仍不用 core

### 2.6 错误码使用模式 — 架构贯通良好 ✅

各业务模块的错误码体系与 core 的 `ResultCode` 接口贯通正常：

```
业务枚举(XXResultCode) → 实现 ExceptionCode → extends ResultCode
                         ↓
                  BusinessException(XXResultCode.XXX)
                         ↓
                  GlobalExceptionHandler
                         ↓
                  BaseResponse.error(resultCode)
```

所有 8 个业务模块都有独立的 `XXResultCode` 枚举，通过 `@YdszResultCode` 注册到 `ResultCodeRegistry`。**无硬编码字符串错误码、无绕过 BaseResponse 的自定义响应包装类。**

---

## 三、架构贯通不足问题汇总

### 3.1 trace 能力完全未贯通 🔴🔴🔴

| 问题 | 严重性 | 影响 |
|------|--------|------|
| `TraceIdGenerator` 零外部使用 | 高 | 高性能生成器被闲置，各模块用低效方式 |
| `TraceIdPropagation` 零外部使用 | 高 | W3C traceparent 标准化未落地 |
| `TraceFilter`(base) 自行管理 trace | 高 | 与 core 生成器不一致，无法切换 |
| Feign 拦截器自行传播 trace | 中 | 未使用 `traceHeaders()` 标准 header |

### 3.2 RequestContext 未充分贯通 🟡

| 问题 | 严重性 | 影响 |
|------|--------|------|
| Feign 拦截器直接从 HttpServletRequest 读 header | 中 | 绕过已解析的 RequestContext |
| Gateway Filter 未写入 RequestContext | 中 | Gateway 是入口但上下文未初始化（reactive 栈有替代方案） |
| `ContextKey<T>` 零外部使用 | 低 | 刚创建，尚未推广 |

### 3.3 冗余模块依赖 🟠

| 模块 | 建议 |
|------|------|
| `ydsz-common-redis` | 移除 core 依赖（42 文件零引用） |
| `ydsz-common-cache` | 移除 core 依赖（68 文件零引用） |
| `ydsz-common-safe` | 评估：唯一的桥梁是 `@Sensitive` 注解（上一轮迁移），若 safe 有必要用 core 则保留；否则考虑将 Sensitive 相关类移到独立位置 |

---

## 四、可落地优化建议

### P0 立即：消除 trace 能力孤岛

**1. 改造 `TraceFilter`（ydsz-common-base），接入 `TraceIdGenerator`**

`TraceFilter` 当前自行生成 TraceId，改为调用 `TraceIdGenerator.generateTraceId()`，为全项目启用高性能生成。同时写入 W3C `traceparent` 到 MDC。

**改造量**：1 个文件（`TraceFilter.java`），修改 ~5 行

**2. 改造 Feign 拦截器，接入 `TraceIdPropagation`**

`FeignRequestInterceptor` 当前自行拼装 X-Trace-Id header，改为调用 `TraceIdPropagation.traceHeaders()`，同时携带 W3C traceparent。

**改造量**：1 个文件（`FeignRequestInterceptor.java`），修改 ~3 行

### P1 短期：推广 RequestContext 消费

**3. FeignRequestInterceptor 从 RequestContext 读取而非 HttpServletRequest**

当前直接从 `HttpServletRequest` header 读权限信息，改为从 `RequestContext` 获取（已在 Filter 中写入）。这减少对 servlet API 的耦合，使得 Feign 模块对非 servlet 场景（如 Kotlin Coroutines）更友好。

**改造量**：1 个文件（`FeignRequestInterceptor.java`），修改 ~5 行

**4. 向 gateway 模块推广 ContextKey**

Gateway 是 reactive 栈，TTL 不可用，但可以用 reactor context。建议在 gateway 模块的 Filter 中使用 `ContextKey` 常量（而非硬编码字符串）作为上下文键名，确保与 servlet 端 key 一致。

**改造量**：gateway 多个 Filter，~10 行

### P1 短期：清理冗余依赖

**5. 移除 `ydsz-common-redis`、`ydsz-common-cache` 对 core 的依赖**

42 + 68 = 110 个文件零使用，纯冗余依赖。`<dependency>` 从 pom.xml 删除即可，行为无变化。

**6. 评估合并或下沉低使用常量类**

- `TokenConstants`（仅 1 个引用方）→ 评估是否合并到 `HeaderConstants` 或下沉到 auth 模块
- `FilterIgnoreConstant`（2 个引用方）→ 服务名清单迁移到 web 模块，URL 模式保留在 core

### P2 中期：架构标准化

**7. 增加 trace 使用文档 + 接入检查**

在 `docs/adr/` 中增加 ADR-006，规范各模块接入 trace 的方式：
- servlet 模块 → Filter 写入 MDC/RequestContext，客户端拦截器用 `TraceIdPropagation`
- reactive 模块 → Reactor context + `ContextKey` 常量

**8. RequestContext.Key 推广计划**

`ContextKey<T>` 已创建但无人使用。在架构规范中推广：所有模块新增上下文键时必须使用 `ContextKey`（而非字符串 key），逐步替换现有字符串键。

---

## 五、热度总结与核心建议

```
                  引用文件数
                  ├── response ──── 180+ ── 核心高价值 ✓
                  ├── code ───────── 100+ ── 异常链路贯穿 ✓
                  ├── constant ────── 37  ── 中等但集中
                  ├── context ──────── 8  ── 推广不足 ✗
                  ├── config ───────── 1  ── 设计合理（Spring auto-binding）
                  └── trace ────────── 0  ── 能力闲置 ✗✗✗
```

**一句话结论**：`response` 和 `code` 体系贯通良好，`constant` 基本满足需求；但 **trace 能力完全闲置（wasted work）**——高性能生成器和 W3C 标准已就绪却无一模块接入，这是本报告发现的最高优先级问题。同时 3 个模块声明了 core 依赖但零使用，属于冗余依赖。

