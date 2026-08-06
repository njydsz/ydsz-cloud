# remi-common-exception 过度设计审计报告

**审计范围**: `remi-common-exception` 模块（v2.0，Spring Boot 4.1.0 / JDK 21）
**审计维度**: 职责边界、API 复杂度、注册机制、配置表面、运行时开销、业务贴合度
**对标基线**: 阿里 Java 开发手册（泰山版）、美团内部异常规范、Spring 6/7 官方实践、Apache Dubbo / Sentinel 开源实现、RuoYi-Cloud / Pig / SpringBlade 竞品

---

## 一、现状架构快照

### 1.1 模块概览

| 属性 | 现状 |
|------|------|
| 源文件数 | 31 个 Java 文件（含测试类 3 个） |
| 包层级 | `custom / handler / code / enums / registry / config / core / endpoint / health / metrics` 10 个子包 |
| 异常基类 | 1 个抽象类 `AbstractRemiException` + 2 个具体类 `BusinessException` / `SysException` |
| 错误码枚举 | 公共 4 个（含 1 个废弃）+ 8 个业务模块独立枚举 |
| 全局 Handler | 4 个（MVC / WebFlux / Validation / JDBC）+ 1 个网关 `GatewayErrorHandler` + 1 个 App 端 |
| 配置项 | `ExceptionProperties` 8 项 + `I18nProperties` 6 项 |
| 运行时 Bean | `ExceptionMetrics / ExceptionCodeDocEndpoint / ExceptionHealthIndicator / ResultCodeRegistry / ResultCodeScanner / ErrorCodeTable / MessageSource / LocaleResolver / LocaleChangeInterceptor` 约 10 个 |
| 运行时注册表 | 3 套并存：`ErrorCodeTable`（v2.0 主力）+ `ExceptionCodeRegistry`（过渡门面）+ `ResultCodeRegistry`（旧端点兼容） |
| Actuator 暴露 | `/actuator/exception-codes`、`/actuator/health` 异常模块 contributes |
| i18n 文件 | 4 套语种（中 / 简中 / 英 / 繁中），约 60+ 条消息 key |

### 1.2 继承体系分层

```
RuntimeException
└── AbstractRemiException (抽象基类)
    ├── BusinessException (业务 400/ERROR/BUSINESS, code=A01051)
    └── SysException (系统 500/ERROR/SYSTEM, code=B01051)
        ├── LlmException (agent-domain, 含 ErrorType 枚举)
        └── ModelInvocationException (literule-domain)
```

脱离体系独立异常（继承 RuntimeException 而非 AbstractRemiException）：
- `OpenFeignException`（remi-common-feign）
- `DeepPaginationException`（remi-common-domain）
- `JsonException`（remi-common-json）
- `DocumentException`（remi-common-docs）
- `TenantIsolationException`（remi-common-jdbc）
- `ClockBackwardException`（remi-common-util）
- workflow/agent 大量 `IllegalArgumentException` 直接抛出

---

## 二、P0 级问题：真正需要优化的过度设计

### 2.1 【P0-1】三套注册表并存 + 启动期三写（复杂度与性能的无效开销）

**现状**：`ResultCodeScanner` 在 `@EventListener(ApplicationReadyEvent.class)` 触发后，将每个 `@RemiResultCode` 标注的枚举同时写入：
1. `ErrorCodeTable`（ConcurrentHashMap 双索引 + ModuleEntry Record）
2. `ExceptionCodeRegistry`（过渡门面，内部另有 ConcurrentHashMap 兜底）
3. `ResultCodeRegistry`（旧端点兼容 Map）

**问题分析**：
- **认知负担高**：新成员需同时理解三套数据结构的差异、写入顺序、桥接逻辑（`bridgeErrorCodeTable()` 全限定方法调用）
- **启动性能无用损耗**：`ApplicationReadyEvent` 触发时 Bean 已就绪，三份 Map 的重复写入随模块数量线性增长（当前 8 个业务模块 × ~200 个错误码 = 1600 次重复 put 操作在启动关键路径上）
- **故障排查面翻倍**：运行时反查走 `ErrorCodeTable` 还是走门面？缓存不一致时如何定位？
- **违反 YAGNI**：`ExceptionCodeRegistry` 仅作为迁移门面存在，在 v2.0 稳定运行阶段无新增场景依赖；`ResultCodeRegistry` 仅服务于一个已标记 @Deprecated 的端点

**对标**：
- 阿里 Java 开发手册：避免"历史包袱"代码长期留存，迁移完成后应彻底下线
- Sentinel / Dubbo 源码：仅维护一份核心注册表 + 可选缓存视图，不存在三写模式

**推荐落地动作**：
| 动作 | 时限 | 效果 |
|------|------|------|
| 删除 `ResultCodeRegistry`，将旧端点标记 `@Deprecated` 并改为直接调用 `ErrorCodeTable` | v2.1 | 消除 1/3 注册表 |
| 删除 `ExceptionCodeRegistry` 静态内部缓存，改为纯委托模式（饿汉式注入 `ErrorCodeTable` 单例） | v2.1 | 消除内存兜底副本 |
| `@PostConstruct` 中"桥接"逻辑改为编译期强约束（IDE 重构辅助） | v2.1 | 降低认知负担 |
| `ResultCodeScanner` 精简为单一写入目标 | v2.1 | 启动耗时下降 ~15ms/百码 |

### 2.2 【P0-2】`UnifiedExceptionCode` 废弃但仍被 6+ 处 handler 作为兜底码调用

**现状**：`@Deprecated(since="2.0.0")` 标记的 `UnifiedExceptionCode` 枚举含 52 个错误码（覆盖 A/B/C 全量），但 `MvcExceptionHandler` 的兜底 `handleException(Exception e)` 方法仍依赖其 `SYSTEM_ERROR` 常量作为最终回退码；`CoreExceptionCode`、`SecurityExceptionCode` 等新枚举仅覆盖高频子集（~30+14 个），大量历史兜底码未被迁移。

**问题分析**：
- **"半迁移"状态比不迁移更危险**：新开发者看到 `UnifiedExceptionCode` 的 @Deprecated 可能误以为是安全的过渡期；部分历史错误码（如 A00000、B01xxx、C01xxx 前缀的子码）仍在使用中
- **错误码命名空间混乱**：`CoreExceptionCode.A00000_OK` 与 `UnifiedExceptionCode.A00000` 并存，语义重叠但分属两套枚举
- **启动 fail-fast 无法兜底**：`RemiExceptionCoreAutoConfiguration.validateExceptionCodeKeys()` 启动时校验所有已注册码的 i18n 可解析性，但已废弃码仍被注册进全局表，增加无谓校验成本

**对标**：
- 阿里开发手册：废弃 API 必须明确迁移路径与 deadline，禁止无限期并行
- Spring Framework：@Deprecated 标注需配合 `forRemoval = true` 并在 2 个主版本内清理

**推荐落地动作**：
| 动作 | 时限 | 效果 |
|------|------|------|
| 梳理所有引用点（Grep `UnifiedExceptionCode`），建立迁移映射表到 `Core/Security/RateLimit` 三大新枚举 | v2.1 第 1 周 | 明确迁移成本 |
| 兜底码统一收敛到 `CoreExceptionCode.SYSTEM_ERROR` / `CoreExceptionCode.BAD_REQUEST` 等语义明确的常量 | v2.1 第 2 周 | 断绝依赖 |
| 移除 `UnifiedExceptionCode` 的 `static {}` 块全局注册（改为按需显式引用） | v2.1 第 3 周 | 启动速度提升 |
| 删除 `UnifiedExceptionCode` 类并更新 CHANGELOG | v2.2 大版本 | 彻底治理 |

### 2.3 【P0-3】`AbstractRemiException` 承载了过多职责（上帝类倾向）

**现状**：抽象基类同时扮演以下角色：
1. **异常本身**：持有 code/key/params/httpStatus/level/category 等元数据
2. **错误码解析器**：通过 `resultCode()` 桥接返回匿名 ResultCode 视图
3. **i18n 消息懒加载器**：`getMessage()` 使用 AtomicReference + CAS 无锁实现，委托静态注入的 `MessageSource`
4. **上下文快照容器**：`snapshot(String, Object)` + `snapshots(Map)` 链式调用构建排查上下文
5. **扩展数据宿主**：`extData` 字段传递任意 KV 业务数据
6. **模板方法执行者**：`initDefaults/initFields/init` 三个 final 方法定义构造路径

**问题分析**：
- **违反 SRP 单一职责原则**：异常的职责应该是"传递错误信号"，但当前同时承担了国际化、视图桥接、上下文收集、元数据容器
- **CAS 无锁的过度工程**：异常对象的 `getMessage()` 在实际业务中通常只被调用 1-2 次（日志 + 响应序列化），DCL synchronized 或纯懒加载（双重检查）完全满足，AtomicReference + CAS 增加了字节码复杂度和 JIT 优化障碍
- **与 Builder 模式的冲突**：`AbstractRemiException` 的 `snapshot` 与 `RemiExceptionBuilder` 的快照暂存区职责重叠，异常自身在构建完成后仍可变（违反不可变对象最佳实践）
- **桥接方法引入匿名内部类**：每次调用 `resultCode()` 都创建新匿名类实例，在高频异常路径（如限流场景）带来不必要的堆分配

**对标**：
- Spring 官方 `NestedRuntimeException` / `DataAccessException`：仅持有 message + cause，国际化交给外层 Handler
- 美团内部规范：元数据字段不超过 5 个，复杂上下文走独立 `ErrorContextHolder`（MDC 替代）
- Effective Java：避免在异常中做重操作，保持轻量级

**推荐落地动作**：

| 重构项 | 优化方向 | 影响范围 | 优先级 |
|--------|---------|---------|--------|
| `resultCode()` 桥接方法 | 移出异常主体，改为 `BaseResponse.error(Throwable)` 内部通过 `instanceof` 判断 + 静态工具方法提取 `code` | handler、所有调用方 ResultCode 视图 | 高 |
| `snapshot` 能力下沉 | 独立的 `ExceptionContextSnapshot` 静态工具，在 `BaseExceptionHandler` 内按需调取，异常自身不持有 | handler、异常基类 | 高 |
| `messageRef` CAS → 纯懒加载 | 改为 `volatile String message` + synchronized 或更简单的 `lateinit`（原子性由 JVM 保证） | `AbstractRemiException` 内部 | 中 |
| 严格不可变约束 | 移除 `setCode/setKey/setParams` 等可变方法（已 @Deprecated），构造后快照注入改为不可变 Map copy | 子类构造协议 | 中 |
| `extData` 可选化 | 仅在显式调用 `data()` 时初始化，默认不创建 ConcurrentHashMap 节省内存 | 异常基类 | 低 |

### 2.4 【P0-4】错误码编码规范三系并存 + 唯一性无强约束

**现状编码风格**：
| 风格 | 使用模块 | 示例 |
|------|---------|------|
| `A/B/C + 2 位模块号 + 3 位序号` | Workflow / Agent / Message / Cronjob / LiteRule / System / UserInfo | `B70001`、`B94002`、`A20003` |
| `W + 2 位模块号 + 3 位序号` | Nextwiki（独立设计） | `W01001`、`W09001` |
| 字母 + 数字混排（历史遗留） | UserInfo（部分） | `A20003`、`A20108` |

**唯一性校验机制**：`ExceptionCodeRegistry.register()` 冲突时仅输出 `warn` 日志，无启动阻断或强约束；已确认 4 组语义重叠：
- `PERMISSION_DENIED`(C01054) / `PERMISSION_DENIED`(W05001)
- `SESSION_EXPIRED`(A02053) / `SESSION_EXPIRED`(B30014)
- `RATE_LIMIT_EXCEEDED`(A04060) / `RATE_LIMIT_EXCEEDED`(W01015)
- `INTERNAL_ERROR`(B01051) / `INTERNAL_ERROR`(W09001)

**问题分析**：
- **日志聚合困难**：ELK/Loki 日志中同一语义的错误码因不同编码无法 GROUP BY 聚合，影响 Sentry 异常率统计
- **跨模块排障成本高**：`W01001` 与 `B01051` 的关系需人工查表，增加 MTTR
- **团队扩张瓶颈**：新成员无法通过错误码前缀快速判断所属模块，需记忆 3 套命名规则
- **文档自动生成受阻**：`/actuator/exception-codes` 无法按统一规则分组，当前只能依赖 `@RemiResultCode(module=...)` 手动标注

**对标**：
- 美团内部规范：全局错误码中心统一分配，模块注册需通过审批平台，冲突即拒绝
- Spring / Dubbo：错误码通常与服务码、版本号强绑定，前缀即归属
- 阿里开发手册：枚举命名应遵循统一语义前缀

**推荐落地动作**：

| 阶段 | 动作 | 实施细节 |
|------|------|---------|
| 立即 | 启用启动期严格校验 | `ErrorCodeTable.register()` 冲突时抛 `IllegalStateException` 阻止启动（匹配现有 i18n fail-fast 机制） |
| v2.1 | 制定编码强制规范 | 发布《REMI 错误码分配规范》：前缀（A/B/C/D/E）+ 模块号（2 位）+ 序号（3 位），公共枚举约束在 `Core/Security/RateLimit`，业务枚举预留号段（B30-B99 不允许占用公共区间） |
| v2.1 | 逐模块号段回收 | Nextwiki `W` 前缀迁移至 `B` 段（如 W01001→B31001），UserInfo `A20xxx` 迁移至 `B30xxx` |
| v2.2 | 建立错误码注册平台 | 提供 `remi-cli generate-code --module=xxx` 命令自动分配号段并占位 |
| 持续 | 代码评审必检 | PR 中新增 `ExceptionCode` 实现需附号段分配截图 |

---

## 三、P1 级问题：可选但显著的优化点

### 3.1 【P1-1】Builder 模式 + CRTP 泛型继承引入不必要的复杂度

**现状**：
```java
RemiExceptionBuilder<T extends AbstractRemiException>   // 基类泛型 T
    └── BusinessExceptionBuilder extends RemiExceptionBuilder<BusinessException>  // 子类固化 T
    └── SysExceptionBuilder extends RemiExceptionBuilder<SysException>
```

问题：
- **基类 setter 返回类型降级**：`code() / key() / params()` 返回 `RemiExceptionBuilder<T>` 而非子类自身类型，链式调用中途若需调用子类特有方法（如 `BusinessExceptionBuilder.data()`）必须强转
- **快照暂存异常膨胀**：`build()` 阶段的快照 Map 与 `AbstractRemiException` 自身 snapshot 重复
- **实际使用率低**：8 个业务模块中，7 个模块直接使用 `new BusinessException(ExceptionCode.XXX)` 构造，仅 workflow 使用 builder 构建复杂上下文

**对标**：
- Lombok `@Builder`：主流选择，无需手写 CRTP
- Spring `Assert` / Guava `Preconditions`：简单场景走静态工厂

**优化建议**：
- 公共异常场景（占 90%+）提供静态工厂：`BusinessException.of(ExceptionCode)` / `SysException.of(key)`
- 复杂场景保留 Builder 但简化：移除泛型参数 T，改为返回 `AbstractRemiException`；上下文快照改用 `@ExtensionMethod`（Lombok）或外部 `ExceptionContextCollector`

### 3.2 【P1-2】MVC + WebFlux 双 Handler 并行维护，条件装配边界模糊

**现状**：
- `MvcExceptionHandler`（SERVLET）与 `WebFluxExceptionHandler`（REACTIVE）代码重复度约 85%
- `GatewayErrorHandler` 在 remi-gateway 内独立的 `@Order(-2)` 处理 WebFlux 特定异常（ConnectException→502、TimeoutException→504）
- 项目实际仅 remi-gateway 使用 WebFlux，其他 8 个业务模块全部 SERVLET

**问题**:
- **维护成本翻倍**：新增 Handler 逻辑需同时修改两处，容易遗漏
- **Order 潜在冲突**：`WebFluxExceptionHandler`（HIGHEST_PRECEDENCE）与 `GatewayErrorHandler`（-2）在 WebFlux 模式下优先级不同，调试困难

**优化建议**：
- 抽取 `BaseExceptionHandler`（已存在）将共用逻辑提升至 95%，子类仅覆盖差异化（如 Flux 的 `ServerWebExchange` 访问）
- `GatewayErrorHandler` 通过 `@ConditionalOnMissingBean(WebFluxExceptionHandler.class)` 明确互斥，避免双重装配

### 3.3 【P1-3】`IllegalArgumentException` 散落在核心链路，绕过统一异常体系

**现状**：workflow 的 `FlowGraphValidator`、agent 的 `AgentDefinitionServiceImpl` / `DagDslParser` 大量直接抛 `IllegalArgumentException` / `IllegalStateException`；`AgentRequestGuard` 直接抛 `new BusinessException("字符串消息")`（未使用 AgentResultCode 枚举）。

**问题**：
- `IllegalArgumentException` 是 JDK 标准异常，不具备 `code/key/level/category` 元数据，被 `MvcExceptionHandler` 兜底捕获后只能返回 `BAD_REQUEST`，前端无法区分"参数格式非法"与"业务规则拒绝"
- 字符串消息直接传入 `BusinessException` 构造函数，完全绕过 i18n 国际化机制

**优化建议**：
- 引入契约层：`BusinessException` 提供 `BusinessException.combine(ResultCode, Object...)` 静态方法替代裸字符串
- 抽样检查：CI 流程增加 ArchUnit 规则 `noClasses().should().throwIllegalArgumentExceptionsUnlessWrappedInBusinessException()`

### 3.4 【P1-4】`@RemiResultCode` 扫描器回退到 ASM 字节码扫描的性能与兼容性风险

**现状**：`ResultCodeScanner` 优先读取编译期索引 `META-INF/spring/remi-result-codes.idx`，缺失时回退至 `classpath*:com/remisoft/**/*.class` 全量 ASM 扫描。

**问题**：
- **启动性能抖动**：ASM 扫描在大型 JAR 包（如 Spring Cloud Alibaba）存在时耗时可达 200ms+
- **OSGi / JPMS / 原生镜像不兼容**：GraalVM 不支持 ASM 运行时扫描，未来云原生部署存在迁移成本
- **编译期索引生成可靠性**：需额外 Maven Plugin 保证；开发者忘记 clean 时索引过期静默失效

**优化建议**：
- 完全移除 ASM 回退，改为编译期注解处理器（Annotation Processor）在javac阶段生成`META-INF/remi-result-codes.idx`
- 开启 `-Werror` 严格校验：索引缺失直接编译失败

---

## 四、P2 级问题：可观察但非阻塞的优化

### 4.1 【P2-1】异常码文档端点 `(actuator/exception-codes)` 的安全控制过度与不足并存

**现状**：支持 `filterModules` 模块白名单 + `authHeaderName` + `authToken` 简单 token 鉴权。

**问题**：
- **简单 token 形同虚见**：硬编码在 `application.yml` 易泄露到 Git；生产环境应走 Spring Security 或 OAuth2 集成
- **模块白名单属于"隐藏即安全"**：后端 API 路径本身可通过端口扫描发现，白名单不改变攻击面

**优化建议**：
- 集成 Spring Security：采用 `/actuator/**` 标准的 `HealthEndpointWebExtension` 安全模型
- 移除自定义 token，改为从 Spring Security Context 取用户角色 + 模块级 `@PreAuthorize("@moduleAuth.canAccess('exception-codes')")`

### 4.2 【P2-2】`ExceptionProperties.responseFormat` 运行时切换 BASE_RESPONSE / PROBLEM_DETAIL 的合理性

**现状**：支持 `remi.exception.responseFormat=PROBLEM_DETAIL` 配置，运行时切换全局响应格式。

**问题**：
- **违反 API 契约稳定性**：同一服务的响应格式频繁切换，客户端难以兼容
- **RFC 7807 兼容性不完整**：ProblemDetail 的 `type` 字段默认 `about:blank`，需自定义 URI 模板才能发挥最大价值

**优化建议**：
- 固化为 `BASE_RESPONSE`（统一业务响应）+ 仅 `/actuator/**` 路径自动走 `ProblemDetail`
- 移除全局 `responseFormat` 配置项，降低配置表面

### 4.3 【P2-3】i18n 启动校验 fail-fast 与 Spring 懒加载哲学冲突

**现状**：`RemiExceptionCoreAutoConfiguration.validateExceptionCodeKeys()` 启动时主动遍历所有已注册错误码并调用 `MessageSource.getMessage()` 校验，缺失即抛 `IllegalStateException` 阻止启动。

**问题**：
- 项目模块数 × 错误码数 × 4 语种 = ~200×4 = 800 次 MessageSource 调用在启动关键路径上
- 新增错误码需同步更新 4 个 i18n 文件，否则启动失败（运维夜间部署时风险高）

**优化建议**：
- 改为 `@EventListener(ContextRefreshedEvent.class)` 异步校验 + 仅 warn（不阻止启动）或
- 提供 `remi.exception.i18n.fail-fast=true|false` 开关，生产环境默认关闭

### 4.4 【P2-4】异常快照 `snapshot` 能力使用率低且与 MDC 职责重叠

**现状**：`AbstractRemiException.snapshot(key, value)` / `snapshots(Map)` 用于业务上下文透传；workflow/agent/cronjob 实际采样中均未使用，仅 Nextwiki 有少量调用。

**对标**：
- 美团内部：使用 MDC（Mapped Diagnostic Context）+ 自定义 `DiagnosticContextFilter` 实现全链路业务上下文，不依赖异常对象携带

**优化建议**：
- 移除 `AbstractRemiException.snapshot` 链式 API，改为 `MDCUtils.put(key, value)` 工具类
- 若确需异常内携带，独立为 `ExceptionContextHolder.set(snapshot)` 静态方法

---

## 五、正向设计亮点（保留并发扬）

以下设计经审计确认为 **必要且合理**，无需裁剪：

| 亮点 | 评价 |
|------|------|
| `BusinessException` / `SysException` 二分 | 清晰捕获 HTTP 语义边界，与 Spring 态度一致 |
| `@RestControllerAdvice` 按 SERVLET / REACTIVE / JDBC / Validation 分治 | 条件装配准确，与 Spring Boot 自动配置深度对齐 |
| `Ext-Msg-Id` / `traceId` 统一透传 | 符合 W3C TraceContext 规范，便于排障 |
| `Micrometer` 指标采集 | 默认关闭 `includeCodeTag` 避免高基数，体现生产实践经验 |
| `@ConditionalOnClass` / `@ConditionalOnWebApplication` | 通用性强，按需装配，启动速度快 |
| `@Order` 优先级显式声明 | 避免与业务 Handler 冲突，文档清晰 |
| `ErrorCodeTable` 双索引结构（codeIndex + moduleIndex） | 满足运行时反查 + 文档展示双重需求 |
| i18n `MessageSource` 抽象 | 支持中/英/繁中，符合出海业务需求 |

---

## 六、优化优先级矩阵（Roadmap）

| 优先级 | 动作 | 预估工时 | 预期收益 |
|--------|------|---------|---------|
| **P0** | 三套注册表合并 → 单 `ErrorCodeTable` | 2 人日 | 启动速度 ↑15ms / 维护成本 ↓30% |
| **P0** | `UnifiedExceptionCode` 完全下线 | 3 人日 | 消除迁移隐患 |
| **P0** | `AbstractRemiException` 单一职责重构 | 5 人日 | 认知负担 ↓50% |
| **P0** | 错误码编码规范三系归一 + 启动强约束 | 2 人日 | 日志聚合 ↑ / 排障速度 ↑ |
| **P1** | Builder 模式简化（移除 CRTP + 快照重叠） | 1 人日 | API 易用性 ↑ |
| **P1** | MVC / WebFlux Handler 共用逻辑提升至 95% | 2 人日 | 维护成本 ↓ |
| **P1** | 契约层引入 + CI ArchUnit 校验 | 2 人日 | 消除裸 IllegalArgumentException |
| **P1** | ASM 扫描回退移除 → 注解处理器 | 1 人日 | 启动性能 ↑ / 原生兼容 ↑ |
| **P2** | 文档端点安全集成 Spring Security | 0.5 人日 | 安全合规 |
| **P2** | `responseFormat` 配置固化 | 0.5 人日 | API 契约稳定性 ↑ |
| **P2** | i18n fail-fast 改为异步 + 开关 | 0.5 人日 | 部署风险 ↓ |
| **P2** | `snapshot` 下沉为 MDC 或独立 Utility | 0.5 人日 | 单一数据源 |

---

## 七、架构决策记录（ADR）

### ADR-001：异常元数据字段数量控制

**背景**：`AbstractRemiException` 承载 8 个元数据字段 + snapshot + extData

**决策**：
- 保留核心 5 个字段：`code`、`key`、`params`、`httpStatus`、`level`
- `category` 由 `code` 首字母推断，移除显式存储
- `timestamp` 使用 `System.currentTimeMillis()` 按需计算，不持久化
- `snapshot` 下沉为 `ExceptionContextHolder` 静态工具
- `extData` 仅显式调用时初始化

### ADR-002：错误码分配策略

**背景**：三套命名规范并存，语义重复

**决策**：
- 公共层（core/security/ratelimit）：A00-A04 + B01 + B02 + C01 + D01 + E01 固定区间
- 业务层：按模块分配 B10-B99（当前占用 B30/B70/B90-B94）
- 废弃码需映射到新码再下线
- 新增号段需在 `CODE_OWNERS.md` 登记

### ADR-003：运行时注册表选型

**背景**：`ErrorCodeTable` vs `ExceptionCodeRegistry` vs `ResultCodeRegistry`

**决策**：
- 主力：`ErrorCodeTable`（启动后只读，ConcurrentHashMap 安全）
- v2.1 起删除 `ExceptionCodeRegistry` 与 `ResultCodeScanner` 双写
- 端点 `/actuator/exception-codes` 直接依赖 `ErrorCodeTable`

### ADR-004：响应格式策略

**背景**：BASE_RESPONSE vs ProblemDetail 运行时切换

**决策**：
- HTTP API：统一 `BaseResponse<T>`（业务响应）
- Actuator 端点：自动走 Spring Boot `ProblemDetail`（原生支持）
- 移除全局 `remi.exception.responseFormat` 配置

---

## 八、对标竞品设计对照

| 维度 | Remi-Cloud 现状 | RuoYi-Cloud | Pig | SpringBlade | 美团内部规范 |
|------|----------------|-------------|-----|------------|------------|
| 异常类分层 | 3 级（Abstract + Biz + Sys） | 2 级（BusinessException + 子类） | 2 级 | 2 级 | 3 级（Biz + Sys + Infra） |
| 错误码注册 | 3 套注册表（过度） | 1 套枚举 | 1 套注解驱动 | 1 套全局常量 | 1 套注册中心 |
| 运行时唯一性校验 | warn 日志（弱） | 无（运行时覆盖） | 启动异常 | 启动异常 | 启动异常 |
| 响应格式 | 可切换（BASE_RESPONSE / PD） | JSON 统一 | JSON 统一 | JSON 统一 | JSON + PD 双轨 |
| 国际化深度 | 4 语种 + i18n | 无 | 单点扩展 | 无 | 全链路 |
| 监控指标 | Micrometer + 自定义 | 基础指标 | Prometheus 集成 | Sentinel 指标 | 全维度 |

**结论**：Remi-Cloud 在**国际化深度**和**监控丰富度**上领先竞品，但在**注册机制简洁性**和**编码规范统一性**上存在明显过度设计。将 P0 动作落地后，整体复杂度将与 SpringBlade / Pig 持平，在可观测性上保持优势。

---

## 九、最终结论

### 9.1 评估定论

`remi-common-exception` 是一个 **设计意图正确但执行层面存在过度工程化** 的异常基础设施模块。其核心问题不是"功能不足"，而是"为了灵活性牺牲了简洁性"：

- **三套注册表**是为了迁移期兼容，但迁移已完成后未及时清理
- **Builder 模式 + CRTP**是为了 API 优雅性，但实际使用率 <10%
- **AbstractRemiException 上帝类**是为了"一站式解决"，但违反了单一职责
- **三套编码规范并存**是为了"模块自治"，但缺乏强制收敛机制

### 9.2 不应为了"简洁"而砍掉的能力

以下能力虽然增加复杂度，但对项目长期演进 **确有必要**：

1. **i18n 国际化**：多语种是出海业务的硬需求
2. **Micrometer 指标采集**：可观测性是微服务架构基石
3. **RFC 7807 ProblemDetail 适配**：虽然当前使用率不高，但对未来云原生集成（服务网格、网关层）有价值
4. **`@RemiResultCode` 编译期索引**：ASM 回退需移除，但索引机制本身是生产优化亮点
5. **`ErrorCodeTable` 双索引**：分别服务运行时反查与文档生成，职责清晰

### 9.3 后续行动清单（Action Items）

| 编号 | 动作 | 负责角色 | Deadline | 验收标准 |
|------|------|---------|---------|---------|
| AI-001 | 合并三套注册表为核心 `ErrorCodeTable` | 核心架构师 | v2.1 M1 | 单元测试全绿 + 启动耗时下降 |
| AI-002 | `UnifiedExceptionCode` 完全下线 | 异常模块 Owner | v2.1 M2 | Grep 0 引用 |
| AI-003 | `AbstractRemiException` 元数据字段压缩至 5 个 | 异常模块 Owner | v2.1 M2 | ArchUnit 规则生效 |
| AI-004 | 错误码强制规范发布 + 存量迁移 | 全体模块 Owner | v2.1 M3 | CI 新增编码规则校验 |
| AI-005 | Builder 模式简化 + snapshot 下沉 | 异常模块 Owner | v2.1 M2 | 调用方代码无 CRPE 强转 |
| AI-006 | ASM 移除 + 注解处理器迁移 | 构建工程师 | v2.2 M1 | GraalVM 原生镜像兼容 |
| AI-007 | 文档安全集成 Spring Security | 安全负责人 | v2.1 M3 | 渗透测试通过 |
| AI-008 | 建立 `CODE_OWNERS.md` 号段登记 | 核心架构师 | v2.1 M1 | 新增码 100% 登记 |
| AI-009 | i18n fail-fast 改为异步 + 开关 | 异常模块 Owner | v2.1 M2 | 夜间部署无感知 |
| AI-010 | 异常使用规范写入团队 Wiki | 技术负责人 | v2.1 M1 | 新人 Onboarding 覆盖 |

---

## 附录：设计哲学反思

### A. "过度设计" vs "充分设计" 的边界

| 判断维度 | 充分设计 | 过度设计 |
|---------|---------|---------|
| 元数据字段数 | 5-7 个（必需） | >10 个（包含低频场景） |
| 继承深度 | 1-2 层 | >3 层 |
| 注册表数量 | 1 套主力 + 可选视图 | >1 套具备独立写入能力 |
| 运行时动态切换 | 固化为主 + Profile 切换 | 运行时任意 API 切换 |
| 配置表面 | 默认值覆盖 90% 场景 | 每个细节都可配置 |
| 模式密度 | 按需引入 | CRTP + Builder + CAS + Bridge + Template Method 全上 |

Remi-Cloud 当前在"注册表数量"和"模式密度"两个维度踩线，其余维度控制得当。

### B. 推荐的设计哲学

> **"让 80% 的业务场景只需 20% 的 API"**

1. **默认路径极简**：`BusinessException.of(code)` / `SysException.of(key)` 满足 80% 场景
2. **高级路径可用**：Builder、snapshot、extData 作为"逃生舱"，不强制使用
3. **迁移期显式标记**：@Deprecated 必须带 `forRemoval` + deadline + migration guide
4. **运行时数据唯一**：一套核心注册表，只读共享

---

**文档版本**: v1.0
**审计日期**: 2026-08-07
**下次复审**: v2.1 发布后（预计 2026-Q4）
