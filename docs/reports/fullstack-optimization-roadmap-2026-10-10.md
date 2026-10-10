# 全栈优化后续路线图 — 基于代码最新状态

> **版本**: v1.0
> **日期**: 2026-10-10
> **前置报告**: `fullstack-optimization-analysis-2026-10-09.md`
> **对标**: 阿里 Java 开发手册、Google Engineering Practices、Vue RFC、Web Vitals
> **数据样本**: ydzs-cloud 10 业务模块 + 30 common 子模块，ydsz-micro monorepo 10 子应用

---

## 〇、前置报告要点回顾

`fullstack-optimization-analysis-2026-10-09.md` 已覆盖 27 项 P0/P1/P2 建议，覆盖面完整。本报告在前置报告基础上，补充基于代码最新运行态发现的新增优化点**不重复已有建议**。前置报告的优先级总表仍然有效，建议同步执行。

---

## 一、数据库表设计 — 补充维度

### 1.1 JSONB 字段缺少约束校验（P1）

项目中大量使用 PostgreSQL `JSONB` 类型承载扩展数据，例如：

| 表 | JSONB 列 | 问题 |
|----|---------|------|
| `ydsz_flow_definition` | `ext`, `canary_rollout_log` | 无 CHECK 约束，写入不校验结构 |
| `ydsz_agt_*` 多表 | `config_json`, `params_json`, `output_json` | JSON 结构完全靠应用层约定 |
| `ydsz_msg_template` | `variable_map` | 缺少 Schema 校验 |

阿里规范要求「JSON 字段必须明确定义结构契约」。PostgreSQL 16+ 支持 `jsonb_schema` 校验（pg_jsonschema 扩展），应用层可用 JSON Schema 防御。

**建议**:
1. 在 RepositoryImpl 写入 JSONB 字段前，通过 `JsonUtils.validateSchema(json, schemaPath)` 进行轻量校验（ydsz-common-json 中新增此工具方法）
2. 为核心 JSONB 字段（如 `ydsz_flow_definition.ext`）编写 database-level `CHECK (jsonb_typeof(ext) = 'object')` 约束作为最后防线
3. 建表规范新增「JSONB 字段必须说明预期结构」条款到 CLAUDE.md

### 1.2 缺少数据库读写分离落地（P2）

已配置 `baomidou.dynamic-datasource-spring-boot3-starter:4.3.1` 和 `datasource.dynamic` 配置项，但实际运行中未见到从库配置。当前所有读写均走单一大库。

**建议**: 当服务 QPS 超过 500 时，为主库添加只读从库，通过 Mybatis-Plus 的 `@DS("slave")` 注解或 AOP 切面将报表/统计/导出类查询路由到从库。

### 1.3 缺少冷热数据归档策略（P1）

项目有分区表设计（flow_audit_log、comm_search_index_partitioned），但缺少通用的归档框架。`ydsz_comm_audit_log` 预计日增 10 万行，若无归档运行一年将达 3.6 亿行。

**建议**: 在 `ydsz-common-jdbc` 中新增 `@ArchivedAfter(days=180)` 注解 + 定时归档任务（ydsz-cronjob），自动将过期数据迁移到低成本存储（如 OSS / 从库归档表）。

---

## 二、接口单元测试 — 补充维度

### 2.1 缺失契约测试（P1）

当前仅有白盒单元测试和黑盒 smoke test。缺少 Spring Cloud Contract / Pact 风格的消费者驱动契约测试(CDC)。对于跨服务调用场景（如 userinfo 通过 Feign 调用 system），一旦服务提供方接口签名变更，消费方可能在运行时才暴露问题。

**建议**:
- 对高频 Feign 调用（userinfo → system 查字典、workflow → userinfo 查角色、agent → system 查参数）引入 REST Assured（已在 pom 中声明）编写契约测试
- 利用 `BaseControllerMockTest` 基类模拟上游服务的 HTTP 响应

### 2.2 缺少性能回归测试（P2）

项目没有自动化的性能基线保护。即使代码逻辑正确，一次不恰当的 SQL 改写也可能导致响应时间从 50ms 飙至 5s。

**建议**: 引入 JMH 微基准测试，对核心路径（如 DictVersionService 缓存命中、SemanticLlmCache L2 读取）设定 CI 耗时基线，PR 中回归超过 20% 时自动警告。

### 2.3 测试覆盖率报告未聚合（P1）

当前各模块独立运行 JaCoCo，但 12 个子模块 + 10 个业务模块共 22 个模块的 coverage 报告分散在各 `target/site/jacoco/` 目录。没有聚合的全局覆盖率视图。

**建议**: 在根 pom 中添加 JaCoCo `report-aggregate` 配置，并在 CI pipeline 中运行 `mvn jacoco:report-aggregate` 产出全局报告，上传到 SonarQube / 自建 coverage dashboard。

---

## 三、前后端能力贯通 — 补充维度

### 3.1 缺失 WebSocket/实时推送契约（P2）

后端已建设 `ydsz-common-socket`（Stomp 封装）和 WebSocket 端点，前端 micro-kernel 也支持 Socket 通信。但以下场景缺少实时推送设计：
- 工作流任务到达通知（目前靠轮询或页面刷新）
- Agent 长任务执行进度（目前前端无法感知后端执行到哪一步）
- 定时任务执行结果实时展示

**建议**: 为工作流模块新增 STOMP 主题 `/topic/workflow/tasks/{userId}`，后端在 `FlowInstanceServiceImpl` 状态变更时推送。为 Agent 执行器新增 SSE 端点 `/agent/stream/{taskId}` 流式返回执行步骤。

### 3.2 缺少统一的错误码前端 SDK（P1）

后端 `ExceptionCodeEnum` 已定义 464 个错误码常量，但前端仅有基础 `string` 比对，无法按需做差异化处理（如 `PASSWORD_EXPIRED` 需跳转修改密码页，`TENANT_EXPIRED` 需跳转续费页），导致所有错误统一 Toast 提示。

**建议**: 前端 `@ydsz/errors` 中建立「错误码 → 处理策略」映射表，常用 B 段码（如账户/权限/租户相关）配置专属处理逻辑，增强终端用户引导能力。

### 3.3 多租户前缀与前端路由对齐（P2）

后端通过 `X-Tenant-Id` Header + Redis key 前缀实现租户隔离。前端路由（如 `/:tenantPrefix/system/dicts`）可考虑将 tenant 信息体现在 URL 中，便于收藏/分享直接定位到目标租户。

---

## 四、前后端架构优化 — 补充维度

### 4.1 后端缺少 GraphQL/BFF 按需聚合（P2）

当前前端获取聚合数据（如首页仪表盘需要同时拉取用户信息/通知数/待办数）需多次 HTTP 请求。后端网关仅做路由聚合。

**建议**: 考虑为 Dashboard 类场景新增 `/dashboard/init` BFF 端点（system 模块已有 `/system/init` 作为雏形，可扩展），将 3-5 次请求合并为 1 次，减少前端 waterfall 延迟。

### 4.2 前端缺少请求去重/竞态处理（P2）

`useComposableFetch` 没有内置竞态保护——若用户快速连续点击分页按钮，可能先发出的后响应覆盖后发出的先响应。

**建议**: 在 `@ydsz/request` 中增加 `AbortController` 自动取消上一未完成请求，或使用 `swr` 的 `dedupingInterval` 实现请求去重。

### 4.3 缺少全链路灰度发布体系（P2）

后端已在 `FlowDefinition` 嵌入 `canary_rollout_log` 字段和标准 `canary_percent/canary_status` 元数据，网关具备基础灰度能力。但全链路灰度涉及前端→网关→服务→数据库的完整标记透传，当前不完整。

**建议**: 完善 `X-Canary-Header` 全链路透传机制——网关根据用户 ID hash 打标，服务层读取灰度标记决定是否走新逻辑，数据层通过 MyBatis-Plus 查询条件隔离灰度数据。前端提供「灰度用户白名单」管理界面。

---

## 五、前后端功能增强 — 补充维度

### 5.1 缺失数据导入/导出全模块统一（P1）

虽然 `ExcelFacade` 已实现封装，SOP-3 也定义了 Excel 导出规范。但各模块实际遵循度参差不齐——部分 Controller 仍自行创建 `HttpServletResponse` 输出流。

**建议**: 在 web 层统一拦截 Excel 导出请求，通过 AOP 切面自动设置 `Content-Disposition`、`Content-Type`、文件名编码，Controller 只需返回 `List<T>` 即可。

### 5.2 缺失数据变更轨迹对比（P1）

`@Audit` AOP 已记录审计日志，但前端没有「数据变更对比视图」。用户无法直观看到某条记录在两次修改之间具体改了哪些字段。

**建议**: 后端 RepositoryImpl `update` 操作时自动记录 `before/after` JSON 快照（ydsz-common-audit 已有 `diff_before_snapshot/diff_after_snapshot` 字段），前端审计日志页面增加「查看差异」按钮，类似 GitHub PR Diff 的高亮对比体验。

### 5.3 缺少批量操作事务编排（P2）

批量导入场景（如 SOP-6 文件上传后解析 Excel 批量创建业务对象）沿用逐行单条 INSERT，未使用 JDBC `rewriteBatchedStatements` 或 MyBatis `executeBatch` 优化。

**建议**: 在 `ydsz-common-jdbc` 中新增 `BatchInserter<T>` 工具类，统一提供 batch size = 500 的批量写入，配合 `@Transactional` 确保原子性。

---

## 六、前后端性能提升 — 补充维度

### 6.1 缺失数据库连接池监控看板（P1）

HikariCP 已配置 `minimum-idle=5, maximum-pool-size=20`，但没有暴露连接池实时状态。慢连接池耗尽（active=20 + queue 满）时会导致用户请求排队，但运维无法提前感知。

**建议**: 通过 Prometheus `hikaricp_*` Micrometer 指标 + Grafana 仪表盘监控连接池使用率、等待时间、泄漏检测，配置 80% 使用率自动告警。

### 6.2 缺少接口慢响应 Top-N 排行（P1）

`ydsz-common-sentry` 具备 SLA 框架基础能力，但未接入具体 Controller 响应时间 Top-N 榜单。开发者不知道哪些接口最需要优化。

**建议**: 在网关层增加请求耗时直方图（`http_server_requests_seconds_bucket`），Grafana 展示 P50/P95/P99 趋势。每天自动推送「昨日 Top10 慢接口」到飞书/钉钉群。

### 6.3 前端缺少资源预加载策略（P2）

vendor 外置策略虽正确，但子应用切换时仍需网络请求加载 JS chunk。当前未见 prefetch/preload 声明。

**建议**: 对高概率访问的子应用（用户访问 system 后有 80% 概率也访问 userinfo），通过 `<link rel="prefetch">` Tailwind 预测加载。

---

## 七、前后端用户体验 — 补充维度

### 7.1 缺少键盘快捷键体系（P2）

前端已有 `useCommandPalette` 雏形，但未建立全局快捷键映射。对标竞品如 Linear、Notion 的全局键盘操作（Cmd+K 命令面板、Cmd+S 保存、Esc 关闭弹窗），可以大幅提升 Power User 效率。

**建议**: 在 `@ydsz-core/ui` 中新增 `useKeyboardShortcuts` composable，提供统一的快捷键注册、冲突检测、上下文感知激活（如弹窗打开时 Esc 优先传递给弹窗处理）。

### 7.2 缺少表单草稿自动保存（P2）

B 端长表单（如 Agent 配置表单、流程定义画布、决策表编辑）若因误刷新或浏览器崩溃导致填写内容丢失，用户体验极差。

**建议**: 在前端表单组件中集成 `useAutoSaveDraft` composable，利用 `localStorage` + debounce 自动保存草稿，重新进入时提示恢复。后端无需改造。

### 7.3 缺少 loading 态骨架屏一致性（P2）

前端已有 `YdSkeleton` 提供 text/circular/rectangular/rounded 四种形态，但各子应用使用未形成约定——部分页面 loading 时仍显示空白或转圈，体验不统一。

**建议**: 制定骨架屏使用规范：列表页用 rectangular skeleton（模拟行数据）、卡片页用 rounded skeleton、全文加载用 shimmer overlay。通过 `useTableData` composable 自动绑定 loading 态到骨架屏。

---

## 八、优先级汇总（新增项）

| 优先级 | 编号 | 维度 | 新增建议 | 工作量 |
|--------|------|------|---------|--------|
| **P1** | DB-07 | 数据库 | JSONB 字段写入前 Schema 校验 | 2 人日 |
| **P1** | DB-08 | 数据库 | 冷热数据归档框架 | 5 人日 |
| **P1** | TEST-04 | 单元测试 | Feign 契约测试（REST Assured） | 3 人日 |
| **1** | TEST-05 | 单元测试 | JaCoCo 聚合报告 + SonarQube | 1 人日 |
| **P1** | CONN-07 | 贯通 | 错误码前端差异化处理 SDK | 2 人日 |
| **P1** | PERF-03 | 性能 | HikariCP 监控告警 | 1 人日 |
| **P1** | PERF-04 | 性能 | 慢接口 Top-N 日报推送 | 2 人日 |
| **P1** | FX-04 | 功能 | Excel 导出 AOP 统一拦截 | 2 人日 |
| **P1** | FX-05 | 功能 | 数据变更 Diff 对比视图 | 3 人日 |
| **P2** | DB-09 | 数据库 | 读写分离从库路由 | 3 人日 |
| **P2** | TEST-06 | 单元测试 | JMH 性能回归基准 | 2 人日 |
| **P2** | CONN-08 | 贯通 | 工作流/Agent 实时推送（STOMP/SSE） | 5 人日 |
| **P2** | ARCH-03 | 架构 | Dashboard BFF 聚合端点 | 3 人日 |
| **P2** | ARCH-04 | 架构 | 请求去重/竞态保护 | 1 人日 |
| **P2** | ARCH-05 | 架构 | 全链路灰度发布体系 | 5 人日 |
| **P2** | FX-06 | 功能 | JDBC Batch 批量写入优化 | 2 人日 |
| **P2** | UX-06 | 体验 | 全局键盘快捷键体系 | 3 人日 |
| **P2** | UX-07 | 体验 | 表单草稿自动保存 | 2 人日 |
| **P2** | UX-08 | 体验 | 骨架屏使用规范统一 | 2 人日 |
| **P2** | PERF-05 | 性能 | 子应用 prefetch 预加载优化 | 1 人日 |

新增合计：约 53 人日
结合前置报告 55 人日，**总计约 108 人日**（可分 3 个迭代 × 2 周完成）

---

## 九、迭代规划建议

| 迭代 | 周期 | 聚焦 | 核心交付 |
|------|------|------|---------|
| Sprint A | Week 1-2 | **质量竖井** | Controller 60% smoke 覆盖 + 状态机测试 + 安全测试 + JaCoCo 聚合 + 慢接口 Top-N |
| Sprint B | Week 3-4 | **性能底座** | 高增长表分区 + 归档框架 + HikariCP 监控 + 慢接口优化 + JSONB 校验 |
| Sprint C | Week 5-6 | **体验贯通** | apiClient 迁移 + BFF 聚合 + 实时推送 + Diff 对比 + UX 快捷键 + 骨架屏统一 |

---

## 十、总结

ydsz-cloud + ydzs-micro 组合在 DDD 分层、编码红线治理、公共能力收敛、可观测性四个维度已对标阿里 P7+/美团内部标准。**最大短板是测试覆盖率（<2% vs 大厂 60%+ 标准），这是质量风险的主要来源**。

优先解决测试覆盖瓶颈后，再推进性能优化和体验增强，是风险收益比最高的路径。建议在 Sprint A 中集中突破测试基础设施，为后续持续重构和 feature 开发建立安全网。
