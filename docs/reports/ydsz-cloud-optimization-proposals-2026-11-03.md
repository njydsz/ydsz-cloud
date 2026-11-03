# ydsz-cloud 后续优化完善建议（2026-11-03）

> 分析范围：**仅本仓库 `ydsz-cloud`**（Java 21 / Spring Boot 4.1 / PostgreSQL 18 / 9 个业务微服务 + 30 个 common 子模块）。
>
> 方法：只读静态审计。所有关键结论均用源码、SQL、pom、CI 配置交叉核验，并标注验证方式。
>
> **重要说明**：仓库中另有一份 `fullstack-optimization-roadmap-2026-11-03.md`，其中大量结论引用 `ydsz-micro/` 前端目录。经核实，**本仓库不含 `ydsz-micro`**（无该目录、无 submodule、无 package.json），因此这些前端结论在本仓库**无法验证**。本报告不重复、不背书这些结论，只覆盖后端与工程链路中可被源码证实的问题。

---

## 一、总体判断

平台骨架已经比较完整：DDD 分层、统一响应、i18n、权限、审计、幂等、缓存、Testcontainers、ArchUnit、增量门禁脚本均已落地。当前的主要矛盾不是"缺能力"，而是**若干关键链路存在确定性缺陷**，以及**一批已识别的优化项没有闭环**。

按"是否会造成线上事故 / 是否让门禁假绿 / 是否只是成本"三分法，建议后续优化分三批推进：

| 批次 | 主题 | 判断依据 |
|------|------|---------|
| **第一批（止血）** | 数据安全、错误信息泄露、索引缺失、Mapper 类型 | 有确定性故障路径 |
| **第二批（闭环）** | 已识别优化项落地、测试金字塔、Controller/事务治理 | 已知欠账，有现成清单 |
| **第三批（增强）** | 性能、体验、契约统一、可访问性 | 成本型，不阻塞发布 |

---

## 二、第一批：确定性缺陷（建议优先处理）

### 2.1 数据库脚本不能作为升级脚本使用 —— 最高优先级

**事实**（已验证）：
- `data/sqls/flyway/V26.10.01.sql` 含 **173 条 `DROP TABLE`**，且 `grep flyway_schema_history` 无结果 —— 该文件是**空库初始化脚本**，不是升级脚本。
- 全部 12 个 `V*.sql` 中，只有 `V26.10.01.sql` 含 DROP；其余版本均为增量。
- `pom.xml:61` 明文禁止 Flyway/Liquibase；仓库中**没有**任何迁移执行框架，也没有 `flyway_schema_history` 表。
- `data/scripts/` 下**没有** `check-db-migrations.py` 这类危险变更检测脚本（仅有 checkstyle / 覆盖率 / 错误码 / 编码 / 网关路由五个门禁脚本）。
- `CLAUDE.md:20` 与 `CLAUDE.md:407-408` 仍写"**Flyway 版本管理**""Flyway 已启用"，与 `pom.xml:61` 和 `AGENTS.md:79` 的"禁止 Flyway"**直接矛盾**。

**风险**：任何人按 CLAUDE.md 的表述把 `V26.10.01.sql` 当基线升级脚本执行，会**清空全部业务表**。这是当前仓库中后果最严重的一处。

**建议**：
1. 立即把 `V26.10.01.sql` 重命名为 `V26.10.01.bootstrap.sql`（或移入 `data/sqls/bootstrap/`），并在文件头加显式警告。
2. 新增 `V*.upgrade.sql` 命名约定：只允许 additive / expand-contract 变更。
3. 新增 `data/scripts/check-db-migrations.py`：扫描所有 `V*.sql`，对 `DROP TABLE` / 无备份 `DROP COLUMN` / 无 `WHERE` 的 `DELETE` / `TRUNCATE` 判红；CI 中作为独立步骤。
4. 修正 `CLAUDE.md:20,407-408` 的 Flyway 表述，与 `AGENTS.md:79`、`README.md:167` 对齐。

**验收**：生产升级脚本破坏性语句为 0；`CLAUDE.md` 与 `AGENTS.md` 表述一致；CI 新增 SQL 危险变更检测步骤。

---

### 2.2 错误信息直接泄露给前端

**事实**（已验证）：
- `DictItemController.java:336`：`return YdszResponse.error("Import failed: " + e.getMessage());`
- `FlowDefinitionController.java:168`：`return YdszResponse.error(YdszResultCode.BAD_REQUEST, "Failed to read ZIP file: " + e.getMessage());`
- `ImportExportController.java:98`（generator）：同上模式。
- `RuleAdminController.java:253,255`：`YdszResponse.error(..., e.getMessage())`。
- `LiteruleExceptionHandler.java:231,245`：全局异常处理器中 `YdszResponse.error(..., e.getMessage())`。
- 另有 `ClusterMigrationInternalController.java:93,118` 把 `e.getMessage()` 拼进 i18n 消息参数。
- 全仓 `e.getMessage()` 出现 **2416 次**（已排除 target/test/backup_logs），其中大量用于日志（合理），但上述 6+ 处直接进响应体。

**风险**：SQL 片段、类名、文件路径、内部堆栈语义会随 `e.getMessage()` 返回给客户端；同时硬编码英文违反 I18N-002。

**建议**：
1. Controller 层删除全部 `e.getMessage()` 响应，改为 `I18n.message("module.xxx.failed")` + `log.error` 记录细节。
2. 全局 ExceptionHandler 中区分系统异常与业务异常：系统异常返回固定文案 + traceId，细节只进日志。
3. 新增门禁脚本 `check-error-message-leak.py`：扫描 `YdszResponse.error(.*getMessage()` 与 `throw new.*Exception(.*getMessage()`，增量判红。

**验收**：Controller 与全局 Handler 中 `e.getMessage()` 进响应体的数量为 0。

---

### 2.3 待优化索引未执行 —— 有 P0 级缺口

**事实**（已验证）：
`data/sqls/flyway/INDEX_ANALYSIS.md` 列出 **22 条待优化索引**，其中 P0 1 条、P1 12 条。经 grep 全部 `V*.sql`，**这些索引一个都没有被创建**：

| 表 | 字段 | 场景 | 优先级 |
|----|------|------|--------|
| `ydsz_flow_run_task` | `assignee_id` | 待办任务查询（按办理人） | **P0** |
| `ydsz_flow_instance` | `start_user_id` | 我发起的实例查询 | P1 |
| `ydsz_file_file_node` | `space_id` / `parent_node_id` | 空间文件列表 / 目录树 | P1 |
| `ydsz_comm_audit_log` | `business_no` | 业务编号反查审计链 | P1 |
| `ydsz_comm_outbox` | `trace_id` | 链路追踪 | P1 |
| `ydsz_msg_log` | `receiver + status` / `biz_type + biz_id` | 消息查询 | P1 |
| `ydsz_agt_async_task` | `user_id` | 用户异步任务 | P1 |
| `ydsz_agt_trace_step` | `step_type` | 步骤类型筛选 | P1 |

**建议**：
1. 新增 `V26.11.03.sql`，用 `CREATE INDEX CONCURRENTLY IF NOT EXISTS` 补建上述索引（P0 优先）。
2. 在测试环境对每条索引跑 `EXPLAIN ANALYZE`，确认命中；对大表记录构建耗时。
3. 把"待优化清单"从 markdown 迁移为可执行清单（如 `data/sqls/flyway/PENDING_INDEXES.md` 或 JSON），并在 CI 中校验"清单中的索引是否已在某个 V*.sql 中落地"。

**验收**：P0/P1 索引全部落地；`INDEX_ANALYSIS.md` 待优化清单清零或转为已排期状态。

---

### 2.4 Mapper XML 布尔字面量 —— 结论：**未发现违规**

**事实**（已验证）：全仓 `*/src/main/resources/mapper/*.xml` 扫描 `\b(true|false)\b`，排除注释与属性后**无任何命中**。布尔字段统一使用 `is_deleted = 0` 字面量（见 `FlowInstanceMapper.xml`、`SearchIndexMapper.xml` 等），与 `SMALLINT` 列定义一致。

**说明**：此前 `fullstack-optimization-roadmap-2026-11-03.md` 声称的 `UserRecentMapper.xml:25`、`SpaceMapper.xml:38`、`GenDatasourceMapper.xml:21`、`RulePackMapper.xml:34` 存在布尔字面量问题，在本仓库中**未复现**。该结论可能来自另一个分支或前端/历史版本，建议核实后从路线图移除，避免误导。

---

## 三、第二批：已知欠账闭环

### 3.1 N+1 查询治理 —— 清单明确，尚未处理

**事实**（已验证）：源码中保留 **8 处显式 TODO**，全部集中在 workflow 模块：

| 位置 | 问题 |
|------|------|
| `YdszWorkflowFacade.java:349` | 一键通过仅取第 1 页 100 条，超过 100 条待办不会被处理 |
| `YdszWorkflowFacade.java:428` | 时间线查询执行 3 次独立 DB 查询 |
| `FlowInstanceBatchOperator.java:166` | 循环内逐条调用 terminate（每条独立事务 + DB 写入） |
| `FlowInstanceQueryService.java:138,251` | 降级路径 N 次 API 调用；findPage + countPage 两次查询 |
| `FlowTaskQueryServiceImpl.java:192` | 整体 3 次 DB 查询 |
| `FlowTaskTransferServiceImpl.java:157` | 涉及 deptId 的在途流程批量更新审批人 |

其他模块已有正面样板：`SlowTaskDetector`、`AnomalyRecoveryScanner`、`JobServiceImpl:824,869`、`AggregateServiceImpl` 均已做批量预加载。

**建议**：按 TODO 逐条立项，优先处理 `FlowInstanceBatchOperator`（循环内独立事务是并发隐患）与 `YdszWorkflowFacade:349`（一键通过漏单是业务正确性问题）。

---

### 3.2 测试金字塔失衡 —— Domain 层零覆盖

**事实**（已验证）：

| 层 | 测试文件数 |
|----|-----------|
| web | **190** |
| server | 37 |
| infra | 9 |
| **domain** | **0** |
| SmokeTest（占绝大多数） | **186** |
| IT | 4 |

即：**190 个 web 层 SmokeTest + 0 个 domain 层测试**。SmokeTest 主要验证"接口可启动"，不验证业务不变量。ArchUnit 分层守护已覆盖 9 个模块（正确方向），但领域逻辑零守护。

**建议**：
1. 把"接口可启动"类 SmokeTest 降级为健康检查，不再占用 CI 预算。
2. 优先为 domain 层补单元测试：workflow 状态机（`FlowInstanceStateMachine` / `FlowTaskStateMachine`）、literule 规则求值、nextwiki 文件树/配额、cronjob 调度算法、金额/权限判断。
3. infra 层 Testcontainers IT 扩展到每个模块至少 1 个核心 Repository。
4. 门禁目标：**domain 层行覆盖率 ≥ 40%**（增量代码已有 40% 门禁，此处指 domain 包整体）。

---

### 3.3 Controller 层 try-catch 与契约违规

**事实**（已验证）：

- **Controller 内 try-catch**：`FlowMonitorDashboardController`（16 处）、`DownloadController`（6）、`UserAccountController`（5）、`RuleDslController`（5）、`RuleDecisionTableController`（4）、`RuleBatchController`（4）、`InternalJobController`（4）、`SamlController`（3）、`CasController`（3）等。
- **契约违规**（返回非 `YdszResponse`/`PageResponse`）：
  - `OutboxAdminController.getStatistics()` 返回 `Map<String, Long>`
  - `I18nAdminController` 5 个端点返回 `Map<String, Object>`
  - 多个导出端点返回 `void`（写 HttpServletResponse）—— 此类可接受，但应统一约定。
- **`@Transactional` 未显式声明 rollbackFor**：全仓 608 处 `@Transactional`，仅 437 处显式声明 `rollbackFor = Exception.class`，**171 处缺失**。

**建议**：
1. Controller 异常统一交全局 advice，删除局部 try-catch（保留导出/下载等需要流式响应的例外）。
2. Admin 类 Controller 统一返回 `YdszResponse<T>`。
3. 全仓 `@Transactional` 统一显式声明 `rollbackFor = Exception.class`（可通过ArchUnit 规则或脚本门禁增量约束）。

---

### 3.4 事务边界审查

**事实**（已验证）：
- `@Transactional(readOnly = true)`：84 处（只读标记已使用，方向正确）。
- `Propagation.REQUIRES_NEW`：仅 4 处。
- `saveBatch` / `insertBatch` 等批量方法存在，但未见显式 batchSize 分片（MyBatis-Plus 默认每批 1000 条）。

**建议**：
1. 对跨服务写操作（workflow + message、nextwiki + audit）逐项确认事务传播与幂等语义。
2. 批量写入统一走 `ExecutorUtils.buildAndRegister()` 并设置分片大小上限，避免大事务。

---

### 3.5 错误码重复 —— 有基线但需持续收敛

**事实**（已验证）：
- `data/scripts/check-error-code-unique.py` 已存在且 CI 已接入（`ci.yml:102-103`）。
- 脚本头部说明：实测 **528 个码、16 个重复占用**，其中 `B91001` 被 system（字典类型不存在）与 message（模板不存在）同时占用。
- 存量进 `data/error-code-baseline.json` 棘轮，只减不增（方向正确）。

**当前状态**：门禁已建立，但**存量 16 条冲突尚未清零**。重复码会导致前端按 code 反解文案时互相覆盖（用户看到错误文案）。

**建议**：
1. 优先处理语义冲突的 16 条（尤其 `B91001`），分配新码并保留旧码兼容期。
2. 前端 `error-codes.generated.ts` 与后端 `ErrorCodeTable` 建立一致性校验（当前未见该脚本，建议补充）。
3. 目标：两个版本内清零。

---

### 3.6 i18n 硬编码残留

**事实**（已验证）：
- 生产源码中 `throw new ...Exception("...中文...")` **仅剩 3 处**（`ExcelStream.java:203`、`CommonQueueMessageOperations.java:73`，另 2 处为 Javadoc 示例）：说明此前 205 处硬编码中文治理**已基本完成**。
- 但工作区正在进行的改动把部分中文改为英文硬编码（如 `LockAdminController`：`"参数错误：keys 不能为空"` → `"Parameter error: keys must not be empty"`）—— 这是**从硬编码中文退化为硬编码英文**，仍违反 I18N-002（禁止 Service/Controller 层硬编码文案）。
- `AgentRequestGuard` 中仍有 `.message("请求过于频繁...")` / `.message("重复请求...")` 硬编码中文。

**建议**：
1. 停止"中文→英文"的机械替换，改为走 `I18n.message(key, params)` + properties 三语 key。
2. 新增门禁：扫描 Controller/Service 层 `YdszResponse.error("...")` 与 `.message("...")` 中的非 i18n 调用，增量判红。

---

### 3.7 性能压测基础设施未接入 CI

**事实**（已验证）：
- `data/perf-tests/` 存在 Gatling 工程（`CloudPerfTest.scala`），但根 `pom.xml` 仅在 profile（`:1548`）中注册 `<module>data/perf-tests</module>`，**默认不在构建路径**。
- CI（`ci.yml`）**没有任何 perf-test 步骤**。
- 现有场景只覆盖 `/actuator/health` 与 `/actuator/info`，**不覆盖任何业务端点**。

**建议**：
1. 补充核心业务场景：登录 → 列表分页 → 详情 → 写入 → 导出，带认证。
2. 定义基线指标（P99 < 500ms @ 50 并发）与告警阈值。
3. 作为 nightly 任务运行，产出趋势报告；不做 PR 门禁（避免 CI 过载）。

---

## 四、第三批：增强型优化

### 4.1 字段注入改造

**事实**（已验证）：全仓 `@Autowired` 字段注入 **150 处**，其中 common 60、workflow 57、nextwiki 10。workflow 是重灾区（本次工作区已在做 `FlowInstanceLifecycleManager` 的依赖收敛，方向正确）。

**建议**：逐步改为构造器注入（配合 Lombok `@RequiredArgsConstructor`），便于测试与不可变。可先对新增代码强制，存量按模块棘轮。

---

### 4.2 超长方法

**事实**（已验证）：发现多处 >100 行方法，最突出的是 `FileApplicationService.java:505-643`（约 138 行，`listFiles` 实现）。

**建议**：按职责拆分（查询组装 / 权限过滤 / VO 转换），单方法控制在 50 行内。可加 Checkstyle `MethodLength` 规则增量约束。

---

### 4.3 索引命名不一致

**事实**（已验证）：`V26.10.08.sql` 中 10 条 trigram 索引使用 `idx_ydsi_` 前缀（`idx_ydsi_file_search_index_*`、`idx_ydsi_rule_def_*`），而全仓其余索引统一为 `idx_ydsz_`。这是明显笔误。

**影响**：不影响功能（索引名只是标识），但会干扰后续按前缀检索/清理索引的运维脚本。

**建议**：在下一个迁移版本中 `DROP INDEX IF EXISTS` + 重建为 `idx_ydsz_` 前缀（注意 `CONCURRENTLY`）。

---

### 4.4 OpenAPI 事实源不可靠

**事实**（已验证）：
- `pom.xml:1309-1320` 把 `springdoc-openapi-maven-plugin` 绑定到 `compile` 阶段。
- `pom.xml:1499-1505` 配置 `apiDocsUrl = http://localhost:${server.port}/v3/api-docs`。
- 普通 `mvn compile` **不会启动服务**，因此该插件在常规构建中拿不到 spec（除非有服务在跑）。
- CI 中未见稳定的 OpenAPI artifact 产出步骤。

**建议**：改用 MockMvc / `@SpringBootTest` 离线生成 spec，或为每个服务发布带 commit SHA 的 OpenAPI artifact。

---

### 4.5 缓存使用统计

**事实**（已验证）：
- `YdszCache.newBuilder()`：**仅 12 处**（common-cache 已有完整能力，含 MultiLevelCache）。
- `@Cacheable`/`@CacheEvict`/`@CachePut`：**63 处**（Spring Cache 抽象为主）。
- 硬编码 Redis key：扫描无命中（此前治理已完成）。

**判断**：缓存体系本身健康，但**本地缓存采用率偏低**。建议对高频只读数据（字典、配置、规则定义、租户信息）评估引入 `MultiLevelCache`，减少 Redis 往返。

---

### 4.6 幂等与分布式锁

**事实**（已验证）：
- `DistributedLocker.tryLock` 使用良好（agent `AgentRequestGuard`、cronjob `JobLockManager`/leader 选举、literule `RuleApprovalService`、nextwiki `FileApplicationService`、userinfo LDAP/Provision）。
- `@Idempotent` / `@RepeatSubmit` 注解仅在 system 模块 `AppInfoController` / `ConfigController` 使用。
- **发现一处信息泄露**：`RepeatSubmitAspect.java:102` 把用户提交的 token 明文打进 warn 日志（`token={}`），未脱敏。

**建议**：
1. 立即对 `RepeatSubmitAspect` 的 token 日志做脱敏（`MaskUtils.mask(token, 3, 3)`）或只记录 hash。
2. 对更多写接口（审批、支付、导出、批量操作）推广 `@Idempotent`。

---

### 4.7 未使用 import —— 存在一批真实冗余

**事实**（已验证，扫描 4581 个主源码文件）：
- 候选未使用 import **155 处**，排除注解误报后 **139 处**。
- 抽样复核确认真实存在：`ExcelFacade.java` 的 `LocalDateTime`/`DateTimeFormatter`、`LocalSessionRegistry.java` 的 `Set`/`AtomicInteger`、`InternalHeaderSigner.java` 的 `I18n`、`MicrometerFunctionMetricsBridge.java` 的 `Map` 等均只在 import 行出现一次。
- 集中分布：`ydsz-common-excel` 32 处、`ydsz-common-thread` 9、`ydsz-common-safe` 7、`ydsz-common-feign` 6、`ydsz-common-socket` 6、`cronjob-domain` 6、`common-netty` 5。
- 说明：Checkstyle `UnusedImports` 规则在 `docs/checkstyle.xml` 中**只对模块自身路径生效**，且 `severity` 为全局 error，但**构建期不执行 checkstyle**（无 phase 绑定），因此这批量未被门禁拦截。

**建议**：
1. 用 IDE 或脚本批量清理 139 处（优先 common-excel / common-thread / common-safe）。
2. 新增 `check-unused-imports.py` 门禁，基于 AST 精确判定（避免注解误报），增量判红。
3. 与既有 `check-checkstyle-delta.py` 合并执行，避免新增 CI 步骤成本。

---

### 4.8 数据库外键策略

**事实**（已验证）：`V26.10.01.sql` 中 `FOREIGN KEY` 仅 **6 处**，而唯一索引 89 处、普通索引 433 处。

**判断**：这是**有意为之**的"应用层引用完整性"策略（常见于多租户 + 分库场景），不是缺陷。但应在文档中明确记录该策略与补偿机制（如定期一致性校验任务），否则会被误判为遗漏。

**建议**：在 `docs/云顶编码规范.md` 数据库章节明确"本项目不使用数据库外键，引用完整性由应用层 + 定时校验保证"，并补充一个定时校验任务（可对账 outbox / audit / 主表）。

---

## 五、工程门禁现状与补充建议

### 5.1 现有门禁（已验证）

| 脚本 | 作用 | 状态 |
|------|------|------|
| `check-checkstyle-delta.py` | 改动文件 0 违规 + 存量只减不增 | ✅ 已接入 CI |
| `check-diff-coverage.py` | 增量行覆盖率 ≥ 40% | ✅ 已接入 CI |
| `check-error-code-unique.py` | 错误码全局唯一（16 条存量基线） | ✅ 已接入 CI |
| `check-file-encoding.py` | UTF-8 无 BOM | ✅ 已接入 CI |
| `check-gateway-routes.py` | 网关路由一致性 | ✅ 已接入 CI |
| ArchUnit × 9 模块 | DDD 分层守护 | ✅ 已存在 |

### 5.2 建议新增门禁

| 优先级 | 门禁 | 作用 |
|--------|------|------|
| P0 | `check-db-migrations.py` | SQL 危险变更检测（DROP/TRUNCATE/无 WHERE DELETE） |
| P0 | `check-error-message-leak.py` | `e.getMessage()` 进响应体检测 |
| P1 | `check-i18n-hardcode.py` | Controller/Service 层硬编码文案检测（中英均判红） |
| P1 | `check-transaction-declaration.py` | `@Transactional` 缺少 `rollbackFor` 检测 |
| P2 | `check-index-pending.py` | 待优化索引是否已落地检测 |
| P2 | `check-openapi-artifact.py` | OpenAPI spec 构建期产出检测 |

---

## 六、优先级汇总与排期建议

| 优先级 | 事项 | 预估工作量 | 阻塞发布？ |
|--------|------|-----------|-----------|
| **P0** | DB 脚本改名 + 危险变更门禁 + CLAUDE.md 修正 | 0.5 天 | 是（数据安全） |
| **P0** | Controller/Handler 错误信息泄露修复 | 1 天 | 是（安全） |
| **P0** | RepeatSubmitAspect token 日志脱敏 | 0.5 小时 | 是（安全） |
| **P0** | 待优化索引落地（P0+P1） | 1 天 | 否（但影响性能） |
| **P1** | N+1 TODO 逐条闭环（workflow 8 处） | 3-5 天 | 否 |
| **P1** | Domain 层单元测试补齐（≥40%） | 5-8 天 | 否 |
| **P1** | Controller try-catch / 契约统一 | 2-3 天 | 否 |
| **P1** | `@Transactional` rollbackFor 统一 | 1 天 | 否 |
| **P1** | 错误码 16 条冲突清零 | 2 天 | 否 |
| **P2** | i18n 硬编码门禁 + 存量治理 | 2 天 | 否 |
| **P2** | 字段注入改造（workflow 优先） | 3 天 | 否 |
| **P2** | 索引命名 `ydsi_` → `ydsz_` 修正 | 0.5 小时 | 否 |
| **P2** | OpenAPI 离线生成改造 | 2 天 | 否 |
| **P2** | 未使用 import 清理（139 处）+ 门禁 | 1 天 | 否 |
| **P3** | 压测场景扩展 + nightly | 2 天 | 否 |

---

## 七、关于 `fullstack-optimization-roadmap-2026-11-03.md` 的说明

仓库中存在一份同名日期路线图，其中以下结论在本仓库**无法验证**，建议核实后再决定是否采纳：

1. **前端相关全部结论**（E2E 选择器 `.app-container`、`v-permission` 指令、vitest 覆盖率配置、bundle 预算等）—— 本仓库不含 `ydsz-micro`。
2. **Mapper XML 布尔字面量违规**（`UserRecentMapper.xml:25`、`SpaceMapper.xml:38` 等）—— 本仓库全量扫描**未复现**。
3. **`check-db-migrations.py`** 被描述为已存在 —— 实际**不存在**。
4. **`springdoc` 依赖 localhost** 的结论**属实**（`pom.xml:1503`），但该文件引用的行号与本仓库实际行号有偏差。

建议：若前端确有独立仓库，应在前端仓库单独出路线图；本仓库路线图聚焦后端与工程链路即可。

---

## 八、最终建议

1. **本周先做数据安全三项**：DB 脚本改名 + 危险变更门禁 + CLAUDE.md 修正。这是唯一可能造成不可逆损失的项。
2. **同步修复错误信息泄露**（6+ 处）与 token 日志脱敏，成本极低。
3. **下周落地待优化索引**，并用 EXPLAIN ANALYZE 验证。
4. **随后按批次推进 N+1、测试金字塔、Controller 治理**，每批保持可交付、可验证。
5. **所有门禁遵循"新代码严格、存量棘轮"**，避免一次性放大维护成本；每个门槛都要有可执行脚本、明确 owner 和例外白名单。
