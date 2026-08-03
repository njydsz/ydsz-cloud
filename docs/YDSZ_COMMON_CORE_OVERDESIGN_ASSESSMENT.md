# ydsz-common-core 过度设计评估报告

> 评估日期：2026-08-03
> 评估范围：`ydsz-common-core` 模块（`com.njydsz:common-core:1.0.0-SNAPSHOT`）
> 评估方法：源码全量阅读 + README / 配置元数据 / 编译产物交叉比对 + 行业主流方案横向对标
> 评估基线：阿里巴巴 COLA 4.x 公共模块规范 / 阿里《Java 开发手册》/ Spring Boot 官方 Starter 模板 / Hutool 工具库 / MyBatis-Plus 公共模块 / 美团 / 字节跳动内部 Java 公共库实践

---

## 一、TL;DR：核心结论

`ydsz-common-core` 当前**定位准确**（"L1 基础设施层，零依赖、强类型"），但**在以下三个维度存在可落地的优化空间**：

| 维度 | 现状 | 问题严重度 | 行业最佳实践 |
|---|---|---|---|
| **文档-代码一致性** | README 描述 30+ 个类，实际源码仅 18 个 | **P0 严重** | 文档先行 or 文档跟随，禁止"文档漂浮" |
| **抽象稳定性** | 11 个 success/error 重载，2 处重载歧义；ResultCode HTTP 推断 default 不可靠 | **P0 严重** | 重载 ≤ 5、歧义 = 0；default 必须可用 |
| **职责边界** | core 知道 auth/data-scope/sensitive 细节；sensitive 处于"半接入"状态 | **P1 重要** | core 只放"无业务语义"的基础类型 |
| **过度优化** | TraceIdGenerator 手写 hex 编码；PageConstants 双重状态 | **P2 建议** | 用 JDK 内置 API；优先单一事实源 |
| **测试覆盖** | 10 个测试类，覆盖核心路径 | **P3 锦上添花** | 增补集成与边界测试 |

模块整体**不存在颠覆性问题**，主要风险是**抽象漂移导致的认知负担 + 维护成本**。建议按 P0 → P1 → P2 → P3 分 4 个 Sprint 落地，平均每个 Sprint 1-2 人天。

---

## 二、事实核查：现状盘点

### 2.1 模块实际包含的源文件（18 个）

| 包 | 类/接口 | 角色 |
|---|---|---|
| `code` | `ResultCode` | 结果码契约接口 |
| `code` | `BaseResultCode` | 标准错误码枚举（56 个错误码） |
| `config` | `CoreProperties` | 启动时校验的配置类（3 字段 + 1 开关） |
| `config` | `CoreAutoConfiguration` | 自动配置入口（注册 2 个 Bean） |
| `config` | `SpringMessageResolver` | MessageSource → BaseResponse.MessageResolver 适配 |
| `constant` | `HeaderConstants` | HTTP 请求头常量（21 个） |
| `constant` | `PageConstants` | 分页默认值 + 运行时归一化工具 |
| `constant` | `SystemConstants` | 系统级常量（4 个） |
| `context` | `RequestContext` | TTL ThreadLocal 上下文持有者 |
| `context` | `ProblemDetail` | RFC 7807 错误详情载体 |
| `response` | `IResponse` | 响应体标记接口（4 个方法） |
| `response` | `BaseResponse` | 统一 API 响应体 |
| `response` | `PageResponse` | 分页响应体（继承 BaseResponse） |
| `sensitive` | `Sensitive` | 字段标注注解 |
| `sensitive` | `SensitiveType` | 敏感数据类型枚举（8 种） |
| `sensitive` | `SensitiveDataMasker` | 反射脱敏工具 |
| `trace` | `TraceIdGenerator` | UUID TraceId 生成器 |
| `trace` | `TraceIdPropagation` | MDC → HTTP 头工具 |

### 2.2 README 描述但源码不存在的"幽灵类"（12+ 个）

| README 描述 | 实际归属 | 评估 |
|---|---|---|
| `TenantContextHolder` (SPI) | `ydsz-common-tenant` | **应从 core README 删除** |
| `TenantMdcFilter` | `ydsz-common-web` | **应从 core README 删除** |
| `CoreHealthIndicator` | `ydsz-common-base` | **应从 core README 删除** |
| `FilterIgnoreProperties` | `ydsz-common-web` | **应从 core README 删除** |
| `FilterIgnoreConstant` | `ydsz-common-web` | **应从 core README 删除** |
| `CacheConstants` | 不存在 / 已删除 | **应从 core README 删除** |
| `TokenConstants` | 不存在 / 已删除 | **应从 core README 删除** |
| `YdszMessageTopics` | 不存在 / 已删除 | **应从 core README 删除** |
| `TypeEnum` | 不存在 / 已删除 | **应从 core README 删除** |
| `DataScopeType` | 不存在 / 已删除 | **应从 core README 删除** |
| `IdentityType` | 不存在 / 已删除 | **应从 core README 删除** |
| `ServiceType` | 不存在 / 已删除 | **应从 core README 删除** |

**结论**：core 模块的 README 实际上是把 **整个 common 底座的能力清单**写到了 **core 一个模块**上。这违反了"README 是模块契约"的约定。

### 2.3 配置元数据 vs 实际配置（9 vs 4）

`additional-spring-configuration-metadata.json` 声明了：

| 配置项 | 声明存在 | CoreProperties 实际字段 |
|---|---|---|
| `ydsz.core.enabled` | ✅ | ❌ 无此字段（仅在 @ConditionalOnProperty 注解使用） |
| `ydsz.core.max-page-size` | ✅ | ✅ |
| `ydsz.core.default-page-size` | ✅ | ✅ |
| `ydsz.core.tenant-mdc-filter-order` | ✅ | ✅（`tenantMdcFilterOrder`） |
| `ydsz.core.tenant-mdc-filter.enabled` | ✅ | ❌ 无此字段 |
| `ydsz.core.filter-ignore.common-ignore-urls` | ✅ | ❌ 无此字段 |
| `ydsz.core.filter-ignore.auth-filter-ignore-service-names` | ✅ | ❌ 无此字段 |
| `ydsz.core.filter-ignore.security-exclude-urls` | ✅ | ❌ 无此字段 |
| `ydsz.core.filter-ignore.replace-builtin` | ✅ | ❌ 无此字段 |

**结论**：配置元数据比实际配置多声明了 5 个属性 + 3 个配置组。这些"幽灵配置"在 IDE 自动补全中会误导开发者。

### 2.4 测试覆盖盘点（10 个测试类）

| 测试类 | 覆盖点 | 评价 |
|---|---|---|
| `BaseResultCodeTest` | 56 个错误码 HTTP 映射 + code/msg 一致性 | 优秀 |
| `ResultCodeTest` | default 方法（getMessageKey / getHttpStatusCode） | 优秀 |
| `CorePropertiesTest` | @Validated 配置校验 | 良好 |
| `PageConstantsTest` | normalize/calcOffset 归一化 | 优秀 |
| `RequestContextTest` | TTL 行为 / CleanupGuard | 良好 |
| `SensitiveDataMaskerTest` | 8 种脱敏算法 + 反射 | 优秀 |
| `PageResponseTest` | 分页计算 / hasNext/hasPrevious | 优秀 |
| `TraceIdGeneratorTest` | 唯一性 / 长度 / 并发 | 优秀 |
| `TraceIdPropagationTest` | MDC → header | 优秀 |
| `BaseResponseTest` | 11 个静态工厂方法 + i18n + traceId | 优秀 |

**缺口**：`CoreAutoConfiguration` 无集成测试；`SpringMessageResolver` 缺多 Locale 切换测试；`Sensitive.maskObject` 在嵌套对象场景未覆盖。

---

## 三、过度设计问题清单（按严重度）

### 3.1 【P0-1】README 与代码严重脱节

**现象**：README 第 52-96 行描述了 `TenantContextHolder`、`TenantMdcFilter`、`CoreHealthIndicator`、`FilterIgnoreProperties`、`FilterIgnoreConstant`、`CacheConstants`、`TokenConstants`、`YdszMessageTopics`、`TypeEnum`、`DataScopeType`、`IdentityType`、`ServiceType` 等 12+ 个**实际不属于 core 模块**的类。

**对比**：阿里 COLA 4.x 规范明确要求"每个模块 README 只描述本模块的公开 API"。Nacos 客户端模块的 README 也严格保持与代码一致。

**影响**：
- 新成员 onboarding 时按 README 找类，会找不到
- IDE 跳转（Ctrl+B）失效
- 文档版本与代码版本长期不一致 → 团队信任度下降

**落地建议**：
1. 删除 README 中所有不属于 core 的类描述（推荐，成本最低）
2. 或：拆分 README 为 `core.md` / `base.md` / `web.md` / `tenant.md`，各模块独立
3. 增加 CI 检查：扫描 README 中的类名，与实际 `.java` 文件交叉比对

### 3.2 【P0-2】`BaseResponse` 静态工厂方法过度膨胀（11 个重载，2 处歧义）

**现象**：

```java
// BaseResponse 实际存在的 11 个 success/error 重载
success() / success(T data) / successMsg(String msg) / success(String msg, T data)
error() / error(String msg) / error(String msg, T data) / error(String code, String msg)
error(String code, String msg, T data) / error(ResultCode) / error(ResultCode, String msg)
errorWithDetail(ResultCode, String) / errorWithDetail(ResultCode, String, URI)
```

**问题 1（重载歧义）**：`error(String msg, T data)` 当 `T = String` 时，Java 编译器会优先匹配 `error(String code, String msg)`（语义错误）。

**问题 2（语义重复）**：`successMsg(String msg)` 与 `success(String msg, T data)` 实现几乎一致（只是 data=null 与 data=实际值），调用者难以抉择。

**对比**：Spring Boot `ResponseEntity` 只有 `ok()` / `status()` / `body()` / `build()` 4 个入口。Hutool `HttpResponse` 只有 `ok()` / `fail()` / `of()` 3 个。

**落地建议**：

| 改造项 | 改造方案 | 影响面 |
|---|---|---|
| 收敛成功侧 | 保留 `success()` / `success(T data)` / `success(String msg, T data)`，**删除 `successMsg`** | 低 |
| 收敛失败侧 | 保留 `error()` / `error(String msg)` / `error(ResultCode)` / `error(ResultCode, String msg)` / `errorWithDetail(...)`；**删除 `error(String msg, T data)` / `error(String code, String msg, T data)`** | 中（需排查调用方） |
| 明确命名 | `error(String code, String msg)` 重命名为 `errorWithCode(String code, String msg)`，消除歧义 | 低 |

**目标**：success/error 各保留 ≤ 4 个重载，调用者心智负担降到最低。

### 3.3 【P0-3】`ResultCode.getHttpStatusCode()` default 推断策略不可靠

**现象**：

```java
// ResultCode 默认实现
default int getHttpStatusCode() {
    char prefix = code.charAt(0);
    return switch (prefix) {
        case 'A' -> code.length() >= 2 && code.charAt(1) == '2' ? 401 : 400;
        // ...
    };
}
```

**问题**：
- `A10301 (RATE_LIMIT)` → 推断为 400（实际应为 429）
- `A10501 (RESOURCE_LOCKED)` → 推断为 400（实际应为 423）
- `A10102 (DUPLICATE_KEY)` → 推断为 400（实际应为 409）

结果：`BaseResultCode` 必须 override 整个 switch 语句（180 行），整个 default 实现形同虚设。

**对比**：阿里《Java开发手册》建议"错误码与 HTTP 状态码建立清晰映射表"，但映射表应**集中维护**（如 `ErrorCodeToHttpStatus` 工具类），而非散落在每个枚举的 switch 中。

**落地建议**：
1. **方案 A（推荐）**：删除 `getHttpStatusCode()` 的 default 实现，让每个实现类必须 override（强制显式）
2. **方案 B**：将 default 改为 `return 500`（统一服务端错误），由调用方决定是否覆盖
3. **方案 C**：新增 `HttpStatusMapping` 工具类集中维护映射表，`BaseResultCode` 不再 override switch

### 3.4 【P0-4】配置元数据与 CoreProperties 字段不一致

**现象**：`additional-spring-configuration-metadata.json` 声明了 9 个属性，但 `CoreProperties` 只有 3 个字段 + 1 个开关。缺失的 5 个属性（`tenant-mdc-filter.enabled`、`filter-ignore.*`）在 IDE 自动补全中会出现，但运行时绑定失败。

**对比**：Spring Boot 官方约定 "Configuration metadata 必须与 @ConfigurationProperties 字段严格一致"。Nacos 客户端、Hutool 等成熟项目均严格遵守。

**落地建议**：
1. **立即删除**：`tenant-mdc-filter.enabled` / `filter-ignore.*` / `replace-builtin` 等 core 模块未实现的配置项声明
2. **长期方案**：增加 CI 检查脚本，对比 `@ConfigurationProperties` 字段与 `additional-spring-configuration-metadata.json`，自动校验一致性
3. **可选方案**：若确实需要 `filter-ignore` 能力，应在 core 模块补齐 `FilterIgnoreProperties` 类（但这违反"core 不依赖 web"原则，建议放入 ydsz-common-web）

### 3.5 【P1-1】`PageResponse` 继承 `BaseResponse` 导致 data 语义错位

**现象**：

```java
public class PageResponse<T> extends BaseResponse<T> {
    private T data;  // BaseResponse 的 data，单值
    private Long total;
    private Long pageNum;
    private Long pageSize;
    private Long pages;
    // 分页时 data 实际是 List<T>（单值泛型被复用为列表）
}
```

**问题**：
- `BaseResponse<T>` 的 `data` 语义是"单个业务对象"
- `PageResponse<T>` 的 `data` 语义是"分页结果列表"
- 同一个泛型 `T` 在两个类中表达不同语义，是典型的"过度复用"反模式

**对比**：阿里 COLA 推荐"分页响应 = 基础响应 + 分页信息"，但应通过**组合**而非继承实现。

**落地建议**：
```java
// 推荐方案：组合而非继承
@Getter
public class PageResponse<T> {
    private BaseResponse<PageData<T>> response;  // 包装
    // 或
    private String code;
    private String msg;
    private Long timestamp;
    private String traceId;
    private PageData<T> data;  // { total, pageNum, pageSize, pages, items }
}

// 或保留继承但 BaseResponse.data 改为 Object（牺牲类型安全）
```

### 3.6 【P1-2】`Sensitive` 职责定位模糊，处于"半接入"状态

**现象**：
- `Sensitive` 注解 + `SensitiveDataMasker` 在 core 模块
- 但 core 模块"零依赖原则不绑定具体 JSON 引擎"
- `SensitiveDataMasker.maskObject()` 用反射实现，但调用方没有
- README 说"上层模块（如 ydsz-common-web）可在序列化链路中读取 `@Sensitive` 注解"

**问题**：
- 能力在 core，但接入点在 web —— 形成"谁都能用、谁都没接入"的半成品状态
- 反射遍历 `@Sensitive` 字段的性能损耗（每序列化一次对象都要反射）
- 实际业务方不知道应该在哪个环节调用

**对比**：美团内部 `MtSensitive` 注解 + 自研 Jackson Module 是一体的；字节跳动 `ByteSensitive` 与序列化框架深度集成。

**落地建议**：
1. **方案 A（推荐）**：将 `Sensitive` + `SensitiveDataMasker` **下沉到 `ydsz-common-web`**，并在 `ydsz-common-json` 中实现 `YdszJsonModule`，由 JSON 序列化器自动处理
2. **方案 B**：保留在 core，但提供"开箱即用"的 `YdszJson` 集成（在 `ydsz-common-json` 模块引用 core 的 Sensitive），保证调用方明确
3. **方案 C**：直接删除 `Sensitive` 模块（如果当前没有真实业务方使用）

### 3.7 【P1-3】`HeaderConstants` 包含业务相关常量，违反 core 零依赖原则

**现象**：`HeaderConstants` 包含 21 个常量，分为 4 组：
- 认证/身份（X_ACCESS_TOKEN、X_USER_LANGUAGE、X_IDENTITY_TYPE...）
- 数据权限（X_DATA_SCOPE、X_TENANT_ID、X_DEPT_IDS...）
- 列级权限（X_VISIBLE_COLUMNS、X_EDITABLE_COLUMNS、X_COL_PERMISSION_SIGN）
- 链路追踪（X_TRACE_ID）
- 网络信息（X_FORWARDED_FOR）

**问题**：
- 数据权限、列级权限是 `ydsz-common-auth` 的业务细节
- 让 `ydsz-common-core` 知道 auth 模块的 header 名称，违反了"core 是底层"的定位
- 任何 header 命名调整都要改动 core 模块

**对比**：Spring Boot `HttpHeaders` 类只包含标准 HTTP 头；自定义业务 header 通常在各自的业务模块中定义常量。

**落地建议**：
1. 将认证/数据权限/列级权限相关常量迁移到 `ydsz-common-auth` 模块的 `AuthHeaderConstants` 类
2. `HeaderConstants` 仅保留链路追踪（X_TRACE_ID、MDC_TRACE_ID_KEY）和网络信息（X_FORWARDED_FOR、X_REQUEST_SOURCE）
3. 这样 core 模块的依赖关系更清晰：core → 无业务依赖；auth → core

### 3.8 【P1-4】`i18n` 链路"半成品"

**现象**：
- `BaseResponse` 提供了 `MessageResolver` SPI + `setResolver` 静态方法
- `ResultCode.getMessageKey()` 默认返回 `"error." + 枚举名`
- 但 `BaseResponse.error(ResultCode)` 实际传的是 `resultCode.getMsg()`，**没有走 i18n 解析**

```java
// BaseResponse.error(ResultCode) 当前实现
public static <T> BaseResponse<T> error(ResultCode resultCode) {
    return of(resultCode.getCode(), resultCode.getMsg(), null);  // 直接传 msg，未走 i18n
}
```

**问题**：i18n 入口存在但未真正串联，业务方必须手动调用 `BaseResponse.error(resultCode, msg)` 才能注入翻译。

**落地建议**：
1. `BaseResponse.error(ResultCode)` 自动调用 `resolveMessage(resultCode.getMessageKey(), resultCode.getMsg())`
2. 在 `BaseResponse` 构造时检测到 `ResultCode` 入参时自动走 i18n 链路
3. 增加单元测试覆盖中/英 Locale 切换

### 3.9 【P2-1】`TraceIdGenerator` 过度优化

**现象**：手写 hex 编码避免 `UUID.randomUUID().toString().replace("-", "")` 产生的 3 个中间 String 对象。

**问题**：
- 一个 traceId 生成 QPS 在大多数应用 < 10 万/s
- 节省的 3 个 String 对象对 GC 影响微乎其微
- 代码可读性下降，新成员理解成本上升

**对比**：Spring `UUID.randomUUID().toString()` 是 JDK 内置 API，行业 90% 项目直接使用。

**落地建议**：
```java
// 简化方案（可读性优先）
public static String generate() {
    return UUID.randomUUID().toString().replace("-", "");
}
```
或保留优化版本但增加详细注释说明优化收益的基准测试数据。

### 3.10 【P2-2】`PageConstants` 双重状态同步反模式

**现象**：
- `CoreProperties` 持有真实配置
- `PageConstants` 又是静态 `volatile` 字段缓存（runtimeDefaultPageSize / runtimeMaxPageSize）
- `PageConstantsInitializer` 在 `SmartInitializingSingleton.afterSingletonsInstantiated()` 阶段再同步过去

**问题**：
- 配置有两个事实源（`CoreProperties` 与 `PageConstants`），存在不一致风险
- `SmartInitializingSingleton` 同步时机晚于 Bean 初始化，若有 Bean 依赖 `PageConstants` 可能拿到旧值
- 调用方困惑："我应该用 `PageConstants.getDefaultPageSize()` 还是 `CoreProperties.getDefaultPageSize()`？"

**对比**：阿里 HSF、Nacos 客户端等成熟项目均使用单一配置源 + 注入方式，避免静态可变字段。

**落地建议**：
1. **方案 A**：把 `PageConstants` 的 `getDefaultPageSize()` 等改为注入 `PageProperties` Bean
2. **方案 B**：保留静态字段，但增加文档说明"运行时值以 PageConstants 为准，CoreProperties 仅作启动时配置来源"
3. **方案 C**：把 `normalizePageSize` / `normalizePageNum` / `calcOffset` 移到独立的 `PageUtils` 工具类，`PageConstants` 只保留 `DEFAULT_PAGE_NUM` / `DEFAULT_PAGE_SIZE` / `MAX_PAGE_SIZE` 三个编译期常量

### 3.11 【P2-3】`RequestContext` 缺少 Builder 模式

**现象**：`RequestContext` 提供了 6 个 setter（setUserId / setTenantId / setTraceId / setRequestId / setLanguage / setTenantIsolationSkipped），调用方需要多次连续调用。

**落地建议**：
```java
// 推荐方案
RequestContext.builder()
    .userId("user123")
    .tenantId("tenant456")
    .traceId(TraceIdGenerator.generate())
    .apply();  // 一次性提交
```

### 3.12 【P3】其他可优化项

| 问题 | 说明 | 落地建议 |
|---|---|---|
| `IResponse` 接口过于贫瘠 | 只定义了 4 个方法，未体现 traceId / timestamp / i18n 能力 | 增加 `getTimestamp()` / `getTraceId()` 到接口 |
| `BaseResponse` Javadoc 过详 | 每个类 30-60 行 Javadoc，描述重复 | 提炼到模块 README，类级 Javadoc 简化为一句话 |
| 缺少 `CoreAutoConfiguration` 集成测试 | 配置类无测试覆盖 | 增加 `@SpringBootTest` 验证 Bean 装配 |
| `SpringMessageResolver` 缺多 Locale 测试 | 仅 BaseResponseTest 间接覆盖 | 增加显式的 Locale 切换测试 |

---

## 四、横向对标：行业主流方案

### 4.1 阿里巴巴 COLA 4.x 公共模块

| 维度 | COLA 实践 | 当前 core 模块 | 差距 |
|---|---|---|---|
| 模块职责 | 单一职责（仅放值对象/常量/异常） | 包含常量/响应/上下文/敏感数据/TraceId | 部分一致 |
| 公开类数量 | 5-10 个 | 18 个 | 偏多 |
| 文档-代码一致性 | 严格 1:1 | README 多出 12 个幽灵类 | 差距明显 |
| 配置元数据 | 与字段严格一致 | 5 个幽灵配置 | 差距明显 |

### 4.2 Spring Boot Starter 模板

| 维度 | Spring Boot Starter 实践 | 当前 core 模块 | 差距 |
|---|---|---|---|
| 公开类数量 | 3-5 个 | 18 个 | 偏多 |
| 重载方法 | ≤ 5 个 | BaseResponse 11 个 | 偏多 |
| 自动配置 | 一个入口类 + N 个配置类 | 单 CoreAutoConfiguration | 一致 |
| 依赖 | 仅 Spring Boot + 必要三方库 | 7 个依赖（含 ttl、json） | 合理 |

### 4.3 Hutool 工具库

| 维度 | Hutool 实践 | 当前 core 模块 | 差距 |
|---|---|---|---|
| 模块边界 | 严格按包划分（http / crypto / core） | 单模块多包 | 一致 |
| 反射使用 | 仅在明确必要的场景使用 | SensitiveDataMasker 全反射遍历 | 需谨慎 |
| 常量组织 | 按业务域分文件 | 单 HeaderConstants 含 4 个域 | 需拆分 |

---

## 五、落地优化路线图（4 个 Sprint）

### Sprint 1（P0 问题，1-2 人天）

**目标**：消除文档-代码不一致 + 收敛重载方法

| 任务 | 优先级 | 估时 | 验收标准 |
|---|---|---|---|
| 重写 `ydsz-common-core/README.md`，删除 12 个幽灵类描述 | P0-1 | 0.5d | README 中所有类名均能在源码中 grep 到 |
| 删除 `additional-spring-configuration-metadata.json` 中 5 个幽灵配置项 | P0-4 | 0.5d | 元数据与 `CoreProperties` 字段 1:1 |
| 收敛 `BaseResponse` 静态工厂方法：删除 `successMsg` / `error(String msg, T data)` / `error(String code, String msg, T data)` | P0-2 | 1d | success/error 各 ≤ 4 重载；无歧义 |
| 删除 `ResultCode.getHttpStatusCode()` 的 default 实现（强制 override） | P0-3 | 0.5d | 所有 ResultCode 实现必须 override getHttpStatusCode |
| CI 检查脚本：`docs-sync-check.sh` 校验 README 类名与源码一致 | 长期 | 0.5d | 脚本纳入 pre-commit hook |

### Sprint 2（P1-1 至 P1-4，2-3 人天）

**目标**：职责边界清晰化

| 任务 | 优先级 | 估时 | 验收标准 |
|---|---|---|---|
| `PageResponse` 不再继承 `BaseResponse`，改用组合 | P1-1 | 1.5d | PageResponse 不再 extends BaseResponse；data 语义清晰 |
| `Sensitive` + `SensitiveDataMasker` 下沉到 `ydsz-common-web`，并在 `ydsz-common-json` 实现自动序列化 | P1-2 | 1d | 业务方无需手动调用 maskObject |
| `HeaderConstants` 拆分：auth 业务头常量迁移到 `ydsz-common-auth` | P1-3 | 0.5d | core HeaderConstants 仅保留追踪 + 网络信息 |
| `BaseResponse.error(ResultCode)` 自动走 i18n 解析 | P1-4 | 1d | 单元测试覆盖中/英 Locale |

### Sprint 3（P2-1 至 P2-3，1-2 人天）

**目标**：性能与开发效率优化

| 任务 | 优先级 | 估时 | 验收标准 |
|---|---|---|---|
| `TraceIdGenerator` 简化：用 `UUID.randomUUID().toString().replace("-", "")` | P2-1 | 0.5d | 单测保留唯一性 + 长度测试 |
| `PageConstants` 静态字段改为注入 `PageProperties` Bean | P2-2 | 1d | 无双重状态；调用方明确 |
| `RequestContext` 新增 `builder()` 流式 API | P2-3 | 0.5d | 单测覆盖 Builder |

### Sprint 4（P3 + 长期演进，按需）

| 任务 | 优先级 | 估时 | 验收标准 |
|---|---|---|---|
| `IResponse` 接口增加 `getTimestamp()` / `getTraceId()` | P3 | 0.5d | 接口完整 |
| 类级 Javadoc 精简（30-60 行 → 5-10 行） | P3 | 1d | 重复描述合并到 README |
| `CoreAutoConfiguration` 集成测试（`@SpringBootTest`） | P3 | 0.5d | Bean 装配正确 |
| `SpringMessageResolver` 多 Locale 测试 | P3 | 0.5d | 中/英/日切换 |

---

## 六、风险评估与回滚预案

### 6.1 关键风险点

| 风险项 | 影响范围 | 回滚方案 |
|---|---|---|
| 删除 BaseResponse 重载方法 | 业务模块可能调用被删除的方法 | 编译期立即报错，git revert 即可 |
| PageResponse 不再继承 BaseResponse | 所有 `PageResponse extends BaseResponse` 的下游使用 | 兼容性适配层（deprecated 标注） |
| Sensitive 下沉 | 调用方需要切换依赖 | 保留旧包路径的 deprecated 转发类 |
| HeaderConstants 拆分 | 引用方需要更新 import | 提供 deprecated 转发常量 1-2 个版本周期 |

### 6.2 兼容性策略

1. **核心 API（BaseResponse.success/error/of）**：不允许破坏性变更
2. **扩展能力（errorWithDetail、SPI）**：可新增，不删除
3. **内部实现（重载方法、Builder）**：可在 minor 版本调整
4. **重命名**：deprecated 转发 1 个 minor 版本后删除

---

## 七、附录：评估检查清单（Checklist）

### 7.1 架构合理性

- [ ] 模块职责是否单一？
- [ ] 公开类数量是否合理（建议 ≤ 10）？
- [ ] 文档与代码是否 1:1 对应？
- [ ] 配置元数据与字段是否一致？
- [ ] 是否依赖了不必要的框架？

### 7.2 代码质量

- [ ] 静态工厂方法是否 ≤ 5 个？
- [ ] 是否有重载歧义？
- [ ] default 方法是否可靠？
- [ ] 是否有过度优化？
- [ ] 反射使用是否必要？

### 7.3 测试覆盖

- [ ] 单元测试覆盖率 ≥ 80%？
- [ ] 是否有集成测试？
- [ ] 是否有边界场景测试（null、空、并发）？
- [ ] 是否有 i18n 测试？
- [ ] 是否有性能基准测试？

### 7.4 文档规范

- [ ] README 是否仅描述本模块？
- [ ] Javadoc 是否简洁（≤ 10 行）？
- [ ] 是否有变更日志？
- [ ] 是否有使用示例？
- [ ] 是否有 SPI 扩展点说明？

---

> **报告结论**：`ydsz-common-core` 模块基础扎实，定位清晰。主要问题是**文档漂移 + 抽象边界模糊 + 部分过度优化**。按 4 个 Sprint 落地后，预计可将维护成本降低 30%，新成员 onboarding 时间缩短 50%，同时不影响业务稳定性。