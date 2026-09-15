# ADR-009：公共能力重复实现收敛决策（重建自丢失的 ADR-2026-09-12）

**状态：** 已接受
**日期：** 2026-09-14（原决议 2026-09-12，本文档为重建）
**决策者：** ydsz-team
**重建原因：** 原文档 `docs/ADR-2026-09-12_公共能力重复实现收敛决策.md` 经 git 全历史检索确认从未入库且已丢失；4 处源码 Javadoc 引用该路径悬空。本文档依据源码内 Javadoc 与 2026-09-14 深度检查报告反推重建。

---

## 背景

2026-09-12 复用深度检查发现若干"业务模块与 common 平行实现"双轨问题，会议形成 ADR-1/ADR-3/ADR-5 三项决议并写入源码 Javadoc，但决议全文未入库。本文档重建三项决议全文，作为后续评审与守护脚本（`scripts/check-common-reuse.py`）的判定依据。

## 决议全文

### ADR-1：短信签名能力收敛（common-notify vs ydsz-message）

**背景：** common-notify 与 message 存在四对同名能力类（SmsProvider / AliyunSmsProvider / TemplateEngine / TemplateVariableValidator），能力重叠。

**决议：**
1. **message 侧为短信发送管线的权威实现**（通道编排、重试、DLQ 由 message 统一承担）；
2. **阿里云签名算法逻辑待下沉 common-notify**（`AliyunSmsSigner`），下沉后 message 改为组合调用，不再自持签名实现；
3. 收敛完成前，`AliyunSmsProvider` 的 Javadoc 须引用本 ADR 而非丢失的旧文档。

**执行状态（2026-09-14）：** 未落地，列入第三批路线（SmsSigner 下沉）。

### ADR-3：线程池管理定界（cronjob 裸 ThreadPoolExecutor vs common-thread）

**背景：** cronjob `ThreadPoolHotUpdateListener` 直接管理 JDK 裸 `ThreadPoolExecutor`（支持运行时参数热更新），与 common-thread 的执行器封装并存。

**决议：**
1. **cronjob 场景保留自有实现**——定时任务需要"运行时核心/最大线程数 + 队列容量热更新 + 变更监听"，common-thread 静态封装不覆盖该场景，属"场景决定的必要实现"；
2. **决议 2：`SystemMetricsCollector` 应复用 common-sentry 的指标采集能力、删除自有 JMX 实现**——决议已定，执行列入后续整改；
3. common-thread 与 cronjob 自有执行器并存为合法状态，新增线程池场景优先 common-thread。

**执行状态（2026-09-14）：** 决议 1 已生效；决议 2 未执行（TODO）。

### ADR-4：网关层与 common 能力定界（gateway 过滤器）

**背景：** gateway 的 `W3CTraceContextFilter` 与 `SqlInjectionFilter` 与 common-util / common-jdbc 的对应能力疑似重叠。

**决议：**
1. **W3C Trace 能力下沉计划**：W3C Trace Context（`traceparent`）解析/传播是全平台需求，common-util 已有 `TracerUtils#parseTraceparent`；网关过滤器承载的协议解析逻辑后续应下沉至 common-util TracerUtils（计划项，未执行）；
2. **SQL 注入防御为纵深分层，非重复建设**：common-jdbc `SqlFirewallInnerInterceptor` 在 JDBC 层深度防护，网关过滤器在入口层拦截，二者互补；网关为响应式栈无法复用 Servlet 端实现（终局定界，守护脚本命中时豁免）。

**执行状态（2026-09-14）：** 决议 2 已生效；决议 1 下沉计划未执行（TODO）。

### ADR-5：RAG 分块与文档预处理定界（ydsz-agent vs common-docs）

**背景：** agent `TextChunker` 与 common-docs 的文档解析/分块能力疑似重叠。

**决议：**
1. **不合并。** common-docs 面向"文档格式解析与导出"，agent `TextChunker` 面向"RAG 检索优化的语义分块"（滑动窗口、重叠率、嵌入长度约束），二者目标函数不同；
2. agent 侧 `DocumentFormat`（输出格式枚举）与 common-docs 解析格式枚举为同名不同物，逐步重命名消歧；
3. 该定界为终局决议，后续守护脚本 E1 命中此两类时按本决议豁免。

**执行状态（2026-09-14）：** 决议生效，脚本豁免清单可引用本 ADR。

## 附带修正

以下 6 处源码 Javadoc 引用了丢失的旧文档路径 `docs/ADR-2026-09-12_公共能力重复实现收敛决策.md`，已统一修正为引用本 ADR：

1. `ydsz-message/.../channel/sms/AliyunSmsProvider.java` → ADR-1
2. `ydsz-agent/.../domain/rag/TextChunker.java` → ADR-5
3. `ydsz-cronjob/.../core/config/ThreadPoolHotUpdateListener.java` → ADR-3
4. `ydsz-cronjob/.../core/metrics/SystemMetricsCollector.java` → ADR-3
5. `ydsz-gateway/.../filter/W3CTraceContextFilter.java` → ADR-4
6. `ydsz-gateway/.../filter/SqlInjectionFilter.java` → ADR-4

## 决议全文（2026-09-15 增补）

> 来源：2026-09-15 公共模块复用深度检查（`docs/云顶公共模块复用深度检查与优化建议_2026-09-15.md`）。
> 增补三项决议，编号续 ADR-1～ADR-5。

### ADR-6：cronjob Outbox 与 common-event OutboxService 收敛（阶段化执行，非豁免）

**背景：** ydsz-cronjob 自建完整事务性 Outbox 链路——`OutboxEvent`（表 `ydsz_job_outbox`，`Long id`）、
`OutboxEventRepository`（domain）+ `OutboxEventRepositoryImpl`（infra）、`OutboxPublisher`（179 行：扫描 +
至少一次 + 指数退避 1s/5s/25s + DEAD）、`OutboxScanTask`、进程内订阅者（`WebhookOutboxSubscriber` 等）。
common-event 已提供同能力超集：`OutboxMessage` / `OutboxStatus`（PENDING/PROCESSING/SENT/DEAD_LETTER，
含 claim 抢单与 reclaim 回收）/ `OutboxRepository`（可配置表名）/ `OutboxService`（含事务 afterCommit 发布）/
`OutboxProcessor` / `OutboxAdminService` / `OutboxHealthIndicator`。

**决议：**
1. **定性为平台内重复，不构成豁免**：common-event 在投递语义（claim / reclaim / 去重键 / trace 透传 / 管理端 /
健康检查）上均为超集，cronjob 侧不存在不可替代的独有职责；`docs/云顶公共模块复用深度检查与优化建议_2026-09-15.md`
P1 项成立。
2. **收敛目标为 common-event**，字段映射如下（迁移期以映射表为准）：

   | cronjob 字段 | common-event 字段 | 备注 |
   |---|---|---|
   | `id`（Long，MP ASSIGN_ID） | `id`（String，雪花字符串） | 主键类型变更，需 DDL + 数据迁移 |
   | `eventKey` | `deduplicationId` | 幂等键语义一致 |
   | `eventType` | `eventType` | 直接对应 |
   | `topic` | —（由 `eventType`/`aggregateType` 路由，或写入 payload 元数据） | 收敛时的映射设计点 |
   | `payload` | `payload` | 直接对应 |
   | `status` PENDING/PUBLISHED/DEAD | PENDING/SENT/DEAD_LETTER（+PROCESSING） | 状态机语义对齐 |
   | `retryCount` | `retryCount` + `maxRetries` | common 侧显式配置最大重试 |
   | `nextRetryTime`（LocalDateTime） | `nextRetryAt`（Instant） | 时区/类型对齐 |
   | `createTime` / `updateTime` | `createdAt` / `updatedAt` | 直接对应 |
   | — | `sentAt` / `errorMessage` / `tenantId` / `traceId` | common 侧增量字段 |

3. **禁止新增第二条 Outbox 链**：其余业务模块需事务性事件投递时，一律使用 common-event `OutboxService`。
4. **阶段化执行（因涉及 DDL 与数据迁移，禁止一次性大爆炸改造）：**
   - 阶段一：DDL 变更（`ydsz_job_outbox` 对齐 common-event 列集）+ 数据迁移脚本，保留双读兼容；
   - 阶段二：cronjob 侧 `OutboxEvent` / `OutboxEventRepository` / `OutboxPublisher` / `OutboxScanTask` 替换为
     common-event `OutboxService` + `OutboxProcessor`，订阅者由 `Consumer<OutboxEventVO>` 改为 `@EventListener`；
   - 阶段三：删除 cronjob 自建类与表，`check-common-reuse.py` E1 命中归零。
   - **验收：** 阶段三完成后，全仓 `grep -rn "OutboxPublisher" ydsz-cronjob` 为空，且 Outbox 事件的
     至少一次投递 + 重试 + DEAD 语义由 common-event 承担。

**执行状态（2026-09-15）：** 决议 1～3 生效；阶段一～三未执行，列为 P1 技术债（需 DDL 迁移窗口）。

### ADR-7：消息模板引擎定界（message 富语法引擎 vs common-notify 模板注册表）

**背景：** 两侧存在同名 `TemplateEngine` 与 `TemplateVariableValidator`。message 侧为**内容渲染语法引擎**
（`${var}` / `${a.b.c}` 嵌套取值、`{{#if}}`/`{{else}}` 条件、`{{#each}}` 循环、`requiredKeys` 必填校验，
按模板内容渲染）；common-notify 侧为**模板注册表 + 渲染入口**（按 `templateId` 渲染、SpEL 渲染、
热加载 `HtmlTemplateRegistry`/`FileTemplateHotLoader`、`NotifyTemplate` 元数据）。

**决议：**
1. **不合并**：二者为"模板内容渲染引擎"与"模板注册/管理入口"的能力分层，接口语义（按内容渲染 vs 按 ID 渲染）
   与消费方不同；
2. **变量校验职责按数据模型划分**：message 校验器由模板 `variableDefs`（JSON）驱动，common-notify 校验器绑定
   `NotifyTemplate` 定义，数据模型不同，不强行统一；
3. **消歧动作（TODO）**：message 侧重命名为 `MessageTemplateRenderer` / `MessageTemplateValidator`，
   消除 E1 同名歧义（保留实现，仅改名）；
4. **新增约束**：非消息域的模板渲染需求一律优先 common-notify；若 message 富语法引擎被第二个模块复用，
   须上提 common-notify 作为高级实现。

**执行状态（2026-09-15）：** 决议 1～2、4 生效；决议 3 改名为 TODO。

### ADR-8：领域事件接口定界（cronjob domain ES 事件 vs common-event 集成事件）

**背景：** ydsz-cronjob 同时存在两套事件类型：`ydsz-cronjob-domain` 自建 `DomainEvent` 接口
（Event Sourcing 元数据：`eventId` / `aggregateId` / `aggregateType` / `eventType` / `occurredAt`），
以及 server 层直接使用 common-event `api.DomainEvent` + `publish.DomainEventPublisher`
（`AlertDispatcher` / `DefaultTaskDispatcher` / `JobServiceImpl`）。

**决议：**
1. **定界为两类事件语义**：common-event `DomainEvent`（继承 Spring `ApplicationEvent`）为**模块间集成事件契约**；
   cronjob 自建接口为**聚合内 Event Sourcing 事件**（携带聚合根与事件溯源元数据），二者语义不同层；
2. **Javadoc 显式引用本 ADR**，消除"为何 domain 层不直接用 common 接口"的疑问；
3. **长期建议（TODO）**：common-event 增加 ES 元数据扩展接口（或事件基类支持聚合元数据），届时 cronjob
   领域事件接口改为继承该扩展，实现"一套事件基座、两种语义"。

**执行状态（2026-09-15）：** 决议 1～2 生效；决议 3 为长期建议。

## 2026-09-15 复查与整改记录

| 项 | 内容 | 状态 |
|---|---|---|
| P0 | 4 处 UUID 主键违规（literule `RuleAuditLogService`、system `ConfigApprovalServiceImpl`、workflow `FlowCategoryServiceImpl` / `FlowDefinitionDeployManager`）改为 common-util `IdGenerator.nextIdStr()` | 已修复并编译通过 |
| 根因 | common-audit 审计链无 ID 兜底：`AuditLog#ensureId()` + `DefaultAuditStorage` / `JdbcAuditStorage` 落库边界调用（JdbcAuditStorage 为原生 JDBC 插入，主键为空即入库失败） | 已补齐 |
| E1 | userinfo `AppOpenApiConfiguration` 改为继承 common-base `BaseOpenApiConfiguration`，移除自建 OpenAPI Bean（文档开关统一为 `ydsz.doc.enabled`） | 已收敛 |
| W1 | gateway `ydsz-common-redis` / `ydsz-common-thread` 经核验为自动装配型依赖（运行时 Bean，`nacosRouteListenerExecutor` 按 Bean 名获取，`bootstrap.yml:75` 声明线程池），非僵尸依赖；`check-common-reuse.py` W1 规则已实现"自动装配豁免"标注 | 误报已消除 |
| 工具 | 脚本矩阵 `webhook→web` 归属修正；W1 自动装配豁免标注 | 已修复 |

## 关联

* 守护规则：`scripts/check-common-reuse.py`（2026-09-14 重建，第四次丢失后入库）
* 规范章节：《云顶编码规范》§33.7、§22
* ADR-007（Bean 拷贝收敛）、ADR-008（ID 生成边界）
* 检查报告：`docs/云顶公共模块复用深度检查与优化建议_2026-09-15.md`
