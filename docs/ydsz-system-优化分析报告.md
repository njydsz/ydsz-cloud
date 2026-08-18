# ydsz-system 模块优化分析报告

> 基于最新代码（截至 2026-08-19）的事实分析，对标阿里 Java 开发手册（黄山/泰山版）、腾讯代码规范、字节 Java 应用规范、Spring Boot 3 / Spring Cloud 最佳实践，以及典型内网基础平台（Apollo / Nacos / QConfig /北斗配置中心）的能力面。
>
> 所有结论均可回溯到具体文件路径 + 行号，已交叉核对 README 与代码，识别 doc drift 与虚构条目。
> 按 P0 → P1 → P2 排序，遵循云顶规范（强制使用 ydsz-common-json / common-cache / 自研 POI 替换 / common 层 L1 四 utility 模块）。

---

## 0. 执行摘要

ydsz-system 整体工程质量**在国内中厂水平之上**：横切关注点（审计/限流/幂等/缓存/指标/事件）齐全、领域仓储分层方向正确、Config 实体是真正的充血模型。但与阿里/腾讯一线规范相比，存在**5 个系统性短板**：

1. **DDD 落地不彻底**——实体错位到 infra 层（应为 domain）、贫血模型与充血模型混用、领域服务缺失。
2. **doc drift 集中爆发**——README 与代码至少 4 处不一致（实体位置、app 模块、缓存策略、已迁移属性）。
3. **过度集成与过度设计并存**——server 引入 3 个未使用的 common 依赖（thread/feign-server 侧/lock）、启动类私有构造、scanBasePackages 过宽。
4. **VO/DTO 角色混淆**——ConfigVO 既作请求体又作响应体，违反 CQRS 与阿里规范「请求/响应/持久化三分离」。
5. **缓存策略与文档背离**——README 自报 Redis 二级缓存，实际是 ydsz-common-cache 进程内本地缓存 + Outbox 事件广播，跨实例一致性靠事件兜底（system 模块变更频率低，未必需要事件总线）。

按本报告落地，预计可将代码规范度从「中厂中段」提升到「大厂规范级」。

---

## 1. 现状基线（基于代码事实）

| 维度 | 数量 / 事实 |
|---|---|
| 子模块 | **7** 个（parent + api/app/domain/infra/server/web），README 自报 5 层 ❌ drift |
| 实体 | 9 个（Config / DictType / DictItem / AppInfo / Variable / EntityVersion / Tenant / TenantPlan / TenantPlanMenu），**全部位于 `ydsz-system-infra/.../entity/`**，README 自报在 domain/entity ❌ drift |
| Mapper | 9 个，位于 infra/mapper，命名规范无 DO 后缀 ✅ |
| Repository | 8 个接口（domain/repository） + 8 个实现（infra/repository） ✅ |
| Controller | 15 个（web/controller，含 SystemApplication 启动类共 16 文件） |
| Service impl | 10+ 个，最大 ConfigServiceImpl **748 行**、DictItemServiceImpl **716 行** |
| 公共依赖 | server 模块引入 **18** 个 ydsz-common-* 子模块，其中 **3 个未使用**（thread / feign / lock） |
| 横切注解 | @Audit / @RateLimit / @Idempotent 三件套齐全 ✅ |
| 缓存 | ydsz-common-cache 本地缓存（@Cacheable/@CacheEvict/@Caching 精准失效） + Outbox 事件广播 |
| 事件 | common-event Outbox 模式，CONFIG_CHANGED / DICT_TYPE_CHANGED / VARIABLE_CHANGED 三类 |
| 监控 | Micrometer 指标在 hot path（非装饰性）✅ |
| 启动 | `SystemApplication` 私有构造、`scanBasePackages = {"com.njydsz.system", "com.njydsz.common"}` |

---

## 2. 关键发现（按 5 个维度）

### 2.1 架构维度

#### A1. 实体位置 doc drift（P0 必修）
- **事实**：README 第 53-64 行声明实体在 `ydsz-system-domain/entity/`，但实际全部 9 个实体在 `ydsz-system-infra/.../entity/`（如 `Config.java:1` `package com.njydsz.system.infra.entity;`）。
- **影响**：domain 层根本没有 entity 目录；充血模型实体被锁在 infra 层，违反 DDD「领域层持有领域模型」原则。
- **根因**：domain/pom.xml 依赖了 `mybatis-plus-annotation`（第 37-39 行）+ `common-jdbc`（第 28-31 行）——domain 想用注解但 MyBatis-Plus 注解会被 `@TableName` 等数据访问语义污染。

#### A2. domain 层被技术细节污染（P0 必修）
- `ydsz-system-domain/pom.xml` 依赖：common-jdbc + mybatis-plus-annotation + spring-security-crypto + common-excel + common-safe。
- **问题**：domain 应保持纯净，只依赖 common-core + common-exception + common-domain + JSR-303。当前依赖让 domain 间接耦合 MyBatis-Plus、Spring Security、Excel 注解、Safe 工具——**纯净度校验会失败**。
- **配置 VO 使用 @ExcelProperty 注解**（domain/pom.xml:57 注释自承）—— VO 是展示对象，不应承担 Excel 序列化职责，应分离 ConfigExcelVO 到独立的 export 包（infra 或 server）。

#### A3. 贫血模型与充血模型混用（P1）
- ✅ Config.java（190 行）是充血模型：含 `validate()` / `getTypedValue()` / `validateValueType()` / `ensureDefault()` / `validateValueFormat()` / `isPublicConfig()` 共 6 个领域方法。
- ❌ Tenant.java / DictType.java 是贫血 POJO：仅字段 + Lombok getter，无任何业务方法。
- **影响**：领域行为被泄漏到 Service（ConfigServiceImpl 748 行），导致单类承担多职责。

#### A4. 领域服务 / 聚合根 / 领域事件缺失（P1）
- domain 层目录仅含 `dto/ vo/ enums/ query/ repository/`，**无 service/ event/ aggregate/ 目录**。
- 所有"Service"都在应用层（ydsz-system-server），且直接编排跨聚合操作：
  - ConfigServiceImpl.save() 同时操作 Config + EntityVersion + SearchIndex + Cache 4 个聚合。
  - DictItemServiceImpl（716 行）同样跨聚合。
- **规范**：DDD 要求一个应用服务方法只编排一个聚合，跨聚合走领域事件解耦。

#### A5. ydsz-system-app 是空壳（P2）
- 仅 3 个 Java 文件，全是薄壳：SystemAppAutoConfiguration（23 行）+ SystemAppHealthIndicator（return Health.up()）+ SystemAppOpenApiConfiguration（重写 getTitle）。
- README 自承"预留 controller/app/ 包"，**实际无任何 controller**。
- ydsz-system-server/pom.xml:22 依赖 ydsz-system-app——引入了一个空壳子模块。
- **影响**：模块数虚高，构建图复杂度增加无业务价值。

### 2.2 功能维度

#### F1. 缺少配置灰度发布（P1）
- 大厂 system 中心（Apollo / Nacos / QConfig）均支持按租户/IP/user 标签灰度发布。
- ydsz-system 的 isPublic 是布尔值（0/1），无灰度维度。配置变更全量生效，无回滚前的灰度验证窗口。

#### F2. 缺少配置变更审批流（P1）
- 大厂配置中心普遍支持「提交变更 → 审批 → 生效」三段式（参考 Apollo 的 ReleaseHistory + 审批流）。
- ydsz-system 直接 CRUD + 写审计，变更即生效，**无审批拦截点**。高危配置（如限流阈值、密钥）变更缺少管控。

#### F3. 缺少租户级配置隔离（P0 必确认）
- ConfigServiceImpl Javadoc 自承"多租户：所有方法自动按当前 TenantContext 隔离"（第 83 行），缓存键含 `tenantId`（第 73-75 行）。
- 但 README 第 128 行表 `ydsz_config` 只说"支持租户维度"，未见 `tenant_id` 列定义。
- **需对照 deploy/sql/init.sql 核查表结构**。如果 ydsz_config 缺 tenant_id 列，多租户隔离是名义而非实质。

#### F4. 缺少配置推送回执（P2）
- Outbox 事件广播无订阅者 ACK 机制，业务方是否收到 CONFIG_CHANGED 未知。
- 大厂通常提供「订阅方确认 + 重试 + 死信队列」三段式保障。

#### F5. 缺少字典国际化（P2）
- DictItem 无 i18n 字段，多语言场景下需要扩展（`display_name_en` / `display_name_zh` 等）。
- 内网系统国际化需求低，但作为基础能力短板应记录。

#### F6. 缺少应用密钥轮换（P2）
- AppInfo 仅支持 BCrypt 校验，无密钥过期/轮换机制。
- 大厂通常支持「主备密钥共存 + 滚动切换」，避免单密钥泄露后的全量切换风险。

### 2.3 性能维度

#### P1. 单 Service 类过大（P1）
- ConfigServiceImpl 748 行 / DictItemServiceImpl 716 行 / VariableServiceImpl 619 行 / ConfigBatchServiceImpl 442 行 / AppInfoServiceImpl 401 行。
- **阿里规范**：单类不超过 1000 行，且「单一职责」。当前 ConfigServiceImpl 同时承担 CRUD + 缓存管理 + 事件发布 + 版本快照 + 搜索同步 + Excel 导入导出 6 类职责。
- **建议**：拆分为 ConfigApplicationService（CRUD 编排） + ConfigCacheManager（缓存管理） + ConfigEventCoordinator（事件协调） + ConfigExcelFacade（导入导出）。

#### P2. 跨聚合操作未走异步（P1）
- ConfigServiceImpl.save() 在同一事务内同步调用：`entityVersionService.createVersion` + `indexUpsert` + `publishConfigChangedEvent`。
- Outbox 事件已异步，但版本快照与搜索索引同步仍阻塞主事务。
- **建议**：版本快照与搜索索引同步可降级为「事务提交后异步」（@TransactionalEventListener AFTER_COMMIT），减少写路径延迟。

#### P3. 缓存策略可简化（P1）
- 当前：ydsz-common-cache 本地缓存 + Outbox 事件广播保证跨实例一致性。
- **system 模块特性**：配置/字典变更频率极低（日均 < 100 次），但读取频率高。
- **建议**：对 system 模块，本地缓存 + 短 TTL（5 分钟）已足够，**Outbox 事件广播可降级或移除**——5 分钟 TTL 即可兜底最终一致性。当前 Outbox 增加了 4 个组件（DomainEventPublisher / CrossModuleEventListener / OutboxProcessor / OutboxMessage）的复杂度，对低频变更场景过度。

#### P4. Micrometer 指标完整（P2 优势）
- SystemMetrics 在 hot path 埋点（ConfigServiceImpl:323 / 327；DictItemServiceImpl:173 / 176），非装饰性。
- 已有 config_read_total / config_read_duration_ms / config_cache_hit_total / config_cache_miss_total / dict_query_* / app_validate_* / variable_read_* 等专用指标。
- **建议**：保持，可作为其他模块指标埋点参考样板。

### 2.4 体验维度

#### E1. README doc drift 集中（P0 必修）
- ❌ 实体位置：第 53-64 行声明 domain/entity，实际 infra/entity。
- ❌ 模块树：第 42-102 行未列出 ydsz-system-app 子模块。
- ❌ 缓存策略：第 140-156 行说「Redis 缓存」，实际 ydsz-common-cache 本地缓存 + Outbox 事件。
- ❌ 已迁移属性：SystemProperties Javadoc 第 20 行提到 `ydsz.system.internal-api-ip-whitelist`，但实际已迁移到 `ydsz.safe.ip-access.*`。
- ❌ Controller 数量：README 第 87 行说"14 个 Controller"，实际 15 个（漏列 SearchDashboardController 与 AuditAdminController 之一）。

#### E2. 启动类过度设计（P1）
- SystemApplication.java:34 `private SystemApplication() {}`——Spring Boot 启动类不会被实例化，私有构造是工具类规范的过度应用。
- SystemApplication.java:23 `scanBasePackages = {"com.njydsz.system", "com.njydsz.common"}`——扫描整个 common 包过于宽泛，性能上略损且语义模糊。
- **建议**：移除私有构造；改用 `@Import({YdszAuthAutoConfiguration.class, YdszAuditAutoConfiguration.class, ...})` 显式声明，或保留 scanBasePackages 但仅扫 `com.njydsz.system`，common 模块通过自身的 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 自动加载（Spring Boot 3 标准做法）。

#### E3. @Idempotent SpEL 引用未定义参数（P0 必修，疑似 bug）
- ConfigController.java:141 `@Idempotent(key = "ydsz:system:config:save:#userId", ttlSeconds = 5)`——方法签名 `save(ConfigVO vo)` **没有 userId 参数**。
- 依赖 common-lock 是否有 `ThreadLocal` 兜底解析 `#userId`，若无则 SpEL 解析失败，幂等保护**静默失效**。
- **需立即验证**：跑一次 `save` 调用，观察 Redis 是否真的写入 `ydsz:system:config:save:#userId` 字面量键（说明 SpEL 失败回退到字面量），还是写入 `ydsz:system:config:save:1001` 之类（说明 ThreadLocal 兜底成功）。

#### E4. VO/DTO 角色混淆（P1）
- ConfigController.save(@Valid @RequestBody ConfigVO vo) / update(@Valid @RequestBody ConfigVO vo)——**ConfigVO 既是响应又是请求**。
- 违反阿里规范「Query / Command / View 三分离」。
- **建议**：拆分 ConfigSaveCommand / ConfigUpdateCommand / ConfigVO 三类，分别承担请求入参 / 响应出参 / 列表展示。

#### E5. Controller 内 try-catch 业务异常（P2）
- ConfigController.java:340-349 `importConfigs` 在 Controller 内 try-catch 包装为 ImportResult——业务异常应抛到全局 ExceptionHandler 统一处理，不应在 Controller 内吞异常返回 success。

### 2.5 过度设计维度

#### O1. 3 个 common 依赖引了不用（P0 必修）
- `ydsz-system-server/pom.xml` 引入：
  - common-thread（pom:124-127）→ server/src 内 0 个 import
  - common-feign（pom:32-35）→ server/src 内 0 个 import（@EnableYdszFeign 在 web/SystemApplication:11）
  - common-lock（pom:28-31）→ server/src 内 0 个 import
- **验证方式**：`grep -r "com.njydsz.common.thread" ydsz-system-server/src`、`grep -r "com.njydsz.common.feign" ydsz-system-server/src`、`grep -r "com.njydsz.common.lock" ydsz-system-server/src` 均返回 0 命中。
- **注意**：common-feign 和 common-lock 是否被 web 层使用？web/pom.xml 未显式声明两者，而是通过 server 传递依赖。建议在 web 层显式声明需要的依赖，server 剔除。

#### O2. InternalApiIpFilter 与 common-safe 共存（P2）
- README 第 220 行说"已由 ydsz.system.internal-api-ip-whitelist 迁移至 ydsz.safe.ip-access（common-safe 统一管控）"。
- 实际 InternalApiIpFilter.java 仍在，但已是「薄壳委托」：内部委托 `IpAccessService.isAllowed(clientIp)`（第 113 行）。
- **不是过渡期并存**，而是合理的「Filter Bean 壳 + 委托」——README 表达有误，应改为"Filter Bean 保留，实现委托 common-safe"。

#### O3. 双重幂等保护叠加（P2）
- ConfigController 每个写接口叠加：@Idempotent（5 秒 Redis SET NX EX） + @RateLimit（50 QPS） + @Audit（异步审计）。
- 50 QPS 对内网 system 模块过于宽松（内网不会有人工 50 QPS 提交配置），@RateLimit 与 @Idempotent 职责重叠。
- **建议**：内网场景降级 @RateLimit 阈值到 10 QPS 或移除（@Idempotent 已防重复提交）。

#### O4. ydsz-system-app 占位桩（P2）
- 3 个空壳 Java 文件，README 自承"预留"。
- server 通过 pom:22 依赖引入了一个无业务的子模块，徒增构建复杂度。
- **建议**：要么填充实际 App 端接口，要么移除该子模块，统一由 server 兼任。

#### O5. Excel 导出在 Controller 内拼装文件名（P2）
- ConfigController.java:305-307 在 Controller 内手动拼 `config_xxx_timestamp.xlsx` 文件名。
- **建议**：抽出 `ExcelWebSupport.buildExportFilename(prefix, group)` 工具方法，复用。

---

## 3. 优化建议清单（按 P0 → P1 → P2 排序）

### P0 — 立即修复（影响规范合规与正确性）

| # | 标题 | 文件 / 位置 | 落地步骤 |
|---|---|---|---|
| P0-1 | 修复实体位置 doc drift | README.md:53-64 | 二选一：(a) 实体迁移到 domain/entity（推荐，需同时迁移 RepositoryImpl 内部转换）；(b) README 改为"实体位于 infra/entity（infra 持久化层）"，承认架构选择 |
| P0-2 | 修复 SystemProperties doc drift | SystemProperties.java:20 Javadoc | 删除"`ydsz.system.internal-api-ip-whitelist`"条目，改为指向 `ydsz.safe.ip-access.*` |
| P0-3 | 验证 @Idempotent SpEL | ConfigController.java:141,164,188,224 | 跑 save 调用，查 Redis 是否写入字面量 `#userId`（说明 SpEL 失败）。若失败：(a) 在方法签名补 `@UserId String userId` 参数（由 common-auth 注入）；或 (b) 改用 `T(com.njydsz.common.security.SecurityUtils).currentUserId()` |
| P0-4 | 剔除 3 个未使用 common 依赖 | ydsz-system-server/pom.xml:28-31,32-35,124-127 | `mvn dependency:analyze` 验证后，从 server/pom 移除 common-thread / common-feign / common-lock；若 web 层需要，在 web/pom 显式声明 |
| P0-5 | 核查 ydsz_config 多租户隔离 | deploy/sql/init.sql + ydsz_config 表 | 核查表是否有 `tenant_id` 列 + 索引；若否，补 DDL；ConfigRepository 的 QueryWrapper 是否带 tenant_id 条件 |
| P0-6 | 修复 README 缓存策略描述 | README.md:140-156 | 改为"ydsz-common-cache 进程内本地缓存 + Outbox 事件广播跨实例一致性"，删除 Redis 二级缓存描述 |
| P0-7 | 修复 README 模块树遗漏 app | README.md:42-102 | 在模块树补充 ydsz-system-app 子模块条目，或注明"app 为预留空壳，待填充" |
| P0-8 | domain 层去技术依赖 | ydsz-system-domain/pom.xml | 移除 common-jdbc / mybatis-plus-annotation / spring-security-crypto / common-excel / common-safe 依赖；这些应只在 infra / server 层引入。domain 仅留 common-core / common-exception / common-domain / jakarta.validation / swagger-annotations |

### P1 — 短期优化（1-2 个迭代内完成）

| # | 标题 | 文件 / 位置 | 落地步骤 |
|---|---|---|---|
| P1-1 | 拆分 ConfigServiceImpl | ConfigServiceImpl.java（748 行） | 拆为 ConfigApplicationService（CRUD 编排）+ ConfigCacheManager（缓存管理）+ ConfigEventCoordinator（事件协调）+ ConfigExcelFacade（导入导出）；目标单类 < 300 行 |
| P1-2 | 抽取领域服务到 domain 层 | ydsz-system-domain/ 新建 service/ 包 | 将 Config.validate() / getTypedValue() 等充血方法保留，但跨聚合编排逻辑（版本快照 + 搜索同步 + 事件发布）抽到 ConfigDomainService |
| P1-3 | 拆分 VO / Command / DTO | ConfigController.java:143,166 等 | 新建 ConfigSaveCommand / ConfigUpdateCommand；ConfigVO 仅作响应；Controller 入参改为 Command |
| P1-4 | 简化缓存策略 | CacheConfig.java + ConfigServiceImpl Outbox 部分 | 评估是否移除 Outbox 事件广播（system 模块变更低频，5 分钟 TTL 兜底足够）；保留则文档化「为什么 system 也需要事件总线」 |
| P1-5 | 跨聚合操作改异步 | ConfigServiceImpl.save() / updateById() | 将 entityVersionService.createVersion + indexUpsert 改为 `@TransactionalEventListener(AFTER_COMMIT)` 异步处理，减少写路径延迟 |
| P1-6 | 贫血实体充血化 | Tenant.java / DictType.java | 为 Tenant 补 activate() / suspend() / changePlan() 等领域方法；为 DictType 补 validate() / itemCountLimit() 等 |
| P1-7 | 配置灰度发布能力 | ydsz_config 表 + Config 实体 + ConfigService | 增加 gray_target 字段（tenant_id 列表 / IP 段 / user 标签）；新增 ConfigGrayService |
| P1-8 | 配置变更审批流 | 新增 ConfigChangeRequest 实体 + 状态机 | 走 common-workflow 或自建轻量审批：草稿 → 待审批 → 已审批 → 生效 → 已回滚 |
| P1-9 | 修复 importConfigs 异常处理 | ConfigController.java:337-350 | 移除 Controller 内 try-catch，让业务异常抛到全局 ExceptionHandler 统一返回 |
| P1-10 | strictValidation 默认改 true | SystemProperties.java:68 | 生产环境应严格校验配置值格式；保留 Nacos 配置可降级 false 用于紧急兼容 |
| P1-11 | 启动类清理 | SystemApplication.java:23,34 | 删除私有构造；改 scanBasePackages 仅扫 `com.njydsz.system`，common 模块走 Spring Boot 3 AutoConfiguration.imports 标准加载 |

### P2 — 长期演进（按需排期）

| # | 标题 | 落地步骤 |
|---|---|---|
| P2-1 | ydsz-system-app 决策 | 填充实际 App 端接口，或移除该子模块（推荐移除，统一由 server 兼任） |
| P2-2 | 配置推送回执 | Outbox 增加 subscriber ACK 机制 + 死信队列 |
| P2-3 | 字典国际化 | DictItem 增 display_name_i18n JSON 字段，按 Accept-Language 解析 |
| P2-4 | 应用密钥轮换 | AppInfo 增加 secret_version + secret_previous_hash 字段，支持主备共存 |
| P2-5 | Excel 工具方法抽取 | ExcelWebSupport 补 buildExportFilename 工具方法 |
| P2-6 | @RateLimit 阈值收敛 | 内网场景 50 QPS 改为 10 QPS 或移除（@Idempotent 已防重复） |
| P2-7 | Micrometer 指标参考样板 | 将 SystemMetrics 的 hot path 埋点模式抽取为其他模块参考实现 |

---

## 4. 不推荐做的（过度设计风险）

### N1. 不要为 system 模块引入 CQRS / Event Sourcing
- system 模块是基础配置/字典服务，写入频率极低，引入 CQRS 分离读写模型得不偿失。
- EntityVersion 已提供快照回滚能力，无需 Event Sourcing 全量事件溯源。

### N2. 不要为 system 模块引入独立的消息队列（Kafka / RocketMQ）
- Outbox + 本地事件表 + Spring `@Async` 已足够；引入 MQ 增加运维负担，违反「最小化外部依赖」原则。
- 若订阅方增多到 10+ 个，可考虑切换到 common-event 的 RocketMQ backend（不是新增依赖，是 backend 切换）。

### N3. 不要强行补全贫血实体到全部充血
- DictType 只有 3 个字段，强行加领域方法是过度设计。建议按业务行为密度判断：Config / Variable 适合充血；DictType / TenantPlanMenu 适合贫血。

### N4. 不要为 P0-1 强行迁移实体到 domain 层
- 实体迁移涉及 RepositoryImpl 转换逻辑、Mapper import 调整、构建期纯净度校验触发等多点改动，**风险高于收益**。
- 推荐 P0-1 走方案 (b)：README 改文档承认"实体在 infra 层"，符合「infra 持有持久化模型」的工程现实，云顶规范允许这种分层选择。

### N5. 不要为 system 模块引入 K8s ConfigMap / Nacos 外部配置中心
- ydsz-system 自身就是配置中心，引入外部配置中心形成循环依赖。配置应自治（DB + 本地缓存）。

---

## 5. 落地路径建议

### 阶段 1（本周）：P0 修复
- 优先 P0-3（@Idempotent SpEL bug 验证）——可能是真实线上 bug。
- 同步 P0-1 / P0-2 / P0-6 / P0-7（README + Javadoc doc drift 修复，零代码风险）。
- P0-4（剔除未使用依赖）需 `mvn dependency:analyze` 验证后操作。
- P0-5（多租户隔离核查）需读 init.sql 与 Repository 实现。
- P0-8（domain 去依赖）需要兼容性测试，建议本迭代末或下迭代初做。

### 阶段 2（2-3 周内）：P1 优化
- P1-1 / P1-2（ConfigServiceImpl 拆分 + 领域服务抽取）联动做，先抽领域服务再拆应用服务。
- P1-3（VO/Command 分离）跟随 P1-1 一起做，避免二次改动 Controller。
- P1-7 / P1-8（灰度 + 审批流）作为新增功能，需先与产品对齐需求再开发。

### 阶段 3（按需）：P2 演进
- P2-1（app 模块决策）建议直接移除，简化构建图。
- 其余 P2 项按业务驱动。

### 验证基线
- 每阶段完成后跑 `mvn clean test` + `mvn dependency:analyze` + 构建期纯净度校验。
- 关键变更（如 P0-8 domain 去依赖）后，跑全量集成测试确认无编译/运行时错误。

---

## 6. 参考对标清单

| 对标对象 | 关键差距 | 本报告对应项 |
|---|---|---|
| 阿里 Java 开发手册（泰山版） | 单类 1000 行限制、VO/DTO 分离、SpEL 表达式规范 | P1-1 / P1-3 / P0-3 |
| 腾讯代码规范 | 启动类规范、模块边界清晰 | P1-11 / P2-1 |
| Spring Boot 3 最佳实践 | AutoConfiguration.imports 标准加载、scanBasePackages 收敛 | P1-11 |
| Apollo 配置中心 | 灰度发布、审批流、推送回执 | P1-7 / P1-8 / P2-2 |
| Nacos 配置中心 | 多租户隔离、配置版本管理 | P0-5（核查） |
| QConfig | 配置值类型校验、严格模式 | P1-10 |
| DDD 标准实践（Evans / Vaughn） | 实体位置、领域服务、聚合根 | P0-1 / P1-2 / P1-6 |
| 云顶编码规范 | common 层 L1 四 utility 模块、common-json / common-cache / 自研 POI 强制使用 | P0-8 |

---

**报告版本**：v1.0
**生成时间**：2026-08-19
**分析基础**：ydsz-system 模块最新代码（截至 2026-08-19）+ README.md + 各 pom.xml + 关键 Service/Controller/Entity/Config 实现
**未覆盖项**：deploy/sql/init.sql 表结构（P0-5 需补充核查）、common-event / common-cache / common-safe 的内部实现（默认按规范合规假设）
