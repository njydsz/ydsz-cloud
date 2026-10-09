# ydzs-cloud × ydzs-micro 全栈优化分析报告

> **版本**: v1.0  
> **日期**: 2026-10-09  
> **对标**: 阿里巴巴 Java 开发手册、Google 工程实践、Vue 3 官方最佳实践、Web Vitals 标准  
> **后端仓库**: D:\Code\open\ydsz-cloud（10 业务模块 + 30 common 子模块）  
> **前端仓库**: D:\Code\open\ydsz-micro（10 子应用 + 公共能力底座）  
> **数据基线**: 173 张数据库表、217 个 Controller、120 个 Repository 接口、123 个前端测试文件

---

## 一、数据库表设计

### 1.1 现状评估

后端共计 152 张业务表，通过 Flyway 版本脚本管理（当前基线 V26.10.01、增量至 V26.10.08）。表命名遵循 `ydsz_` 前缀规范，布尔字段已 100% 使用 `SMALLINT NOT NULL DEFAULT 0`（零 BOOLEAN 违规），审计四字段（created_by/updated_by/created_at/updated_at）在多张主表中覆盖完整。索引体系已建设 433 个索引，多租户字段 tenant_id 在全部 152 张表中 100% 存在并配套索引。整体达到了"合规基线"水平。

但深入到字段级设计，仍存在值得改进的问题。

### 1.2 ID 字段宽度不一致（P1）

规范 DB-008 要求统一 `VARCHAR(36)`，但实际存在三种宽度混用。`VARCHAR(32)` 约 110 张表（Agent/Cronjob/Workflow 为主的 Snowflake 短格式），`VARCHAR(36)` 约 12 张表（gen/idm 模块和部分主表），`VARCHAR(64)` 约 9 张表（Agent 审批、异步任务、认证策略等 comm/idm 模块）。tenant_id 字段同步存在 `VARCHAR(32/36/64)` 三种宽度。

PostgreSQL 中不同类型的 JOIN 不会报错，但宽度差异导致索引对齐效率降低，内存页读取行数减少，且给跨模块联合查询带来隐式类型转换风险。这在数据量增长后对查询性能的负面影响会逐步放大。

**建议**: 在下一个大版本中执行统一迁移脚本——统一为 `VARCHAR(36)`（兼容 UUID 和 Snowflake）。对于当前 Snowflake 41 位时间戳 + 10 位机器 + 12 位序列号的格式（纯数字 19 位以内），`VARCHAR(36)` 足够容纳，无需改造 ID 生成器。先更新 Flyway 脚本，再逐步滚动重启服务。

### 1.3 高增长表缺少分区设计（P0）

当前仅 `ydsz_flow_audit_log`（按 operated_at RANGE 分区）和 `ydsz_comm_search_index_partitioned`（按 tenant_id HASH 分区）两张表使用了分区，但以下高增长表缺少分区设计：

`ydsz_comm_audit_log` 每次 `@Audit` 切面触发写入，预计日增量 10 万+ 行；`ydsz_msg_log` 消息发送记录 41 列 + 12 索引，高并发写入；`ydsz_job_log` / `ydsz_job_log_content` 任务执行日志；`ydsz_agt_trace_step` 每步 Agent 执行记录含 content + 2 个 JSONB 字段；`ydsz_comm_outbox` 领域事件投递（7 个索引维护成本高）；`ydsz_file_share_access_log` 共享访问日志；`ydsz_msg_trace` 消息发送链路。

分区不仅提升查询性能，更重要的是带来运维便利——过期数据可以 `DROP PARTITION` 秒级清理，而非 `DELETE` 导致的锁表和 WAL 膨胀。

**建议**: 对 `ydsz_comm_audit_log` 和 `ydsz_msg_log` 两张最紧迫的表实施按月 RANGE 分区（PostgreSQL 原生 declarative partitioning）。`ydsz_comm_outbox` 按 `tenant_id` HASH 分区。`ydsz_agt_trace_step` 按月 RANGE 分区。编写分区管理函数自动创建下月分区并归档超过 6 个月的旧分区。

### 1.4 类外键列缺少索引（P1）

分析发现 60+ 个类外键列存在索引缺失，集中在反向查询场景。例如 `ydsz_file_file_tag.file_node_id`（按 tag 查文件需全表扫描），`ydsz_flow_his_instance.definition_id`（查流程定义的全部实例），`ydsz_msg_preference.user_id`（按用户查消息偏好），`ydsz_agt_async_task.user_id`（查用户发起的异步任务）。

**建议**: 优先为 file/msg/flow 三个模块的反向查询补索引。可以编写一个通用 SQL 查询来识别所有 `*_id` 后缀且未建立索引的列，在 code review 流程中作为检查项。

### 1.5 时间戳类型（P2）

414 处使用 `timestamp without time zone`，仅 3 处使用 `timestamp with time zone`。项目运行在 PostgreSQL 上，且前端完整配置了国际化（6 语种），时区处理不当会导致跨时区数据展示错误。

**建议**: 逐步将全库 `timestamp without time zone` 迁移为 `timestamptz`。首先在应用层 JDBC 连接串中强制 `timezone=UTC`，然后通过 `ALTER TABLE ... ALTER COLUMN ... TYPE TIMESTAMPTZ` 完成字段级迁移。这对云部署和跨机房复制尤为重要。

### 1.6 冗余索引（P2）

识别出 5 个确认冗余的索引：`ydsz_agt_approval` 的单列 tenant_id 索引被两个联合索引完全覆盖；`ydsz_file_file_node` 的单列 parent_id 索引被 4 个以 parent_id 开头的联合索引覆盖；`ydsz_flow_run_task` 的单列 assignee_id 索引被 2 个联合索引覆盖。

**建议**: 删除冗余索引，减少写入时的 B-Tree 维护开销，节省存储空间。通过 `pg_stat_user_indexes` 对全库做一次使用频率审计，确认零调用后安全删除。

---

## 二、接口单元测试

### 2.1 现状评估

后端项目测试框架选型正确（JUnit 5.10 + AssertJ 3.26 + Mockito 5.12 + Testcontainers 1.20 + JaCoCo 0.8.12），基础设施已建设完备（TestcontainersBase 提供 PG18+Redis7 容器、BaseControllerMockTest 提供轻量级 MockMvc、DddLayerArchTest 守护 DDD 分层）。maven 正确区分了 surefire（单元测试）和 failsafe（集成测试），并在 CI 中默认执行 `mvn verify`。

但测试执行层面极度薄弱。全项目仅 13 个测试文件，对应超过 1000 个业务类。覆盖率估计低于 2%，远低于 JaCoCo 门禁阈值 10%。整个 CI 流程中的覆盖率门禁处于"要么持续失败、要么被 skip 绕过"的状态。

### 2.2 盲区一：218 个 Controller 零测试（P0）

218 个 Controller 完全没有任何测试覆盖。在当前快速迭代的开发节奏下，接口签名变更、参数增减、权限注解调整等情况随时可能发生，但没有任何自动化手段能在合并前发现问题。已有 `BaseControllerMockTest` 基类提供了容器无关的 MockMvc 支持，新 Controller 接入 smoke test 的成本很低。

**建议**: 强制要求每个新增 Controller 至少编写一个 smoke test（验证 HTTP 200 + YdszResponse 结构完整）。对于存量 Controller，按优先级分批补齐——先补齐写操作（CRUD 中的 CUD），再补齐复杂查询。目标是 3 个月内将 Controller 测试覆盖从 0 提升到 60%。

### 2.3 盲区二：核心业务逻辑零测试（P0）

工作流状态机流转（任务通过/驳回/加签/转办/撤销等状态变更路径）、RBAC 权限判断（角色继承、数据行权限/列权限 SQL 拼接）、规则引擎执行（DMN 决策表求值、规则冲突检测）、定时任务幂等触发——这些对业务正确性至关重要的逻辑完全没有测试。一旦有人误改状态机判断条件，可能导致流程死锁或越权数据泄露。

**建议**: 优先为 Workflow 状态机编写状态转换矩阵测试——枚举所有合法的起始状态 × 操作 → 目标状态，用参数化测试一次性覆盖。其次为权限服务编写行权限注入的集成测试，通过 Testcontainers 真实验证 SQL 拼接结果是否包含 tenant_id / org_id 过滤条件。

### 2.4 盲区三：安全模块零测试（P0）

`ydsz-common-safe` 的 SQL 注入防护（正则特征检测 + IP 自动封禁）、XSS 过滤、`@Idempotent` 幂等防护（SET NX 防重）、`@RateLimit` 限流——这些安全关键路径零测试。一旦正则规则变更或限流计数器异常，缺少自动化兜底。

**建议**: 为 SqlInjectionFilter 编写典型的注入 payload 测试（`' OR 1=1 --`、`UNION SELECT`、时间盲注），验证 filter 正确拦截并写入审计日志。为幂等性编写并发测试（10 线程同时提交相同请求，仅 1 个成功）。

### 2.5 测试数据构造（P1）

所有测试数据均在测试方法内直接 `new DTO()` + `setXxx()` 构造，没有任何 Builder / Fixture 模式。构造一个完整的 `MsgTemplateDTO` 需要设置 15+ 个字段，测试代码冗长且不可复用。修改 Entity 字段时需要手动同步所有测试中的构造代码。

**建议**: 为核心业务实体引入 Test Data Builder 模式。例如 `MsgTemplateBuilder().withName("test").withChannel("SMS").build()`，提供合理的默认值，测试中只需 override 关注字段。预期可减少测试代码量 30% 以上。

---

## 三、前后端能力贯通

### 3.1 现状评估

后端 SpringDoc 配置完善（DocAutoConfiguration + OpenApiAutoConfiguration + 各模块 WebOpenApiConfiguration），构建期可生成 openapi.json。前端有完整的 OpenAPI SDK 生成管线（`bash/gen-api.mts` → `bash/unified-contract.mts` → `openapi-typescript` → 8 个子应用的 `schema.d.ts` 和 `.api-contract.lock`）。分页体系前后端对齐（pageNum/pageSize），成功码 `A00000` 全链路一致。基础设施选型正确。

但实际执行层面存在明显断层。

### 3.2 DTO 类缺少 @Schema 注解（P1）

后端 system-domain 下仅 3 个 VO 和 5 个 DTO 有 `@Schema`，核心类如 `ConfigDTO`、`ConfigPageQuery`、`ConfigVO`、`AppInfoDTO` 全部缺少注解。前端生成的 `schema.d.ts` 中字段没有 `@description` 注释，字段约束（`@NotBlank`、`@Size`、`@Xss`）不会反映到 OpenAPI spec 中，前端开发者无法从 TypeScript 类型提示中得知字段含义和校验规则。

**建议**: 在 code review checklist 中增加"新增 DTO/VO 必须添加 `@Schema(description=...)` 注解"的要求。对存量 DTO 按使用频率分批补充。推荐配合 `springdoc-openapi` 的 `io.swagger.v3.core.jackson.ModelResolver` 注解扫描器，自动从 Javadoc 中提取描述信息作为补充。

### 3.3 Serializable 污染 API 契约（P1）

后端 Query/DTO/VO 类为实现 `Serializable` 接口而包含 `private static final long serialVersionUID = 1L` 字段。Jackson 序列化后暴露到 JSON 中，前端 `openapi-typescript` 忠实地将其生成到 `schema.d.ts`。开发者在 IDE 中看到 `serialVersionUID?: number` 会极大困惑。

**建议**: 两种解法——方案 A，在 VO/DTO 类上添加 `@JsonIgnoreProperties({"serialVersionUID"})` 全局排除；方案 B，移除 VO/DTO 的 `Serializable` 实现（仅在需要 Java 序列化的场景如缓存中保留，HTTP 传输无需此接口）。方案 B 更彻底。

### 3.4 双轨 API 生成机制并行（P1）

系统中有两条生成前端 API 客户端的路径——旧轨 `bash/gen-contract.py` 生成 `apps/*/src/api/*.ts` 手写封装（当前实际使用），新轨 `bash/unified-contract.mts` 生成 `openapi-fetch` 的 `apiClient`（已建好但使用率为零）。两套机制分别维护，一旦后端接口变更而只重新运行了其中一套，就会产生前后端不一致。

**建议**: 明确定位新轨 `apiClient` 为"未来唯一标准"，制定一个季度迁移计划——先从 `system-web` 模块试点接入 apiClient，验证路径处理、错误拦截、TS 生成质量后，再批量推广到其余 8 个子应用。旧轨代码在新轨稳定后移除。

### 3.5 缺少统一日期格式（P2）

后端使用 `LocalDateTime` / `LocalDate`（Java 8+ time API），Jackson 序列化为 ISO-8601 格式。但后端没有任何统一的 `@JsonFormat` 注解来显式指定格式，完全依赖 Jackson 默认行为。一旦某位开发者在 `ObjectMapper` 中注册了自定义 `JavaTimeModule` 或在特定 VO 上使用 `@JsonFormat`，就会产生格式不一致。

**建议**: 在 `ydsz-common-json` 中统一配置 `ObjectMapper` 的 `JavaTimeModule`，禁用 `WRITE_DATES_AS_TIMESTAMPS`，确保全项目一致输出 `"yyyy-MM-dd'T'HH:mm:ss"` 格式。前端 `dayjs` 已天然支持 ISO-8601 解析，无需额外处理。

### 3.6 枚举无前端类型约束（P2）

后端广泛使用字符串表示枚举（如 `status: "ENABLED"/"DISABLED"`、`valueType: "STRING"/"NUMBER"/"JSON"`），但 Java 类中声明为 `String` 而非 `Enum`。生成的前端类型也全是 `string`，无字面量联合类型约束。前端开发者可能传错枚举值（如 `"enabled"` 小写），后端需靠 Service 校验才能发现。

**建议**: 优先为高风险的枚举字段（如 valueType、status、channel）补充 `@Schema(implementation = EnumClass.class)` 注解，让 `openapi-typescript` 生成联合类型。中期可推动后端改为 `Enum` 类型 + `@JsonValue`/`@JsonCreator` 注解，从根本上解决类型安全问题。

### 3.7 文件上传 Content-Type 设置（P1）

前端 `uploader.ts` 显式设置 `'Content-Type': 'multipart/form-data'`。但浏览器在发送 `FormData` 时自动设置 `Content-Type: multipart/form-data; boundary=----WebKitFormBoundary...`，手动设置的 header 不含 boundary，可能导致后端 Spring 无法解析 multipart 请求。

**建议**: 删除该显式设置，让浏览器自动添加完整的 Content-Type（含 boundary）。

---

## 四、前后端架构优化

### 4.1 后端架构亮点

经过持续治理，后端 DDD 分层已经达到了较高成熟度。依赖方向严格单向（web → server → domain ← infra），server 层仅依赖 domain 层接口和 common 模块，零 infra 引用。所有 Controller 返回 `YdszResponse<T>` 或 `PageResponse<T>` 统一包装，Controller 层零 try-catch 块。异常三层防御体系（全局 BaseExceptionHandler + 各模块 19 个 ExceptionHandler + 网关 GatewayExceptionHandler）不暴露内部堆栈。SQL 注入三层纵深防护（网关正则特征检测 + JDBC AST 防火墙 + ORM 参数化查询）。Jwt 双 token 机制（access 2h + refresh 7d）+ Redis 黑名单即时吊销 + Bloom Filter 优化。语义化 LLM 缓存 `SemanticLlmCache` 探索 AI 场景的精细化缓存设计。

### 4.2 前端架构亮点

前端自研了完整的 ESM 微前端三级体系（micro-kernel 内核 + micro-runtime 运行时 + micro-shared-deps vendor 外置），支持 proxy/snapshot/iframe 三种沙箱策略，支持马尔可夫链路由预测的预加载优化。自研 43 个 headless 原语组件（Radix-Vue 风格），通过 `@ydsz-core/ui` 统一出口。通信层成对生成 businessClient + baseClient，businessClient 承载业务拦截器，baseClient 用于 token 刷新等逃生通道打破循环依赖。SWR 多级缓存 + 401 并发排队 + TraceID 全链路追踪。构建产物 vendor 外置通过主应用 importmap 运行时加载，消除重复打包。6 语种国际化 + 脚本自动翻译 + i18n key 漂移检查。

### 4.3 虚拟线程推广（P2）

后端 `ExecutorUtils` 和 `MeteredVirtualExecutorService` 已提供完整的虚拟线程 API，使用方包括 Agent 聊天执行器、ReAct 异步推理、流程批量操作、导出任务。但仍有部分 IO 密集型场景未使用虚拟线程。

**建议**: 梳理所有使用 `CompletableFuture.supplyAsync()` 或手动 `new Thread()` 的场景，统一迁移到 `ExecutorUtils.newVirtualThreadExecutor()`。特别适合消息批量发送、文件批量导出、Agent 并发推理等 IO 密集型操作。已在 VirtualThreadMetrics 中集成 Micrometer Gauge，可直接观察迁移效果。

### 4.4 大字段冷热分离（P2）

以下表含多个 TEXT 大字段的表，每次查询都会触发 PostgreSQL TOAST 解压缩，导致无效 IO 和内存占用：`ydsz_comm_audit_log` 含 `request_params`、`response_result`、`diff_before_snapshot`、`diff_after_snapshot` 四个 text 字段；`ydsz_agt_dag_workflow` 含 `description`、`dsl_content`、`layout_json` 三个大文本字段。

**建议**: 对 `ydsz_comm_audit_log` 将四个大 text 字段拆到独立的 `ydsz_comm_audit_log_detail` 子表中，主表仅保留查询必需的元信息字段。仅在需要详情时才 JOIN 子表，日常审计列表查询效率可提升 50% 以上。

---

## 五、前后端功能增强

### 5.1 缺失的网关健康仪表板（P2）

当前 Nginx 已配置 `/health`（浅探针返回 200 JSON）和 `/health/deep`（深探针转发到 `/actuator/health`），但前端缺少一个可视化的"系统健康仪表板"，无法让运维人员在不登录服务器的情况下感知各模块状态。

**建议**: 在前端新增 `admin-monitor` 模块或嵌入 system-web 的健康看板，调用 `/health/deep` 和各个服务的 `/actuator/metrics` 端点，可视化展示各微服务的 JVM 线程、GC、HTTP 请求数、DB 连接池使用率等核心指标。

### 5.2 缺失的全局搜索能力（P1）

后端已有 `ydsz-common-search` 模块封装了 ES/OpenSearch 集成，并已配置分区表 `ydsz_comm_search_index_partitioned`。但前端缺少一个全局搜索入口（类似 macOS Spotlight 的 Cmd+K 命令面板但范围扩大到业务数据）。

**建议**: 已实现的 `useCommandPalette` composable 可作为基础，在 prefix system（设置/字典/租户管理）之外接入 cross-module search（跨模块搜索），通过后端全局搜索 API 返回高亮结果。对标竞品的"全局搜索栏"是 B 端管理系统的差异化体验。

### 5.3 缺失的操作录制与回退（P2）

对于 B 端复杂的表单操作（如流程设计器、规则决策表编辑），用户常因误操作丢失大量工作。目前没有任何"撤销/重做"或"操作历史回放"能力。

**建议**: 在 `ydsz-common` 中新增一个轻量级 Command 模式框架（已在 workflow 模块有类似 FlowInstance 执行历史的设计可参考），为表单密集型子应用（workflow、literule、agent）提供 50 步的撤销/重做能力。

### 5.4 文件秒传的覆盖不全（P1）

后端 `FileDedupService` 已支持 SHA-256 查重（SOP-6 已定义），但前端仅部分模块实现了秒传流程。大部分文件上传场景仍是直接 POST 上传后等待后端返回，用户体感较慢。

**建议**: 在 `@ydsz/request` 的 `uploader.ts` 中增加预计算前端 SHA-256（可通过 `crypto.subtle.digest('SHA-256', arrayBuffer)` 实现），上传前发送 HEAD 请求查询文件是否已存在，存在则直接返回秒传结果跳过上传。

---

## 六、前后端性能提升

### 6.1 后端查询性能

Agent `listActive()` 当前全量加载数据后在 Java 侧过滤有效条目，随着 Agent 数量增长（预期 100+），此模式将出现明显性能下降。建议改写为 SQL 层 WHERE 过滤（`LambdaQueryWrapper.eq("status", "ACTIVE")`），将过滤推至 DB 层执行。

N+1 查询风险分析：当前未发现典型 N+1 模式（for 循环中调用 `findById`），但 `DictTypeVO` 关联 `DictItem` 列表的场景需要关注——若在循环中为每个 Type 调用一次 `findItemsByTypeId`，则构成 N+1。建议在层级关联查询中使用 `selectBatchIds` 一次性加载，或在 DB 层 JOIN。

分页 count 查询优化：MyBatis-Plus 的 `selectPage` 默认会执行 `COUNT(*)` 全表扫描获取总数。对千万级数据表（如审计日志），每次分页都 count 代价极高。建议对高频大表使用 `Page(..., searchCount=false)` 禁用 count，改为前端"加载更多"无限滚动模式，或缓存总数到 Redis。

### 6.2 前端首屏加载

前端当前的 vendor 外置策略（通过 importmap 在主应用预加载 vue/vue-router/pinia/vxe-table/echarts 等核心库）设计正确，子应用产物因此缩减到仅业务代码。但 webpack-bundle-analyzer 风格的依赖分析报告未在构建流程中强制执行，vendor 锁版本（`bash/importmap.lock.json`）的变更依赖人工比对。

**建议**: 在 Turbo CI pipeline 中接入 rollup-plugin-visualizer，每次构建生成 `stats.html` 并归档为构建产物。任何 PR 导致 vendor chunk 大小增幅超过 15% 时在 review 评论中自动提示。

### 6.3 前端 SWR 缓存命中率监控

前端 `cache-adapter.ts` 配置了 5 分钟 stale + 10 分钟 maxAge 的 SWR 策略。但没有监控缓存命中率，开发者无法判断缓存策略是否合理。

**建议**: 在 `cache-adapter.ts` 中增加 hit/miss 计数器，暴露在 Sentry 自定义指标中。若持续低于 30% 需要考虑调大 staleTime 或减少接口变更频率。

---

## 七、前后端用户体验

### 7.1 前端体验优点

前端交互体验已覆盖企业级管理系统的核心场景。`YdLoading` 组件内置 50ms `minLoadingTime` 防止请求过快完成时的 UI 闪烁。`YdSkeleton` 提供 text/circular/rectangular/rounded 四种形态的 shimmer 占位动画。`YdEmptyState` 预设了 created（新建）/no-data（无数据）/no-result（无搜索结果）/no-permission（无权限）/error（加载失败）五种场景的视觉和文案。表单基于 zod schema 驱动校验，支持异步校验规则。列表通过 `useTableData` composable 统一了排序、筛选、选择的状态管理。乐观更新 `useOptimisticUpdate` 在失败时自动快照回滚。

### 7.2 移动端未适配（P2）

项目当前为 Desktop-first 管理系统，各应用使用 Tailwind 3.4 默认断点（sm:640px / md:768px / lg:1024px），没有移动端专属的侧边栏抽屉、触摸手势等适配。如果产品规划涉及移动端或 iPad 使用场景，需要补充 responsive grid + 移动导航组件。当前可以在 iPad（1024px）上正常显示，但手机体验不可用。

**建议**: 若产品暂不涉及移动端，可以将 Desktop-only 作为明确决策记录在架构文档中；若后续规划移动化，优先在 `@ydsz-core/ui/mobile-bridge` 中补齐 responsive 断点工具函数和移动端抽屉导航组件。

### 7.3 a11y Color Contrast 关闭（P1）

前端 `@axe-core/playwright` 配置中 color-contrast 规则被关闭，注释称"动态主题切换过渡期不足"。但长期关闭意味着色觉障碍用户可能无法正确识别按钮状态和内容层次。

**建议**: 先恢复 brand-color（主色/成功/警告/危险）和 text-color（正文/辅助/禁用）的对比度扫描，明确各 token 的最小对比度阈值（WCAG AA 要求正文 4.5:1，大字 3:1）。将"主题切换过渡期"的解决时间明确写入排期，而非无限期关闭。

### 7.4 E2E Smoke 覆盖深度不足（P1）

前端 10 个 smoke test 的模式高度一致：TC-001 页面访问（heading 可见）→ TC-002 列表加载（table/empty 择一可见）→ TC-003 新增按钮→ dialog 弹出。三类用例仅验证了"页面不崩溃"，未覆盖真正的用户旅程。

**建议**: 为每个子应用补充 2-3 条 CRUD 全链路 E2E 用例——"创建一条记录 → 在列表中找到 → 编辑 → 搜索验证 → 删除 → 确认删除"。着重覆盖表单校验反馈（如提交空表单时提示必填）、操作成功/失败的 Toast 提示、列表分页切换等真实用户场景。

### 7.5 Internationalization 完整性检查（P2）

后端 i18n 已推进到 Service 层（205+ 硬编码中文已替换为 `I18n.message()`），前端 vue-i18n 已支持 6 语种自动翻译。但仍可能存在部分新增 key 未同步到全部 3 份 locale 文件的情况。

**建议**: 在 CI 中运行 `bash/gen-i18n.py` 的 `--check` 模式（如果尚未实现则新增），自动检测 zh-CN / en-US / es-ES / fr-FR / ja-JP / ko-KR 六个文件中 key 是否对齐，不一致时阻断合并。

---

## 八、优先级总表

| 优先级 | 编号 | 维度 | 建议 | 预估工作量 |
|--------|------|------|------|-----------|
| **P0** | DB-03 | 数据库 | 审计日志/消息表/Outbox 增加按月 RANGE 分区 | 2 人日 |
| **P0** | TEST-01 | 单元测试 | 218 个 Controller 补齐 smoke test | 5 人日 |
| **P0** | TEST-02 | 单元测试 | Workflow 状态机流转 + RBAC 判断测试 | 3 人日 |
| **P0** | TEST-03 | 单元测试 | 安全模块（注入防护/幂等/限流）测试 | 2 人日 |
| **P1** | DB-02 | 数据库 | 60+ 个类外键列补索引 | 3 人日 |
| **P1** | DB-01 | 数据库 | ID 字段统一为 VARCHAR(36) | 5 人日 |
| **P1** | CONN-01 | 前后端贯通 | DTO 类补充 @Schema 注解 | 3 人日 |
| **P1** | CONN-02 | 前后端贯通 | 移除 VO 中 Serializable 或全局 @JsonIgnore | 1 人日 |
| **P1** | CONN-03 | 前后端贯通 | 明确 apiClient 为唯一标准，启动迁移 | 5 人日 |
| **P1** | CONN-04 | 前后端贯通 | 文件上传删除手动 Content-Type 设置 | 0.5 人日 |
| **P1** | UX-03 | 用户体验 | 恢复 a11y color-contrast 扫描 | 2 人日 |
| **P1** | UX-04 | 用户体验 | E2E smoke 扩展为 CRUD 全链路 | 3 人日 |
| **P1** | FX-03 | 功能增强 | 文件秒传前端集成（SHA-256 + HEAD 查询） | 2 人日 |
| **P2** | DB-05 | 数据库 | 时间戳统一为 timestamptz | 5 人日 |
| **P2** | DB-06 | 数据库 | 删除 5 个冗余索引 | 0.5 人日 |
| **P2** | ARCH-01 | 架构优化 | 虚拟线程推广到所有 IO 密集场景 | 3 人日 |
| **P2** | ARCH-02 | 架构优化 | 大字段冷热分离（audit_log 详情子表） | 2 人日 |
| **P2** | PERF-02 | 性能提升 | 构建产物可视化 + vendor chunk 体积门禁 | 1 人日 |
| **P2** | CONN-05 | 前后端贯通 | 统一 Jackson 日期格式（禁用 WRITE_DATES_AS_TIMESTAMPS） | 0.5 人日 |
| **P2** | CONN-06 | 前后端贯通 | 补充 @Schema(implementation) 生成枚举联合类型 | 2 人日 |
| **P2** | UX-02 | 用户体验 | 移动端适配（如产品规划需要） | 10 人日 |
| **P2** | UX-05 | 用户体验 | i18n key 对齐 CI 检查 | 1 人日 |

合计预估：约 55 人日（可并行拆分到 2-3 个迭代中完成）

---

## 九、对标结论

对照互联网大厂（阿里、字节、美团内部）的研发规范，当前项目在以下方面已达标或领先：

- DDD 分层治理（server 零 infra 引用、依赖倒置完善）在阿里 P7+ 级别的代码标准中属于优秀水准
- 统一响应包装（YdszResponse）和三层 ExceptionHandler 是美团内部标准模板的强化版
- SQL 注入三层纵深防护超过大多数中小厂的单项防护
- 前端自研 ESM 微内核 + markov 路由预测在同类 B 端管理系统中具有技术先进性
- 190 条红线规则的自动化治理体系对标 Google Static Analysis 团队方法论

主要差距集中在测试覆盖和安全左移两个维度：

- 大厂通常要求核心模块单测覆盖率 60%+（美团基础技术部标准），当前 <2% 差距巨大
- 阿里要求 Controller 覆盖率 100%（至少 smoke），当前 0%
- 安全测试左移（SAST/DAST 门禁）是大厂 DevSecOps 标配，当前仅靠运行时拦截

优先补齐测试覆盖短板，比继续优化架构更能带来质量收益。建议将本报告中的 P0/P1 项纳入下一个里程碑的核心 KPI。
