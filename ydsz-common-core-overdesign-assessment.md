# ydsz-common-core 模块过度设计评估报告

> 评估范围：`ydsz-common/ydsz-common-core`
> 评估日期：2026-08-09
> 评估维度：功能-复杂度匹配度、竞品对标、大厂规范、维护成本

---

## 一、模块现状概览

### 1.1 模块定位

`ydsz-common-core` 是 ydsz-backend 公共基础框架的 **L1 基础设施层**，定位为"最小核心"（minimal core），负责提供：

- 统一响应模型与工厂方法
- 基于 TransmittableThreadLocal 的请求上下文传递
- 链路追踪 ID 生成与传播
- 分页参数归一化常量
- HTTP 请求头常量与系统级常量
- 国际化消息解析机制

### 1.2 代码规模

| 维度 | 数值 |
|------|------|
| Java 文件数 | 19 |
| 估算代码行数 | ~2,200 行 |
| 外部依赖 | Lombok、SLF4J、TransmittableThreadLocal、Spring Boot（optional）、Jakarta Validation API、ydsz-common-json |
| 子模块引用方 | common-base、common-web、common-app 及所有业务模块 |

### 1.3 核心组件清单

| 组件 | 路径 | 职责 | 代码行数 |
|------|------|------|----------|
| `IResponse<T>` | response/ | 统一响应接口契约 | ~95 |
| `BaseResponse<T>` | response/ | 响应载体 + 静态工厂 + i18n 解析器持有 | ~500 |
| `PageResult<T>` | response/ | 分页响应信封 | ~90 |
| `Results` | response/ | BaseResponse / PageResult 的门面委托 | ~85 |
| `ResultCode` | code/ | 结果码接口 | ~90 |
| `BaseResultCode` | code/ | 协议级错误码枚举 | ~125 |
| `RequestContext` | context/ | TTL 上下文传递 + Builder + MDC 桥接 | ~1,100 |
| `ContextKey<T>` | context/ | 类型安全上下文键 | ~145 |
| `BizContextKeys` | context/ | 业务上下文键集中定义 | ~48 |
| `RequestSnapshot` | context/ | 不可变请求快照 | ~160 |
| `TraceIdGenerator` | trace/ | TraceId 生成（随机 + 时间有序） | ~188 |
| `TraceIdPropagation` | trace/ | TraceId 传播工具 | ~126 |
| `CoreAutoConfiguration` | config/ | 自动配置入口 | ~72 |
| `CoreProperties` | config/ | 配置属性 | ~84 |
| `SpringMessageResolver` | config/ | i18n 适配器 | ~65 |
| `PageConstants` | constant/ | 分页常量与归一化工具 | ~306 |
| `HeaderConstants` | constant/ | HTTP 请求头常量 | ~282 |
| `SystemConstants` | constant/ | 系统级常量 | ~28 |
| `TokenConstants` | constant/ | Token 相关常量 | ~37 |

---

## 二、竞品横向对标

### 2.1 主流快速开发框架 Core 模块对比

| 维度 | ydsz-common-core | RuoYi-Vue | Jeecg-Boot | Guns |
|------|------------------|-----------|------------|------|
| **响应模型** | IResponse + BaseResponse + PageResult + Results 四层 | R\<T\> 单层 | JeecgResponse 单层 | R\<T\> 单层 |
| **响应字段** | code/msg/data/timestamp/traceId/extensions/total/pageNum/pageSize | code/msg/data | code/message/data | code/message/data/total |
| **错误码** | 接口 + 枚举（含 HTTP 状态码） | 常量类 | 字典表 + 枚举 | 枚举 |
| **上下文传递** | TransmittableThreadLocal + Builder + MDC 桥接 | BaseContextHolder (ThreadLocal) | 无独立上下文 | ThreadLocal |
| **TraceId** | 自研（两种策略 + W3C） | 无独立实现 | Sleuth 集成 | MDC |
| **分页工具** | 归一化 + 深度分页预警 | PageHelper 原生 | PageHelper 原生 | PageHelper 原生 |
| **API 层数** | Base → Results + PageResult（入口分散） | 单入口 R 类 | 单入口 | 单入口 |
| **代码规模** | ~2,200 行 | ~200 行 | ~150 行 | ~180 行 |

### 2.2 互联网大厂研发规范对标

| 规范要点 | ydsz 现状 | 评价 |
|----------|-----------|------|
| 统一返回对象 | R\<T\> 信封结构 | ✅ 符合，但信封字段偏多 |
| 业务成功码与 HTTP 状态码分离 | BaseResultCode 含 httpStatus | ✅ 良好 |
| 异常使用枚举 | 已实现 | ✅ 符合 |
| 避免过度抽象 | 多层抽象（Results、ContextKey、PageResult） | ⚠️ 存在过度抽象倾向 |
| 单职责原则 | BaseResponse 承载 i18n 持有的全局静态状态 | ⚠️ 职责偏多 |
| 最小核心原则 | core 中定义 TokenConstants、HeaderConstants 中的业务头（数据权限） | ⚠️ 部分常量偏业务 |

### 2.3 竞品设计的启示

1. **RuoYi 的极简哲学**：R\<T\> 只解决核心痛点（code/msg/data），扩展交给业务层。
2. **Jeecg 的字典表模式**：错误码通过字典表管理而非 core 枚举硬编码，更易运维。
3. **单入口优于多入口**：Results 与 BaseResponse 并存增加了新人学习成本和 API 选择困难。
4. **分页工具下沉**：主流框架深度集成 PageHelper/MyBatis Plus，自定义归一化工具存在价值但应避免过度封装。

---

## 三、过度设计评估（按功能维度）

### 3.1 P0 级别——显著过度设计（建议紧急优化）

#### P0-1: `Results` 门面类

**现状问题：**

- `Results` 的 8 个静态方法（ok/page/fail）与 `BaseResponse` 的工厂方法高度重叠（100% 委托，零新增逻辑）
- 造成 API 发散：新人面临选择困难——该用 `Results.ok()` 还是 `BaseResponse.success()`？
- 违反 YAGNI 原则：当前无基于 `Results` 的差异化行为
- 类自身注释也承认"不引入新行为、不新增依赖"

**对标竞品：** RuoYi、Jeecg、Guns 均采用单入口，不存在"结果门面 + 响应基础类"的双层工厂。

**优化建议：**

1. 将 `Results` 标记为 `@Deprecated(forRemoval = true)`，引导迁移至 `BaseResponse` / `PageResult` 的静态工厂
2. 在下一主版本（2.0）中移除 `Results`，仅保留 `BaseResponse.success/error` 与 `PageResult.success/error` 两套语义明确的工厂入口
3. 全量替换 `Results.` 使用点为等价 `BaseResponse.` / `PageResult.`

**预期收益：** 模块 API 收敛为单入口，减少 ~85 行代码 + 1 个类的维护成本，降低认知负担。

---

#### P0-2: `RequestContext.Builder` 冗余复杂度

**现状问题：**

`RequestContext.Builder`（约 200 行）提供的批量 setter + `apply()` 模式，与直接调用静态 setter 完全等价：

```java
// Builder 模式（冗余）
RequestContext.builder().userId("u1").tenantId("t1").traceId("tr-1").apply();

// 直接 setter（足够清晰）
RequestContext.setUserId("u1");
RequestContext.setTenantId("t1");
RequestContext.setTraceId("tr-1");
```

Builder 模式适用于"构建不可变对象"或"参数校验复杂"场景，但不适用于当前"收集 setter 到 TTL Map"的场景。

**对标：** RuoYi 的 `BaseContextHolder` 直接提供静态 set/get，无 Builder；Spring 的 `RequestContextHolder` 也是直接操作。

**优化建议：**

1. 标记 `RequestContext.Builder` 为 `@Deprecated`，推荐直接使用静态 setter
2. 移除 Builder 公开 API 后，内部用静态 setter 实现即可
3. 如需"批量设置 + 返回 CleanupGuard"语义，可提取私有辅助方法

**预期收益：** 减少约 200 行代码，降低 API 认知复杂度。

---

#### P0-3: `ContextKey<T>` 泛型抽象的实际收益不足

**现状问题：**

1. 创建 `ContextKey` 需要显式声明 + 引用，90% 场景是简单 userId/tenantId 读取，直接用 String key 更简洁
2. `RequestContext` 内置的 `setUserId/getUserId` 等方法并未使用 `ContextKey`，而是直接用 String key，说明内部实现也不依赖它
3. API 冗余：`put(ContextKey<T>, T)` + `put(String, Object)` 两套并存，`get/remove/getOrDefault` 同理

**对标：** Netty 的 `AttributeKey<T>` 存在是因为高并发读写场景，类型安全收益高；而 TTL Map 是请求内低频读写，String key 足够。

**优化建议：**

1. 保留 `ContextKey<T>` 类但不主动推荐，仅在真正需要类型安全的业务模块中自行使用
2. 移除 `put(ContextKey<T>, T) / get(ContextKey<T>) / getOrDefault(ContextKey<T>) / remove(ContextKey<?>)` 四个重载
3. RequestContext API 从 10+ 个 put/get 重载精简到 3 个核心方法

**预期收益：** API 精简，消除泛型类型擦除认知负担。

---

### 3.2 P1 级别——存在优化空间（建议纳入迭代）

#### P1-1: `BaseResponse` 的多职责耦合

**现状问题：** `BaseResponse` 承担 5 项职责：
1. 响应数据载体
2. 静态工厂
3. **i18n 解析器全局持有**（MessageResolver + AtomicReference）
4. 时间戳 + traceId 自动填充
5. 分页字段（total/pageNum/pageSize）+ 扩展字段

i18n 解析器作为静态持有，使 `BaseResponse` 成为"带全局可变状态的工厂"，违反"纯数据对象"定位。

**优化建议：**
1. 将 i18n 解析逻辑下沉到 `BaseResponseAdvice`（common-base 层已实现）
2. 移除 `setResolverIfAbsent / resolveMessage / isResolverRegistered` 三个方法
3. 分页字段从 `BaseResponse` 中剥离，仅在 `PageResult` 中保留

---

#### P1-2: 分页 API 的双重实现

**现状问题：** 存在两套分页成功响应构造入口：

| 入口 | 返回类型 |
|------|----------|
| `PageResult.success(total, pageNum, pageSize, data)` | `PageResult<T>` (新) |
| `Results.page(total, pageNum, pageSize, data)` | `PageResult<T>` (委托) |
| `BaseResponse.successPage(total, pageNum, pageSize, data)` | `BaseResponse<T>` (旧) |

**优化建议：**
1. `BaseResponse.total/pageNum/pageSize` 字段移除，下沉到 `PageResult`
2. 移除 `BaseResponse.successPage / emptyPage` 两个工厂方法
3. 分页场景统一使用 `PageResult.success / PageResult.empty / PageResult.error`

---

#### P1-3: `BizContextKeys` 与 RequestContext @Deprecated 常量的过渡期冗余

**现状问题：** v1.9 将业务键从 `RequestContext` 下沉至 `BizContextKeys`，但保留 8 个 `@Deprecated` 桥接常量，"core 不应承载业务语义"的观点反而让 BizContextKeys 本身成为过渡产物。

**优化建议：**
1. 标记 `BizContextKeys` 为 `@Deprecated`，各业务模块自行定义 `ContextKey`
2. 删除 `RequestContext` 中的 8 个 `@Deprecated` 桥接常量
3. 通过 ArchUnit enforce 禁止 core 新增业务语义常量

---

#### P1-4: `TraceIdGenerator` 的两种策略维护成本

**现状问题：** 同时维护两种策略：
- `generateTraceId()` — 纯随机 16 bytes（5 行逻辑）
- `generateSortableTraceId()` — UUIDv7 风格（40+ 行 + 自旋 + AtomicLong）

**优化建议：** 默认保留 `generateSortableTraceId`（UUIDv7 行业趋势，时间有序便于排查），移除或内部调用前者。

---

### 3.3 P2 级别——轻微过度或建议观察（可选优化）

#### P2-1: `PageConstants` 编译期常量与运行时值并存
现状合理（Java 注解需要编译期常量约束），不建议调整。

#### P2-2: `TokenConstants` 的独立性
仅含 2 个常量，可合并入 `HeaderConstants`，减少碎片化常量类。

#### P2-3: `BaseResultCode.CODE_MAP` 常驻内存
16 个枚举的 Map 内存可忽略，无需改动。

#### P2-4: `RequestSnapshot` 的引入时机
优秀的 future-proof 设计，保留价值明确。

---

## 四、可落地的优化路线图

### Phase 1：API 收敛（建议 1-2 周）

| 优先级 | 改动项 | 工作量 | 风险 |
|--------|--------|--------|------|
| P0-1 | 全量弃用 `Results` → 迁移至 `BaseResponse/PageResult` 工厂 | 中 | 低 |
| P0-2 | 弃用 `RequestContext.Builder`，引入静态 setter | 低 | 低 |
| P0-3 | 弃用 `ContextKey<T>` 方法重载，保留工具类 | 低 | 中 |

**验证手段：**
- 全量 IDE 搜索 `Results.`、`RequestContext.builder()`、`put(ContextKey` 等调用点，确保替换完整
- 单元测试 + ArchUnit 规则 enforce 禁止新增使用

---

### Phase 2：职责精简（建议 2-4 周）

| 优先级 | 改动项 | 工作量 | 风险 |
|--------|--------|--------|------|
| P1-1 | 将 i18n 解析从 `BaseResponse` 下沉到 `BaseResponseAdvice` | 中 | 高（需回归所有 i18n 调用点） |
| P1-2 | 分页字段从 `BaseResponse` 剥离至 `PageResult` | 中 | 中（影响所有 `successPage` 调用） |
| P1-3 | 清理 `BizContextKeys` + `@Deprecated` 桥接常量 | 低 | 低 |

---

### Phase 3：策略收敛（建议 1 周）

| 优先级 | 改动项 | 工作量 | 风险 |
|--------|--------|--------|------|
| P1-4 | TraceId 策略统一为 UUIDv7 风格 | 低 | 低（兼容现有格式） |
| P2-2 | `TokenConstants` 合并入其他常量类 | 低 | 低 |

---

## 五、优化后的模块目标结构

```
response/
├── IResponse<T>          (不变)
├── BaseResponse<T>       (精简：仅保留 code/msg/data/timestamp/traceId/extensions)
└── PageResult<T>         (承载分页元数据)

code/
├── ResultCode            (不变)
└── BaseResultCode        (不变)

context/
├── RequestContext        (精简：移除 Builder、移除 ContextKey 重载、移除 @Deprecated 桥接)
├── ContextKey<T>         (保留为工具类，不再在 RequestContext 上暴露重载)
└── RequestSnapshot       (不变)

trace/
├── TraceIdGenerator      (精简：只保留一种策略 + W3C 支持)
└── TraceIdPropagation    (不变)

config/
├── CoreAutoConfiguration (不变)
├── CoreProperties        (不变)
└── SpringMessageResolver (不变，或下沉至 common-base)

constant/
├── PageConstants         (不变)
├── HeaderConstants       (合并 TokenConstants)
└── SystemConstants       (不变)
```

**精简后代码规模：** ~1,400 行（减少约 35%）

---

## 六、架构守护规则建议

```java
// 禁止新增"结果门面"类（纯委托）
@ArchTest
static final ArchRule no_result_facade_duplication =
    noClasses().that().haveSimpleNameEndingWith("Results")
        .should().beAssignableTo(BaseResponse.class);

// core 层不直接引用 Servlet API
@ArchTest
static final ArchRule core_should_not_access_servlet =
    noClasses().that().resideInAPackage("..core..")
        .should().dependOnClassesThat().resideInAPackage("jakarta.servlet..");

// RequestContext 不应使用过重的泛型 ContextKey 重载作为主要 API
@ArchTest
static final ArchRule request_context_api_surface_is_minimal =
    methods().that().areDeclaredIn(RequestContext.class)
        .and().haveRawParameterTypes(ContextKey.class)
        .should().beAnnotatedWith(Deprecated.class);
```

---

## 七、总结

ydsz-common-core 作为基础设施层，整体设计质量高于竞品平均水平，体现了团队对"正确抽象"的追求。但在演进过程中，部分设计基于"未来可能需要"的防御性动机，在实际业务场景中并未产生对等的收益。本次评估识别了 **3 项 P0（显著过度）**、**4 项 P1（存在优化空间）** 和 **4 项 P2（轻微过度或观察）**，建议按 Phase 1→2→3 的节奏逐步收敛，在保持"最小核心"定位的同时，将模块认知负担降低 30% 以上。

> **核心原则：** 基础设施层的设计应当像公路——简洁、标准、不易误解；而不是像赛车场——功能丰富但需要高超技巧才能驾驭。
