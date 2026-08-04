# ydsz-common-core 全局引用分析与优化建议报告

> **分析日期**: 2026-08-04  
> **分析范围**: ydsz-backend 全部 11 个顶级模块（9 个业务模块 + Gateway + ydsz-common）  
> **分析方法**: 静态代码扫描 + 依赖链追踪 + 使用模式分类统计  

---

## 一、模块定位与能力清单

### 1.1 架构定位

`ydsz-common-core` 是 ydsz-backend 公共底座（ydsz-common）的 **L1 基础设施层**，为全项目提供最小化核心抽象：

```
ydsz-common-core (L1: 核心抽象)
    ↑
    ├── ydsz-common-domain (L3: DDD 领域基类)
    ├── ydsz-common-exception (L3: 异常体系)
    ├── ydsz-common-base (L6: HTTP 基座)
    └── ydsz-common-web (L6: Web 端基座) → 所有业务模块 -web/-server
```

### 1.2 能力清单（6 个包、15 个源文件）

| 包名 | 核心类 | 能力描述 |
|------|--------|----------|
| `code` | `ResultCode`（接口）、`BaseResultCode`（枚举, 57 个值） | 统一结果码体系 |
| `config` | `CoreAutoConfiguration`、`CoreProperties`、`SpringMessageResolver` | 自动配置与 i18n 消息解析 |
| `constant` | `HeaderConstants`(19 个头)、`PageConstants`、`SystemConstants` | 全局常量 |
| `context` | `RequestContext`（TTL 上下文）、`ProblemDetail`（RFC 7807） | 请求上下文与错误详情 |
| `response` | `BaseResponse`、`PageResponse`、`IResponse` | 统一 API 响应模型 |
| `trace` | `TraceIdGenerator`、`TraceIdPropagation` | 链路追踪生成与传播 |

---

## 二、各业务模块集成度评估

### 2.1 依赖方式汇总

| 业务模块 | `-api` 子模块 | `-web` 子模块 | `-server` 子模块 | `-domain` 子模块 |
|---------|-------------|-------------|-----------------|-----------------|
| **显式声明 core** | ✅ 全部 9 个 | ❌ | ❌ | ❌ |
| **传递依赖路径** | `feign → core` | `web → base → core` | `web → base → core` | 仅 2 个模块有引用 |

**关键事实**: 除 Gateway 外，没有任何业务模块的 `-web`/`-server`/`-domain` 显式声明对 core 的依赖，全部通过 `ydsz-common-web → ydsz-common-base → ydsz-common-core` 三级传递获取。

### 2.2 代码引用量统计

| 业务模块 | `import com.njydsz.common.core` 文件数 | 主要使用场景 |
|---------|--------------------------------------|-------------|
| **ydsz-workflow** | **85** | Controller 返回 BaseResponse、Service 用 AuthContext |
| **ydsz-message** | **52** | Controller 返回、Feign Fallback、消息常量 |
| **ydsz-project** | **39** | Controller 返回、Feign Client/Fallback |
| **ydsz-cronjob** | **33** | Controller 返回、Feign Client/Fallback + 自定义硬编码错误码 |
| **ydsz-literule** | **27** | Controller 返回、Feign Client/Fallback |
| **ydsz-nextwiki** | **21** | Controller 返回、Domain 层直接引用 PageConstants |
| **ydsz-userinfo** | **16** | Controller 返回、登陆过滤器 |
| **ydsz-system** | **12** | Controller 返回、字典/配置接口 |
| **ydsz-agent** | **11** | Controller 返回、Feign Fallback |
| **ydsz-gateway** | **11** | Error 配置、认证过滤器、TraceId 生成 |

**总计: ~296 个 Java 文件引用 core**，占全项目业务代码的较大比例。

### 2.3 集成成熟度热力图

```
能力维度         │ 利用率 │ 评价
────────────────┼────────┼──────────────────────
BaseResponse    │ ██████ │ ⭐⭐⭐⭐⭐ 100% 统一
TraceId 追踪     │ ██████ │ ⭐⭐⭐⭐⭐ 完整传播链
HeaderConstants │ ██████ │ ⭐⭐⭐⭐⭐ 全模块统一
PageConstants   │ ████░░ │ ⭐⭐⭐⭐  domain 层使用充分
SystemConstants │ ███░░░ │ ⭐⭐⭐   部分模块使用
RequestContext  │ ██░░░░ │ ⭐⭐    仅 common 层使用
BaseResultCode  │ █░░░░░ │ ⭐      几乎被绕过
ResultCode 接口  │ ░░░░░░ │ 0      无任何业务模块实现
ProblemDetail   │ ░░░░░░ │ 0      无任何业务模块使用
CoreProperties  │ ░░░░░░ │ 0      无配置覆盖，全用默认值
```

---

## 三、核心问题诊断

### 🔴 P0: 双轨错误码体系（严重）

这是本次分析发现的**最严重架构问题**。

**现状**: 项目存在两套互不兼容的错误码体系：

| 维度 | 体系A: ResultCode (core) | 体系B: ExceptionCode (exception) |
|------|--------------------------|----------------------------------|
| 接口 | `ResultCode` | `ExceptionCode` |
| 系统枚举 | `BaseResultCode` (57个值) | `UnifiedExceptionCode` (60个值) |
| 业务枚举实现 | **无** | 全部 9 个模块的业务枚举都实现它 |
| 编码格式 | `A10101` | `A01051` / `B40001` |
| 与 BaseResponse 集成 | `BaseResponse.error(ResultCode)` 走 i18n | 通过 Handler 转 `error(String code, String msg)` 不走 i18n |

**后果**:
1. **BaseResultCode 57 个标准错误码形同虚设** — 业务模块全部绕过了它
2. **BaseResponse 的 4 个 ResultCode 重载方法无法用于业务模块**（所有业务枚举都不实现 ResultCode）
3. **同一场景三种不同 code**: `BaseResultCode.NOT_FOUND="A10101"` / `UnifiedExceptionCode.NOT_FOUND="A04051"` / `ProjectResultCode.PROJECT_NOT_FOUND="B40001"`
4. **编码冲突**: `BaseResultCode` 中 B70001-B70003（工作流）与 `WorkflowResultCode` 同码不同义；B30001-B30005（用户）与 `UserInfoResultCode` 重复定义
5. **HTTP 状态码丢失**: `BusinessException(ResultCode)` 构造器硬编码 HTTP 400，丢失原始状态码信息

### 🔴 P0: Controller 层错误码大面积缺失（严重）

统计发现，业务模块 Controller 中 **80+ 处** `BaseResponse.error()` 调用不带任何错误码：

```java
// ❌ 反模式：占 Controller 中 error() 调用的 ~85%
return BaseResponse.error("文件不能为空");               // code 默认 "A99999"
return BaseResponse.error("规则不存在: " + ruleCode);    // code 默认 "A99999"
return BaseResponse.error("Agent not found: " + id);    // code 默认 "A99999"
```

而正确的带码调用仅约 8 处：
```java
// ✅ 推荐模式：仅占 ~15%
return BaseResponse.error(BaseResultCode.NOT_FOUND, "任务不存在: " + taskId);
```

**影响**: 前端收到 80+ 种不同错误但只看到一个统一的 `A99999` 错误码，无法做差异化处理。

### 🟡 P1: 双轨 ProblemDetail（中等）

项目存在两套独立的 `ProblemDetail` 实现：

| 版本 | 位置 | API | 字段差异 |
|------|------|-----|---------|
| core 版 | `common-core/context/ProblemDetail.java` | `of(ResultCode, detail)` | type/title/status/detail/instance |
| exception 版 | `common-exception/model/ProblemDetail.java` | `of(type, title, status, detail)` | +traceId/requestId/timestamp/errorCode/extensions |

**两套实现均未被任何业务模块直接使用**，仅 exception 版被 Handler 内部调用。

### 🟡 P1: 三套租户上下文（中等）

```
RequestContext          ← common-core (TTL, 原生)
    ↑ 同步写入
TenantContext           ← common-security (委托给 RequestContext)
    ≠ 不互通
TenantContextHolder     ← common-tenant (独立 TTL，完全不共享)
```

`TenantContextHolder` 使用独立的 `TransmittableThreadLocal`，不与 `RequestContext` 共享数据。如果在同一个请求中分别通过两个 API 写入/读取租户 ID，会出现数据不一致。

### 🟠 P2: 业务代码直接用字符串 error（轻微但面广）

Gateway 模块使用 `BaseResponse.error("403", "error.IP_FORBIDDEN")` 等硬编码数字，不入两套体系。

### 🟢 亮点：已规范使用的部分

| 亮点 | 说明 |
|------|------|
| BaseResponse 100% 统一 | 所有 Controller 统一返回 BaseResponse/PageResponse，无重复封装 |
| TraceId 全链路打通 | Gateway → TraceFilter → MDC → Feign → 下游，完整传播 |
| HeaderConstants 全量复用 | 19 个 HTTP 头常量在 auth/feign/jdbc/web 等模块统一引用 |
| PageConstants 正常化 | normalizePageSize/normalizePageNum/calcOffset 被 domain 层复用 |
| 非侵入式架构 | 不强制继承 BaseController/BaseService，但通过 Advice/Filter/AOP 提供能力 |

---

## 四、未被充分使用的公共能力

除了 core 模块本身，ydsz-common 体系中有大量能力未被业务模块充分利用：

| 公共能力 | 所在模块 | 使用现状 | 建议 |
|---------|---------|---------|------|
| `BaseDTO` | common-domain | **0 个业务模块继承**，所有 DTO 都手动写 operatorId/tenantId 字段 | 业务 DTO 继承 BaseDTO 可自动携带审计上下文 |
| `BaseQuery` | common-domain | 少数模块使用，大部分自己写 Query 类 | 统一继承 BaseQuery 获得 searchKey/status/时间范围/租户过滤 |
| `DomainEvent` | common-domain | `BaseEntity` 内置事件管理但未见业务模块使用 | DDD 事件驱动可解耦模块间依赖 |
| `TreeNode` + `TreeBuilder` | common-domain | 未见业务模块使用 | 组织树/菜单/分类等场景可直接复用，含 DFS/BFS/循环检测 |
| `BaseStatusEnum` | common-domain | 未见业务模块实现 | 状态机流转约束可防止非法状态跃迁 |
| `AbstractModuleMetrics` | common-base | 部分模块继承 | 强制所有模块实现统一指标，接入 Prometheus |
| `BaseGlobalResponseAdvice` | common-base | Web/App 端各一个子类 | 已在用，较充分 |
| `@DomainService` | common-domain | 仅 nextwiki 使用 | DDD 风格标记，建议推广 |
| `@SoftDelete` / `@Version` | common-domain | 未见使用 | 替代手写 deleted/version 注解 |
| `LogBase` | common-domain | 日志型表未使用 | 避免日志表携带无意义的乐观锁/软删除字段 |

---

## 五、优化建议（分阶段可落地）

### 阶段一：止血（2-3 周，解决 P0 问题）

#### 5.1.1 统一错误码体系

**方案**: 让 `ExceptionCode` 继承 `ResultCode`，或让 `ResultCode` 扩展以兼容 `ExceptionCode`。

```java
// 方案A（推荐）：让 ExceptionCode 继承 ResultCode
// 文件: common-exception/enums/ExceptionCode.java
public interface ExceptionCode extends ResultCode {
    String getKey();         // 保留 i18n key
    // 其余从 ResultCode 继承: getCode(), getMsg(), getHttpStatusCode(), getMessageKey()
    
    @Override
    default String getMsg() {
        // 默认从 MessageSource 解析，fallback 到 key
        return resolveFromMessageSource(getKey());
    }
}
```

**收益**:
- 所有业务模块的错误码枚举自动兼容 `BaseResponse.error(ResultCode)` 及其 i18n 链路
- `BaseResultCode` 和 `UnifiedExceptionCode` 可逐步合并
- 一次改造，永久消除双轨

#### 5.1.2 清理 BaseResultCode 中的业务码

**方案**: 从 `BaseResultCode` 中移除以下枚举值，避免与业务模块冲突：

```diff
- WORKFLOW_NOT_FOUND("B70001", ...)
- WORKFLOW_REJECT("B70002", ...)
- WORKFLOW_NO_PERMISSION("B70003", ...)
- USER_NOT_FOUND("B30001", ...)
- PASSWORD_INCORRECT("B30002", ...)
- USER_DISABLED("B30003", ...)
- USER_LOCKED("B30004", ...)
- USERNAME_DUPLICATE("B30005", ...)
- DEPARTMENT_NOT_FOUND("B30101", ...)
- EMPLOYEE_NOT_FOUND("B30201", ...)
```

这些错误码应由对应的业务模块 (`WorkflowResultCode`、`UserInfoResultCode`) 统一定义和维护。BaseResultCode 仅保留**跨模块通用**的系统级错误码。

#### 5.1.3 Controller 层错误码补全（推荐引入检查工具）

**方案**: 
1. 为所有 `BaseResponse.error("...")` 调用补充正确的错误码
2. 引入 ArchUnit 或 Checkstyle 规则，禁止无 code 参数的 `error()` 调用（或至少在 CI 中产生 Warning）

```java
// ❌ 禁止
BaseResponse.error("文件不能为空");

// ✅ 替代
BaseResponse.error(FileResultCode.FILE_EMPTY);

// ✅ 或至少
BaseResponse.error(BaseResultCode.BAD_REQUEST, "文件不能为空");
```

预期需要修改约 80 处，可分模块逐步推进。

### 阶段二：打通（4-6 周，解决 P1 问题）

#### 5.2.1 合并 ProblemDetail 实现

**方案**: 将 exception 版的 ProblemDetail 增强字段（traceId/requestId/timestamp/extensions）合并到 core 版，业务模块统一使用 core 版。

```
ydsz-common-core/context/ProblemDetail.java  ← 唯一实现
    ├── of(ResultCode, detail)               ← 保留
    ├── 新增: traceId, requestId, timestamp, errorCode, extensions
    └── ydsz-common-exception 中的版本移除
```

#### 5.2.2 统一租户上下文

**方案**: `TenantContextHolder` 改为委托 `RequestContext`：

```java
// ydsz-common-tenant/TenantContextHolder.java
public final class TenantContextHolder {
    public static void setCurrentTenantId(String tenantId) {
        RequestContext.setTenantId(tenantId);  // 改为委托
    }
    public static String getCurrentTenantId() {
        return RequestContext.getTenantId();    // 改为委托
    }
}
```

#### 5.2.3 Gateway 错误码纳入统一体系

**方案**: Gateway 模块的 `BaseResponse.error("403", ...)` 改为使用 `BaseResultCode` 或 `GatewayResultCode`（实现 ExceptionCode）。

### 阶段三：深化（6-8 周，提升公共能力利用率）

#### 5.3.1 推广 DDD 建模基类

| 建议 | 对象模块 | 说明 |
|------|---------|------|
| 业务 Entity 统一继承 `MpBaseEntity<String>` | 全部 | 已广泛使用，继续强化 |
| 业务 DTO 继承 `BaseDTO` | 全部 | 自动获得 operatorId/tenantId/traceId |
| 业务 Query 继承 `BaseQuery` | 全部 | 统一 searchKey/status/时间范围/租户 |
| 树形数据使用 `TreeNode` + `TreeBuilder` | system/userinfo/project | 菜单/组织/分类统一建模 |
| 状态枚举实现 `BaseStatusEnum` | 全部 | 状态流转约束，防止非法跃迁 |
| 日志类实体继承 `LogBase` | 全部 | 避免日志表无意义的乐观锁/软删除 |

#### 5.3.2 建立领域事件机制

**方案**: 基于 `BaseEntity.registerEvent()` + `DomainEvent` 建立跨模块事件总线。

```
订单完成 → DomainEvent("project.order.completed")
    ├── message 模块监听 → 发送通知
    ├── cronjob 模块监听 → 触发后续任务
    └── audit 模块监听 → 记录审计日志
```

当前各模块通过直接 Feign 调用实现"事件通知"，导致模块间强耦合。领域事件可解耦这种依赖。

#### 5.3.3 强化可观测性

**方案**:
1. 所有业务模块强制继承 `AbstractModuleMetrics`，输出统一 Prometheus 指标
2. `BaseResponse` 构造器中加入 Micrometer Timer 记录
3. `RequestContext.dump()` 落地到审计日志

#### 5.3.4 补全显式依赖声明

**方案**: 所有直接使用了 `com.njydsz.common.core` 中类的模块，应在 pom.xml 中**显式声明**对 core 的依赖，而非依赖三级传递。

这可以防止未来 common-web 或 common-base 调整依赖时导致的编译中断。

---

## 六、优化优先级矩阵

```
影响面大
    │
    │  P0-2 清理BaseResultCode业务码    P0-1 统一错误码体系
    │  P2-1 引入error()代码检查工具      P0-3 Controller错误码补全
    │
    │  P1-2 统一租户上下文               P1-1 合并ProblemDetail
    │  P1-3 Gateway错误码纳管
    │
    │  P3-3 强化可观测性                 P3-1 推广DDD建模基类
    │  P3-4 补全显式依赖声明              P3-2 建立领域事件机制
    │
    └──────────────────────────────────────────
                      实施难度大 →
```

---

## 七、监控与度量建议

为持续追踪优化效果，建议建立以下度量指标：

| 指标 | 当前基线 | 目标值 | 监控方式 |
|------|---------|--------|---------|
| Controller 无码 error() 占比 | ~85% | <5% | ArchUnit / Checkstyle |
| 业务枚举实现 ResultCode 接口 | 0% | 100% | 静态扫描 |
| BaseResultCode 含业务码数量 | 11 个 | 0 个 | Code Review |
| 重复 ProblemDetail 实现 | 2 套 | 1 套 | 文件扫描 |
| 独立租户 Holder 数量 | 3 个 | 1 个 | 代码审计 |
| 业务 Entity 继承基类率 | ~80% (MpBaseEntity) | 100% | 静态扫描 |
| 业务 DTO 继承 BaseDTO 率 | 0% | >80% | 静态扫描 |

---

## 八、总结

`ydsz-common-core` 的设计理念是正确且先进的：通过最小化核心抽象（响应模型、上下文、常量、追踪）为全项目提供统一底座。但在实际落地过程中，出现了以下关键偏离：

1. **错误码体系分裂** — 这是最需要优先解决的架构债。双轨体系导致 BaseResultCode 的 57 个精心设计的标准错误码被架空，BaseResponse 的 ResultCode 参数化方法无法使用。
2. **Controller 层"裸奔"** — 85% 的错误响应不带语义化错误码，前端无法做差异化处理。
3. **公共能力沉睡** — BaseDTO、BaseQuery、TreeNode、BaseStatusEnum、DomainEvent 等精心设计的基类基本未被业务模块继承使用，导致大量重复代码。
4. **上下文碎片化** — 三套租户上下文互不通信，隐含数据一致性风险。

建议按"止血→打通→深化"三阶段推进，优先解决错误码体系统一（工期约 2-3 周），再逐步提升公共能力的利用率。

---

## 九、实施进展 (2026-08-04)

### ✅ 已完成

| 编号 | 项目 | 变更文件数 | 详情 |
|------|------|-----------|------|
| **P0-1** | 统一错误码体系 | 2 | `ExceptionCode extends ResultCode`；`BusinessException(ResultCode)` 保留 HTTP 状态码 |
| **P0-2** | 清理 BaseResultCode 业务码 | 1 | 11 个业务码标记 `@Deprecated`，指明迁移方向 |
| **P0-3** | Controller 错误码补全 | 25 | 62 处 `BaseResponse.error()` 补充正确错误码；33 条 import 添加 |
| **P1-1** | 合并 ProblemDetail | 4 | 增强 core 版（+traceId/requestId/errorCode/timestamp/extensions），删除 exception 版 |
| **P1-2** | 统一租户上下文 | 1 | `TenantContextHolder` 双向同步 `RequestContext` |
| **P1-3** | Gateway 错误码纳管 | 6 | 8 处硬编码数字替换为 `BaseResultCode` 枚举 |
| **P3-4** | 补全显式依赖声明 | 17 | 17 个 `-web`/`-server` 子模块 pom.xml 添加显式 `ydsz-common-core` 依赖 |
| **P3-1/2** | DDD 建模基类指南 | 1 | 编写 `ddd-common-patterns-guide.md`（BaseDTO/BaseQuery/TreeNode/DomainEvent） |
| **杂项** | MessageResultCode 注解 | 1 | 补全 `@YdszResultCode` 注解 |
| **杂项** | RepeatSubmitTokenController | 1 | 硬编码 "401" → `BaseResultCode.UNAUTHORIZED` |

**累计变更：59 个文件，83+ 处代码修改**

### 📋 建议后续跟进

| 项目 | 范围 | 备注 |
|------|------|------|
| 引入 ArchUnit 检查 | CI/CD | 禁止无 code 的 `BaseResponse.error()` 调用 |
| 推广 DTO 继承 BaseDTO | 全部 9 个模块 | 参考 `ddd-common-patterns-guide.md` |
| 推广 Query 继承 BaseQuery | 全部查询模块 | 参考 `ddd-common-patterns-guide.md` |
| 树形使用 TreeNode | system/userinfo/project | 参考 `ddd-common-patterns-guide.md` |
| 领域事件解耦 | project→message/cronjob | 参考 `ddd-common-patterns-guide.md` |
| 强化可观测性 | 全部模块 | 继承 AbstractModuleMetrics，Prometheus 指标 |

---

> **附录**: 各业务模块 -api 子模块的 pom.xml 均显式声明了 ydsz-common-core 依赖，`-web`/`-server` 通过 `ydsz-common-web → ydsz-common-base → ydsz-common-core` 三级传递获取。Gateway 是唯一直接依赖 core 的非 common 单体模块（因 Reactive 栈不能依赖 ydsz-common-web）。
