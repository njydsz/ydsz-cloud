# 全栈优化建议报告 — ydsz-cloud & ydsz-micro

> **分析日期**：2026-10-08
> **对标标准**：阿里 Java 开发手册、Google Java Style、OWASP、WCAG 2.1 AA、Web Vitals 2025
> **覆盖范围**：ydsz-cloud（后端 10 业务模块 + 30 common 子模块）、ydsz-micro（前端 micro-kernel 微前端 + 8 子应用）

---

## 一、总览

| 维度 | 当前评分 | 核心结论 |
|------|---------|---------|
| 数据库表设计 | B | 命名/审计字段合规，布尔类型大面积违规（40+ 字段），外键缺失 |
| 接口单元测试 | C+ | 后端初期建设（52 测试/5% 门槛），前端成熟（123 测试+E2E+a11y） |
| 前后端贯通 | A- | 双重生成脚本保契约同步，错误码自动注入 OpenAPI |
| 架构质量 | A | DDD 分层合规，Domain 零内部依赖，公共能力收敛度高 |
| 功能增强 | B+ | 重复实现仍存（导入/导出/搜索/历史），审计覆盖不全 |
| 性能 | B+ | 缓存体系良好，2 处虚拟线程遗留，左模糊 SQL 有慢查风险 |
| 用户体验 | B | i18n 双管道漂移，a11y 仅静态约定，缺分片上传 |

---

## 二、数据库表设计维度

### 2.1 【P0】布尔字段类型合规改造

**现状**：全库 40+ 字段使用 `boolean`/`BOOLEAN`，违反 DB-007 阻断级规范（必须 `SMALLINT`）。

**涉及表举例**：
- `ydsz_comm_audit_log.is_deleted`
- `ydsz_comm_outbox.is_deleted`
- `ydsz_flow_instance.is_deleted`
- `ydsz_msg_log.is_deleted`
- `ydsz_idm_auth_apikey.is_enabled`
- `ydsz_agt_dag_workflow.is_published`

**修复方案**：

```sql
-- 生成批量 ALTER 脚本模板
ALTER TABLE {table} ALTER COLUMN is_deleted SET DATA TYPE SMALLINT 
  USING CASE WHEN is_deleted = TRUE THEN 1 ELSE 0 END;
```

**落地步骤**：
1. 编写 Flyway 脚本 `V26.10.08__migrate_boolean_to_smallint.sql`
2. 夜间低峰执行（需 PT-OSC 在线变更避免锁表）
3. 验证后更新对应 Entity `@TableField` 映射

### 2.2 【P1】外键约束补充

**现状**：仅 generator 模块 6 条 FK，业务表零外键，数据完整性依赖应用层。

**建议补充**：
```sql
-- 核心关联字段建议加 FK（先评估写入性能影响）
ALTER TABLE ydsz_flow_instance ADD CONSTRAINT fk_flow_instance_definition 
  FOREIGN KEY (definition_id) REFERENCES ydsz_flow_definition(id);
```

### 2.3 【P1】索引优化

**左模糊查询慢 SQL**：
- `SearchIndexMapper.xml:67-80` 使用 `ILIKE CONCAT('%', #{keyword}, '%')` 无法走 B-Tree 索引

**修复方案**：
- 方案 A：接入已有 `ydsz-common-search`（含 UnifySearchService 全文检索能力）
- 方案 B：使用 PostgreSQL `tsvector` + GIN 索引

### 2.4 【P2】大表分区扩展

`ydsz_msg_log`、`ydsz_flow_audit_log` 等高频写入表目前仅 flow_audit_log 按月分区，建议：
- `ydsz_msg_log` 按月 RANGE 分区
- `ydsz_flow_audit_log` 保留 3 个月热数据 + 冷数据归档

---

## 三、接口单元测试维度

### 3.1 【P0】后端覆盖率基线提升

**现状**：52 个测试类，仅覆盖 Controller + 架构门禁，JaCoCo 门槛 5%。

**目标路线**：

| 阶段 | 时间 | 行覆盖率 | 关键动作 |
|------|------|---------|---------|
| Phase 1 | 2026-Q4 | 15% | 补充核心 Service 层单元测试 |
| Phase 2 | 2027-Q1 | 25% | Repository 集成测试（Testcontainers） |
| Phase 3 | 2027-Q2 | 40% | 全模块 Service + 边界场景覆盖 |

**优先补齐**（高风险核心逻辑）：
- workflow 流程状态机流转（`FlowInstanceMergeServiceImpl`）
- agent 执行器并发安全（`ReActAgentExecutor`）
- cronjob 任务调度幂等性
- message 多渠道发送降级

### 3.2 【P1】集成测试基线建设

**现状**：`TestcontainersBase` 已提供 PG + Redis 容器，但集成测试（`*IT.java`）几乎空白。

**建议**：
1. 各模块编写 `*RepositoryIT.java` 验证 Mapper SQL 正确性
2. 编写 `*ControllerIT.java` 验证完整请求链路（非 MockMvc 切片）
3. CI 中使用 Surefire 跑单元 + Failsafe 跑集成，分开报告

### 3.3 【P2】前端测试覆盖率提升

**现状**：Vitest 101 test case 全通过，但覆盖率门槛仅 5%。

**建议**：
- Phase 1 → 15%：补充 composables / hooks 单测
- Phase 2 → 30%：补充 views 层关键业务组件测试
- Phase 3 → 40%：接入 Vitest Workspace 多应用统一覆盖率报告

---

## 四、前后端能力贯通维度

### 4.1 【P1】gen-contract.py 解析器增强

**现状**：Java 内部类/容器类型解析存在边界情况（如 `HttpServletResponse` 仅生成占位 interface）。

**改进点**：
1. 增强对 `Page<T>`、`List<T>`、`Map<K,V>` 泛型的完整解析
2. 增加 `@JsonIgnore`、`@JsonProperty` 注解识别
3. 补充 `@Valid` 注解 → 前端表单校验规则自动生成

### 4.2 【P1】网关路由规则文档化

**现状**：前端调用路径 `/dict/type/page` 与后端 Controller 路径一致，但 Nacos 配置中有路由重写规则未在代码中显式记录。

**建议**：
1. 在 `docs/nacos-config/` 目录下补充网关路由映射表
2. CI 中新增"路由一致性检查"——比对网关配置与 Controller 注解

### 4.3 【P2】WebSocket 实时推送替代轮询

**现状**：AsyncTask 状态获取走 HTTP 轮询（`AsyncTaskController`）。

**建议**：
- 接入 `ydsz-common-socket` 已有 WebSocket 通道
- 长任务（Agent 执行/批量导入/流程导出）使用 STOMP 推送进度
- 前端 `useAsyncTask` 组合式函数升级为 WebSocket 订阅模式

---

## 五、架构优化维度

### 5.1 【P0】虚拟线程迁移收尾

**现状**：2 处遗留使用 `InternalExecutorFactory.newFixedThreadPool()`：

- `FlowAiAgentNodeExecutor.java:105`
- `MessageServiceImpl.java:137`

**修复**：统一改为 `ExecutorUtils.newVirtualThreadExecutor()`。

### 5.2 【P1】审计日志下沉到 Service 层

**现状**：`@Audit` 仅覆盖 Controller 层，server/infra 核心 Service 漏审。

**建议补充**：
- `FlowInstanceMergeServiceImpl` 流程合并操作
- `MessageServiceImpl` 消息发送核心路径
- cronjob 实际执行逻辑（非仅触发入口）

### 5.3 【P1】统一导入框架

**现状**：system/nextwiki/generator 各自实现导入逻辑（校验、回滚重复编码）。

**建议**：
1. 在 `ydsz-common-excel` 中增加 `ImportFramework`（参考导出 `ExcelFacade` 模式）
2. 提供通用校验接口 `ImportValidator<T>`
3. 支持部分成功 + 失败行号精确报告

### 5.4 【P2】统一历史版本框架

**现状**：nextwiki/literule/generator/userinfo 各自实现版本对比。

**建议**：在 `ydsz-common` 中提供 `VersionHistoryService<T>` 基础抽象。

---

## 六、功能增强维度

### 6.1 【P0】大文件分片上传

**现状**：`IFileStorageProvider` 仅支持单次完整上传，超 100MB 失败需重传。

**方案**：
1. 扩展接口：`initMultipartUpload` → `uploadPart` → `completeMultipartUpload`
2. 前端使用 `File.slice()` 分片 + 并发上传
3. MD5 秒传已有（`FileDedupService`），补充断点续传

### 6.2 【P1】全局搜索统一收拢

**现状**：workflow/agent 自建搜索未使用 `common-search` 的 `UnifiedSearchService`。

**建议**：统一接入，获得熔断/限流/缓存能力。

### 6.3 【P1】数据归档自动化

**现状**：仅 nextwiki `ColdDataArchivalService`（且标注 TODO），其他模块无 TTL 清理。

**建议**：
- cronjob Job 历史：保留 90 天，超出自动归档
- 消息日志：保留 30 天，按月分区自动 DETACH
- 审计日志：保留 180 天

### 6.4 【P2】变更历史统一埋点

所有写操作应自动记录变更快照（参考 `Outbox` 模式），支持"谁在什么时间改了什么"。

---

## 七、性能优化维度

### 7.1 【P0】搜索 SQL 优化

**问题**：`SearchIndexMapper.xml` 左模糊无法走索引。

**修复**：
1. 短期：加 PG `pg_trgm` 扩展 + GIN 索引（不需要 ES 也能模糊查）
2. 长期：统一接入 common-search 全文检索

```sql
CREATE EXTENSION pg_trgm;
CREATE INDEX idx_search_content_trgm ON ydsz_comm_search_index 
  USING GIN (content gin_trgm_ops);
```

### 7.2 【P1】Redis 租户前缀全量检查

**风险**：agent 模块 `RedisAgentStateStore`/`RedisDagCheckpointStore` 直接使用 `RedisTemplate` 而非 `StringOps`。

**动作**：
1. 梳理所有 `RedisTemplate` 直连点
2. 迁移至 `StringOps`/`HashOps`（自动带租户前缀）

### 7.3 【P2】批量操作并发优化

workflow 批量通过/驳回/转办当前串行处理，大批量（100+）时响应慢。

**建议**：使用 `ExecutorUtils.newVirtualThreadExecutor()` 并发处理单批操作（单操作独立事务）。

---

## 八、用户体验维度

### 8.1 【P0】i18n 生成脚本合并

**现状**：`data/scripts/gen_i18n.py` 与 `t-i18n/generate-i18n.cjs` 双管道导致 zh_TW 翻译覆盖率仅 46%。

**方案**：合并为单一入口 `pnpm gen:i18n`，统一调用 Google Translate API + 本地词表覆盖。

### 8.2 【P1】无障碍（a11y）深度扫描

**现状**：仅有 Button/Input/Form 组件存在性断言，未真正验证属性。

**动作**：
1. 接入 `@axe-core/playwright` 到 CI
2. 覆盖 List/Form/Dashboard 三种模板页面
3. 设置 WCAG 2.1 AA 门禁（严重违规阻塞构建）

### 8.3 【P1】错误码建议动作结构化

**现状**：用户建议动作耦合在前端 i18n，后端 `retryable` 元信息已有，但缺少 `actionHint`。

**方案**：
1. `ExceptionCode` 接口增加 `actionHintKey` 字段
2. 后端在 i18n properties 中预填建议动作
3. 前端 `resolveErrorMessage()` 自动展示建议按钮

### 8.4 【P2】表单草稿能力默认开启

**现状**：`useFormDraft` 已存在，但 `system/user-edit` 等关键表单需手动包裹。

**建议**：在 `YdForm` 组件层集成草稿开关 prop，默认开启。

### 8.5 【P2】通知组件无障碍修复

`ElNotification`/`showToast` 缺少 `role="alert"` / `aria-live="polite"` 包裹，屏幕阅读器无法捕捉即时消息。

---

## 九、优先级排序总表

| 优先级 | 编号 | 改进项 | 维度 | 预估工时 |
|--------|------|--------|------|---------|
| P0 | DB-01 | 布尔字段 SMALLINT 迁移 | 数据库 | 3d |
| P0 | ARCH-01 | 虚拟线程 2 处遗留迁移 | 架构 | 0.5d |
| P0 | TEST-01 | 后端覆盖率 5%→15% | 测试 | 5d |
| P0 | FE-01 | i18n 单管道合并 | 体验 | 2d |
| P0 | FUNC-01 | 大文件分片上传 | 功能 | 3d |
| P0 | PERF-01 | 搜索左模糊 → pg_trgm GIN | 性能 | 1d |
| P1 | DB-02 | 核心 FK 约束补充 | 数据库 | 2d |
| P1 | TEST-02 | 集成测试基线建设 | 测试 | 3d |
| P1 | ARCH-02 | 审计日志下沉 Service 层 | 架构 | 2d |
| P1 | FUNC-02 | 全局搜索统一收拢 | 功能 | 3d |
| P1 | FUNC-03 | 数据归档 TTL 策略 | 功能 | 2d |
| P1 | UX-01 | a11y axe-core CI 集成 | 体验 | 2d |
| P1 | UX-02 | 错误码 actionHint 结构化 | 体验 | 1d |
| P1 | PERF-02 | Redis 租户前缀全量检查 | 性能 | 1d |
| P2 | DB-03 | 大表按月分区扩展 | 数据库 | 2d |
| P2 | TEST-03 | 前端覆盖率 5%→15% | 测试 | 3d |
| P2 | FE-02 | WebSocket 替代轮询 | 贯通 | 2d |
| P2 | PERF-03 | 批量操作并发优化 | 性能 | 1d |
| P2 | UX-03 | 表单草稿默认开启 | 体验 | 1d |
| P2 | UX-04 | 通知组件 a11y 修复 | 体验 | 0.5d |

---

## 十、对标差距总结

| 对标项 | 行业标杆 | 本项目现状 | 差距 |
|--------|---------|-----------|------|
| 后端行覆盖率 | 60%+（美团/阿里标准） | 5% | -55pp |
| 前端行覆盖率 | 40%+（Google标准） | 5% | -35pp |
| 布尔字段类型 | TINYINT(1)/SMALLINT 统一 | 40+ 处 BOOLEAN 违规 | 需整改 |
| 全文检索 | ES  standardization | 自建 search + 左模糊 | 可升级 |
| 分片上传 | S3 Multipart | 单次完整上传 | 缺失 |
| a11y 合规 | WCAG 2.1 AA | 静态约定测试 | 缺 CI 门禁 |
| i18n 覆盖 | 中英双文 100% | zh_TW 46% | 需补全 |
| 审计覆盖 | 全写操作覆盖 | 仅 Controller 层 | 需下沉 |
| 虚拟线程 | 全量迁移 | 2 处遗留 | 近完成 |

---

*报告由 CatPaw 基于代码扫描与静态分析自动生成，建议结合实际业务排期执行。*
