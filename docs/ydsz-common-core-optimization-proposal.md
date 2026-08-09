# ydsz-common-core 模块优化完善建议

> 基于 `ydsz-common/ydsz-common-core` 当前源码（16 个类、约 3153 行）的逐文件审查。
> 对标参考：阿里巴巴《Java 开发手册》错误码规范、Spring Boot 4.x 自动配置/Health 规范、
> Netflix/Spring 全链路追踪与上下文传递实践、Google/Spring "core 模块保持轻量与无业务语义" 的基线要求。
>
> 结论速览：模块**基础设计质量较高**（TTL 上下文、类型安全键、数据驱动错误码、W3C traceparent、启动期配置校验都做对了），
> 但存在**三层主要债务**——① 业务语义泄漏进最底层 core（"上帝上下文"）；② 同一能力多套并行 API（API 蔓延）；
> ③ 个别文档与代码不一致 / 死代码。以下按五个维度给出可落地建议，并标注优先级（P0 高 / P1 中 / P2 低）。

---

## 0. 现状画像（事实基线）

| 维度 | 现状 | 评价 |
|---|---|---|
| 上下文载体 | `RequestContext`（1050 行）基于 TTL，含命名键、ContextKey、Builder、MDC 桥接、snapshot/restore、业务存取器 | 能力强，但已"上帝化" |
| 响应模型 | `BaseResponse<T>`（514 行）含 code/msg/data/traceId/timestamp + 分页字段 + extensions | 职责过载 |
| 错误码 | `ResultCode` 接口 + `BaseResultCode` 枚举，数据驱动 HTTP 状态（无 switch） | 规范、对齐阿某巴 |
| 链路追踪 | `TraceIdGenerator`（ThreadLocalRandom+HexFormat）+ `TraceIdPropagation`（X-Trace-Id + W3C traceparent） | 现代、无框架依赖 |
| 配置 | `CoreProperties`（`@Validated` + `@AssertTrue`）+ `CoreAutoConfiguration`（`@ConditionalOnProperty`/`@ConditionalOnBean`） | 规范 |
| 自动配置 | 已通过 `META-INF/spring/...AutoConfiguration.imports` 注册 | 符合 Spring Boot 3+ 规范 |
| i18n | `src/main/resources/i18n/core/messages*.properties` + `SpringMessageResolver` 适配器 | 已具备，但键约定需统一说明 |
| 健康 | `CoreHealthIndicator`（仅 UP + 硬编码版本） | **未被注册为 Bean，等同失效** |

`@since` 版本跨度 1.0.0 → 1.9.0，**1.9.0 一次性灌入大量业务键与分页字段**，是债务主要来源——增量式堆叠、缺少阶段性重构。

---

## 1. 架构优化（Architecture）

### A1【P0】业务语义泄漏到最底层 core：重构"上帝上下文"
`RequestContext` 在 `@since 1.9.0` 直接定义了属于**认证/数据权限/审计**模块的键与存取器：
`KEY_AUTH_INFO`、`KEY_LOGIN_USER`、`KEY_TENANT_CONTEXT`、`KEY_COLUMN_PERMISSION`、`KEY_AUDIT_DATA`，
且其类型（`AuthInfo`/`LoginUser`/`TenantContext`/`ColumnPermissionInfo`）**定义在其它模块**，仅靠 `Object` 弱引用承载。
这破坏了"core = 最底层、无业务语义"的基线（pom 自身描述为 *minimal core*）。

**落地建议**：
- 方案一（推荐）：在 core 中定义**跨切面契约接口** `AuthInfo`、`LoginUser`、`ColumnPermission`，由 auth/data-permission 模块实现；`RequestContext` 仅持有接口，不再硬编码业务键与 `Object`。
- 方案二：将上述键与存取器下沉到各自模块的 `XxxRequestContext` 扩展类，core 只保留通用 `get/put(ContextKey)`。
- 两条线都消除"字符串键 + Object"的隐式契约，把 `setAuthInfo(Object)` 等改成编译期类型安全。

### A2【P0】`KEY_HTTP_REQUEST` 存储原生 `HttpServletRequest` —— 反模式
`RequestContext.setHttpRequest(Object)` 把活的 Servlet 请求对象塞进线程上下文。
问题：① 不可序列化、无法安全跨异步/线程池传递；② 在异步边界可能引用已销毁的请求；③ 把 core 与 Servlet API 隐性绑死。

**落地建议**：新增不可变小对象 `RequestSnapshot`（method、path、queryString、remoteAddr、traceId），在入口 Filter 一次性快照后写入；删除 `setHttpRequest/getHttpRequest`。

### A3【P1】响应信封与分页元数据耦合（单一职责）
`BaseResponse` 自 1.9.0 起新增 `total`/`pageNum`/`pageSize` 三个字段，使"通用响应信封"承担了"分页结果"职责。
大厂通行做法是将分页封装为独立值对象 `Page<T>` / `PagedResult<T>`，响应只包一层 `data`。

**落地建议**：新增 `PageResult<T>`（`List<T> rows` + `long total` + `int pageNum` + `int pageSize` + `long totalPages`），
`BaseResponse.successPage(...)` 返回 `BaseResponse<PageResult<T>>`；后续版本标记 `BaseResponse` 上的分页字段为 `@Deprecated` 并迁移。

### A4【P1】`ydsz-common-json` 硬依赖与自定义注解的序列化耦合（需核实）
`BaseResponse` 使用 `com.njydsz.common.json.annotation.@JsonClass/@JsonInclude/@JsonPropertyOrder`（**自定义包**，非 Jackson）。
core 对 `ydsz-common-json` 是**非 optional 硬依赖**。风险：若某消费者未引入 `ydsz-common-json` 或未注册其 Jackson Module，
标准 Jackson 不识别这些注解，序列化结果可能异常。

**落地建议（先核实后决策）**：
- 核实 `ydsz-common-json` 是否通过 `Module`/序列化器读取这些自定义注解；若是，在 core README 明确"必须配合 ydsz-common-json"的硬约束。
- 若 `@JsonClass` 仅用于"反序列化白名单安全"，这是有价值的安全能力，应**标准化并文档化**；若它只是 Jackson 能力的翻版，则属过度设计，建议迁移到标准 Jackson 注解，让 core 保持序列化框架无关。

### A5【P2】`CoreHealthIndicator` 未被注册，版本号硬编码
- README 声称 `CoreAutoConfiguration` 注册 `coreHealthIndicator`，但 `CoreAutoConfiguration` 实际只注册了 `springMessageResolver` 与 `pageConstantsInitializer` —— **健康端点事实不存在**，属于文档/代码不一致 + 功能缺口。
- 版本常量 `VERSION = "1.0.0"` 硬编码，与 pom 的 `1.0.0-SNAPSHOT` 脱节，且 `@since 1.4.0` 与返回版本矛盾。

**落地建议**：在 `CoreAutoConfiguration` 中补注册 `CoreHealthIndicator` Bean（或确认由 starter 注册并修正 README）；版本从 `Implementation-Version`/BuildProperties 读取；如需区分存活/就绪，可拆 `HealthIndicator`（liveness）与 `ReadinessHealthIndicator`（下游 DB/Redis 探测）。

### A6【P2】`SystemConstants.DEFAULT_TENANT_ID="0"` 等硬编码
租户默认值写死在常量里，与运行时配置未关联，跨环境（多租户 SaaS）易踩坑。

**落地建议**：租户默认态交由 `CoreProperties`/租户模块配置，core 只定义"系统用户/SYSTEM"等真正全局常量。

---

## 2. 功能增强（Feature）

### F1【P1】分页"深度分页风险"目前只有查询方法、无生效点
`PageConstants.isOffsetSafe()` / `MAX_SAFE_OFFSET=10000` 提供了判断，但**没有任何调用方**，等于空置。

**落地建议**：在分页归一化出口（如 Web 模块 Resolver 或 `PageConstants.calcOffset`）中，当 `!isOffsetSafe` 时打 WARN 日志/返回 400 提示改用游标分页；或提供 `PageResult.cursorBased(...)` 工厂。

### F2【P1】缺少统一的异常→响应转换入口
`BaseResponse` 留了私有的 `extractResultCode(Throwable)`（**当前无任何调用**，见过度设计节），但对外没有"业务异常→标准响应"的便捷通道。

**落地建议**：提供 `BaseResponse.error(Throwable, ExceptionToResultCodeConverter)` 或 `@ControllerAdvice` 用的 `ResultCodeResolver` 扩展点，由异常模块注册转换器，避免 core 反向依赖异常类型。

### F3【P2】TraceId 可排序性与可观测字段
- `generateTraceId()` 是纯 128-bit 随机，无法按时间排序，不利于日志趋势分析。
- 响应体仅有 `traceId`，缺 `requestId` / `spanId`，前端排障信息不足。

**落地建议**：提供 `generateSortableTraceId()`（ULID / UUIDv7 / 时间戳前缀）；响应信封可选补 `requestId`、`spanId`（默认 null 不序列化）。

### F4【P2】`TraceIdPropagation.traceHeaders()` 在缺 traceId 时返回空 Map
调用方若误用 `traceHeaders()`（而非 `traceHeadersOrCreate()`），会**静默断链**。

**落地建议**：`traceHeaders()` 缺 traceId 时默认 `OrCreate` 语义，或两者合并并加 `@deprecated` 说明；对外只暴露一个"保证非空"的方法。

### F5【P2】上下文异步传播缺"开箱即用"封装
已有 `snapshot/restore`、`bridgeToMdc`、`runWithCleanup`，但异步（`CompletableFuture`/虚拟线程）传播需手动拼装。

**落地建议**：提供 `RequestContext.propagate(Supplier<T>)` / `runAsync(Runnable)`，内部完成 TTL+MDC 快照恢复与清理，降低误用面。

---

## 3. 性能提升（Performance）

### P1【P1】TTL `copy()` 每次线程交接全量克隆 Map
`RequestContext.CONTEXT_HOLDER` 覆写了 `copy()`，父→子线程传递时 `new HashMap<>(parentValue)` 全量拷贝。
若上下文被塞入大对象（如 `KEY_CACHED_USER_INFO_MAP` 用户缓存 Map），高并发异步下拷贝成本被放大。

**落地建议**：
- 把"请求级用户缓存"移出 `RequestContext`（它本质是 auth 模块的缓存优化，不应占用通用上下文）；
- 保持上下文内容精简（基本类型 + 小对象），使拷贝成本恒定且极小；
- 如确需大对象，评估用 `TtlCopier`/不可变快照替代每handoff 深拷贝。

### P2【P2】`BaseResponse` 构造即查 traceId + 取时间戳
`new BaseResponse<>()` 每次都执行 `resolveTraceId()`（RequestContext.get + MDC.get）。单条响应无感，但批量/流场景有微小开销。

**落地建议**：`traceId` 改为懒加载（首次 `getTraceId()` 时解析），或构造时由调用方显式传入，避免无条件查 MDC。

### P3【P2】`snapshot()/dump()` 的防御性拷贝
`Collections.unmodifiableMap(new HashMap<>(holder))` 每次生成新 Map，诊断日志高频调用有分配成本。属正确性优先项，仅建议在超高频诊断路径缓存快照。

---

## 4. 体验改善（Developer Experience）

### E1【P1】同一能力三套并行 API，调用方无所适从
`RequestContext` 存在：① 字符串键 `put(String,Object)/get(String)`；② `ContextKey<T>` 类型安全键；③ 命名存取器 `getUserId()/setAuthInfo(Class)`；外加 `Builder`。新人难以判断"该用哪个"。

**落地建议**：
- 确立 **`ContextKey<T>` 为唯一对外类型安全入口**，保留少量高频命名存取器（userId/tenantId/traceId）作为语法糖；
- 将原始 `put(String,Object)/get(String)` 标记 `@Deprecated`，引导迁移；
- `Builder` 与 `setXxx` 二选一收敛（Builder 适合批量写入，命名 setter 适合单字段，可共存但文档讲清场景）。

### E2【P2】i18n 键约定需显式文档化
实际 `messages.properties` **同时**定义了 `response.success/response.error`（success 路径用）与 `error.BAD_REQUEST` 等（`ResultCode.getMessageKey()` 默认 `error.<NAME>`）。两者都已覆盖、并非缺陷，但存在 `error.SUCCESS` 这类冗余键，且约定散落在代码注释里。

**落地建议**：在 core README 用一节固化约定——`response.*` 仅用于通用成功/失败文案，`error.<枚举名>` 用于错误码文案；删除永不会被错误路径命中的 `error.SUCCESS`。

### E3【P2】统一响应门面（facade）
散落的 `BaseResponse.success/error/error(ResultCode)/successPage` 可收口为一个 `Results` 静态门面，降低记忆成本：`Results.ok(data)`、`Results.fail(BaseResultCode.BIZ_ERROR)`。

### E4【P2】补全单元测试与架构守护
模块仅引入 `spring-boot-starter-test`，但未见测试类落地情况。建议最低补齐：
- `RequestContext` 清理（`CleanupGuard`/`runWithCleanup` 防泄漏）、TTL 子线程传递；
- `PageConstants` 归一化边界（0/null/超上限）、`calcOffset` 不溢出；
- `TraceIdGenerator` 唯一性（批量生成碰撞率为 0）、`traceparentHeader` 格式合规；
- `ResultCode.getMessageKey()` 对**非枚举实现**的行为（见下，当前会抛 `ClassCastException`）。

### E5【P2】Javadoc 中的"性能结论"需严谨
`TraceIdGenerator` 注释写了"约 2.5x 于 UUID"等基准结论。建议在源码注释中标注基准环境/版本，避免给读者误导（或改为指向基准说明文档）。

---

## 5. 过度设计（Over-engineering，建议收敛/删除）

### O1【P0】`BaseResponse.extractResultCode(Throwable)` 是死代码
全模块（`ydsz-common-core`）检索 `extractResultCode` 仅定义、无调用（见 grep 实证）。它意图从异常提取 `ResultCode`，但注释又称"异常模块应另行处理"，自相矛盾。

**落地建议**：直接删除；异常→响应的转换用 F2 的扩展点方案替代。

### O2【P1】`RequestContext` 承载业务缓存（`KEY_CACHED_USER_INFO_MAP`）
"请求级用户信息缓存以避免反复 Redis"是 auth 模块的性能优化，却放在通用上下文里，既膨胀上下文（放大 P1 的拷贝成本），又把缓存语义与 core 耦合。

**落地建议**：下沉到 auth 模块（如 `RequestScoped` Bean 或本地 `ThreadLocal` 缓存），core 只留通用存取。

### O3【P1】`BaseResponse.getExtensions()` 直接返回内部可变 Map
`getExtensions()` 把 `this.extensions` 原样返回，破坏了封装，调用方可任意篡改响应扩展字段。

**落地建议**：返回 `Collections.unmodifiableMap(...)` 或仅保留 `putExtension/getExtension`；如需批量写，提供受控 `editExtensions(Consumer)`。

### O4【P2】`TokenConstants.PREFIX = "ydsz"` 在 core 内无引用
检索显示 `PREFIX` 仅在 `TokenConstants` 自身定义、core 内无使用（可能用于其它模块，但属于 core 的职责外常量）。

**落地建议**：确认无 core 内用途后移至使用方模块，或删除。

### O5【P2】`HeaderConstants` 自述与事实矛盾
类注释声明"标准 HTTP 头直接在代码中使用字符串字面量即可，无需在此定义"，却又定义了 `X-Forwarded-For`（标准头）；`TokenConstants.AUTHENTICATION="Authorization"` 也定义了标准头，与 `HeaderConstants` 的口径冲突。

**落地建议**：统一口径——要么在 `HeaderConstants` 集中收纳所有项目相关头（含标准头），要么明确"仅自定义头"，并移除重复的标准头常量。

### O6【P2】`ResultCode.getMessageKey()` 默认实现对非枚举实现不安全
默认 `return "error." + ((Enum<?>) this).name()` 把 `this` 强转 `Enum`，若有人以**普通类**实现 `ResultCode` 且未覆盖该方法，运行期抛 `ClassCastException`。

**落地建议**：默认实现改为基于 `getCode()`（如 `"error." + getCode()`），或明确"ResultCode 必须以枚举实现"并在接口注释固化。

### O7【P2】`successMsg(String)` 绕过构造器
`successMsg` 用 `new BaseResponse<>()` 后手动 `response.code = SUCCESS`，与其它工厂走构造器不一致，且易在未来构造器加逻辑时遗漏。

**落地建议**：改为 `of(SUCCESS, msg, null)`，与其他工厂统一。

---

## 6. 落地路线图（建议顺序）

| 阶段 | 目标 | 关键项 |
|---|---|---|
| **Phase 1（止血，1 周内）** | 修一致性 & 死代码 | O1 删死代码、A5 注册 HealthIndicator+修 README、O3 封装 getExtensions、O6 修正 getMessageKey |
| **Phase 2（减负，2~4 周）** | 收敛 API 与上下文 | A1 业务键接口化/下沉、A2 去除 HttpServletRequest、E1 统一 ContextKey 入口、O2 移出用户缓存 |
| **Phase 3（演进，1~2 季度）** | 结构升级 | A3 抽出 `PageResult<T>`、A4 明确 JSON 引擎契约、F2 异常转换扩展点、F1 深度分页生效 |
| **持续** | 质量守护 | E4 单测、架构测试（禁止 core 反向依赖业务模块）、依赖收敛审计 |

---

## 7. 与行业规范的对照小结

- ✅ **做对的**：TTL 线程池安全传递、类型安全 `ContextKey`、错误码显式声明 HTTP 状态（遵循"禁止前缀猜测"）、W3C traceparent、启动期 `@Validated` 配置校验、`@ConditionalOnBean(MessageSource)` 优雅降级、自动配置按 Spring Boot 3+ `AutoConfiguration.imports` 注册。
- ⚠️ **待补的**：core 模块零业务语义（对标 Spring/Google core 基线）、响应 DTO 不可变化（对标记录式契约）、分页独立值对象、Health 真实生效、文档与代码一致、删除死代码与并行冗余 API。

> 整体评价：**骨架优秀、长胖需减肥**。优先处理 P0（业务泄漏 + 死代码 + Health 失效），可在不破坏兼容性的前提下显著抬升模块的健康度与可维护性。
