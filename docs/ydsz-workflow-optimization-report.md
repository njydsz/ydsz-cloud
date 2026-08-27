# ydsz-workflow 模块优化完善建议报告

> **分析日期**：2026-08-27  
> **分析范围**：ydsz-workflow 全部 7 个子模块（domain/engine/infra/server/web/app/api）  
> **对标竞品**：Flowable 7.x / Camunda 7.x / Activiti 8.x  
> **对标规范**：BPMN 2.0 OMG 标准、阿里云腾讯云工作流产品、美团内部编码规范 v1.0.2

---

## 一、现状总评

ydsz-workflow 是一个**零第三方 BPMN 依赖的轻量级自研工作流引擎**，采用 DDD 分层架构，覆盖流程建模→部署→运行→任务办理→分析优化的全链路。代码量约 15 万行，包含 21 张数据库表、35+ 服务接口、17 个仓储、16 个 Controller。

**核心优势**：
- 完全自主可控，无 Flowable/Camunda 的沉重依赖和许可证风险
- DDD 分层合规，依赖倒置清晰
- 安全防护体系完善（XXE 防护、ReDoS 防护、敏感数据脱敏、幂等/限流/审计）
- 多级缓存 + 分布式锁 + 动态调度的生产级成熟度
- 多数据库支持（MySQL/Oracle/PostgreSQL）

**核心差距**：
- 与 BPMN 2.0 标准兼容性约 60%，事件/消息/补偿等高级语义缺失
- 领域模型贫血，业务规则散落在 Service/VO/枚举中
- 多数据库兼容存在硬伤（PostgreSQL 专属语法、列名乱码）
- AI 能力骨架化，子目录已建但未实现

---

## 二、架构优化建议

### 2.1 领域模型充血化（P0 — 影响可维护性）

**现状问题**：domain 层无领域实体、无值对象、无领域服务。48 个 VO 是纯数据容器，业务规则散落在 `FlowTaskStateMachine`、`FlowNodeVO.getExtMap()` 的 14 个 getter、以及 Service 层各处。

**对标参考**：
- Flowable 的 `ProcessInstance`、`Execution`、`Task` 实体封装了完整的生命周期行为
- Camunda 的 `ProcessInstanceModification` 将业务规则内聚到领域对象

**落地建议**：

| 阶段 | 动作 | 预期收益 |
|------|------|----------|
| 短期 | 将 `FlowInstance` 从 infra 实体提升为领域聚合根，内嵌 `transitTo()`/`reject()`/`rollback()` 等行为 | 消除 Service 层重复的状态校验逻辑 |
| 短期 | 将 `FlowNodeVO` 的 ext JSON 解析抽取为值对象：`SlaConfig`、`ServiceNodeConfig`、`CountersignConfig`、`AssigneeConfig` | 编译期类型安全，消除 14 个弱类型 getter |
| 中期 | 引入 `DomainEventPublisher` 接口，在聚合根状态变更时自动发布事件 | 事件发布从 Service 层解耦 |
| 中期 | 将 `CountersignStrategy`（会签策略）纳入 domain 层作为领域服务 | 业务规则内聚 |

### 2.2 补齐实例级状态机（P0 — 影响一致性）

**现状问题**：仅有 `FlowTaskStateMachine`（任务级），缺少 `FlowInstanceStateMachine`。`FlowInstanceStatus.canTransitTo` 直接在枚举中定义，但没有对应的独立状态机类来封装校验逻辑。

**对标参考**：Camunda 的 `ProcessInstanceState` 枚举定义了完整的合法流转路径，并通过 `ProcessInstanceModificationBuilder` 执行。

**落地建议**：
- 创建 `FlowInstanceStateMachine`，与 `FlowTaskStateMachine` 对称设计
- 定义实例级流转规则：`RUNNING ↔ SUSPENDED`、`RUNNING → COMPLETED/TERMINATED/REJECTED/ERROR`、`COMPLETED → ROLLED_BACK`
- 消除 `FlowInstanceStatus.canTransitTo` 中的重复逻辑，统一由状态机管理

### 2.3 消除多数据库兼容硬伤（P0 — 影响可用性）

**现状问题**：
- `FlowRunTaskMapper.xml` 第 307 行使用 `EXTRACT(EPOCH FROM ...)`（PostgreSQL 专属）
- 第 323-326 行使用 `COUNT(1) FILTER (WHERE ...)`（PostgreSQL 专属）
- `FlowRunTaskMapper.xml` 第 40-41 行列名乱码 `ydsznder_count` / `last_ydsznded_at`
- `FlowDefinitionMapper.xml` 列名 `version` 与 DDL `flow_version` 不一致

**对标参考**：Flowable 通过 `DatabaseSpecificMapper` + `db-specific-mappers.xml` 配置实现多数据库 SQL 差异化。

**落地建议**：
- 立即修复列名乱码和映射不一致（纯 bug，无设计争议）
- 引入 MyBatis-Plus 的 `IDialect` 或自定义 `DatabaseDialect` 抽象，按数据库类型路由到不同 SQL
- 将 PostgreSQL 专属语法改写为 MySQL/Oracle 兼容写法（如 `TIMESTAMPDIFF(HOUR, due_at, NOW())`）
- 在 CI 中增加多数据库集成测试（H2 测 MySQL 路径、Testcontainer 测 PostgreSQL 路径）

### 2.4 领域事件体系完善（P1 — 影响扩展性）

**现状问题**：仅有 7 个领域事件，缺少挂起/恢复/回滚/签收/委派/SLA 触发等关键事件。无 `DomainEventPublisher` 接口，事件发布散落在 Service 层。

**对标参考**：Camunda 支持 50+ 历史事件类型（`HistoryEventTypes`），通过 `EventHandler` 接口订阅。

**落地建议**：

| 缺失事件 | 触发时机 | 消费者场景 |
|----------|----------|------------|
| `FlowInstanceSuspendedEvent` | 实例挂起 | 通知办理人暂停 |
| `FlowInstanceResumedEvent` | 实例恢复 | 恢复待办提醒 |
| `FlowInstanceRolledBackEvent` | 实例回滚 | 清理下游数据 |
| `FlowTaskClaimedEvent` | 任务签收 | 更新办理时效统计 |
| `FlowTaskDelegatedEvent` | 任务委派 | 通知被委派人 |
| `FlowTaskRejectedEvent` | 任务级驳回 | 触发驳回统计 |
| `FlowTaskSlaTriggeredEvent` | SLA 触发 | 执行升级/自动处理 |
| `FlowDefinitionDeployedEvent` | 定义部署 | 清除缓存、通知订阅者 |

- 为事件增加 `correlationId`（关联业务 ID）和 `causationId`（因果链上游事件 ID），支持事件链追踪
- 事件类增加 `version` 字段（`v1`、`v2`...），支持 schema 演进

### 2.5 Repository CQRS 拆分（P2 — 影响读写性能）

**现状问题**：`FlowRunTaskRepository` 32 个方法、`FlowInstanceRepository` 22 个方法，命令/查询混在一起。统计查询（`selectOverviewStats`、`selectApproverEfficiency` 等）返回 `Map<String, Object>`，破坏类型安全。

**对标参考**：阿里云 Serverless Workflow 采用 CQRS 分离读写模型，读模型走 Elasticsearch，写模型走关系数据库。

**落地建议**：
- 将 Repository 拆分为 `FlowRunTaskCommandRepository`（CUD 操作）和 `FlowRunTaskQueryRepository`（查询操作）
- 统计查询方法迁移到 `FlowAnalyticsRepository`，返回强类型 VO
- 高频统计查询（待办数、超期数）走 Redis 缓存或物化视图，避免实时 COUNT

---

## 三、功能增强建议

### 3.1 BPMN 2.0 标准兼容性提升（P1 — 影响互操作性）

**现状问题**：当前支持约 60% 的 BPMN 2.0 元素。`eventBasedGateway` 和 `complexGateway` 降级处理，`terminateEventDefinition`/`linkEventDefinition`/`conditionalEventDefinition` 不支持，消息/信号/补偿事件无运行时。

**对标参考**：

| BPMN 能力 | Flowable | Camunda | ydsz-workflow | 差距 |
|-----------|----------|---------|---------------|------|
| 事件网关 | ✅ 完整 | ✅ 完整 | ⚠️ 降级为条件网关 | 需实现事件等待/竞争机制 |
| 复杂网关 | ✅ 完整 | ✅ 完整 | ⚠️ 降级为包容网关 | 需实现复杂条件表达式 |
| 终止事件 | ✅ | ✅ | ❌ 不支持 | 需实现 |
| 链接事件 | ✅ | ✅ | ❌ 不支持 | 需实现（长流程跳转） |
| 消息/信号 | ✅ 运行时 | ✅ 运行时 | ⚠️ 仅解析标记 | 需实现发布-订阅运行时 |
| 补偿事件 | ✅ 运行时 | ✅ 运行时 | ⚠️ 仅解析标记 | 需实现补偿执行逻辑 |
| 业务规则任务 | ✅ DMN | ✅ DMN | ❌ 不支持 | 可对接 ydsz-literule |
| 发送任务 | ✅ | ✅ | ❌ 不支持 | 需实现 |
| 事务子流程 | ✅ | ✅ | ❌ 不支持 | 需实现 |
| 执行监听器 | ✅ | ✅ | ❌ 不支持 | 需实现 |

**落地建议**（按优先级排序）：

1. **P0 — 终止事件**：实现 `terminateEventDefinition` 运行时，触发时立即结束当前实例并清理所有运行中任务
2. **P1 — 消息/信号运行时**：基于 Redis Pub/Sub 或 RocketMQ 实现跨流程实例的消息传递，支持 `messageEventDefinition` 和 `signalEventDefinition`
3. **P1 — 链接事件**：实现 `linkEventDefinition` 作为长流程的"跳转锚点"，解决跨页面流程图的可读性问题
4. **P2 — 事件网关**：实现事件等待语义，多个竞争事件中第一个触发者胜出，其余取消
5. **P2 — 业务规则任务**：对接 ydsz-literule 模块，实现 `businessRuleTask` 的 DMN 式规则决策

### 3.2 表单引擎增强（P1 — 影响用户体验）

**现状问题**：当前支持 18 种字段类型，但缺少动态计算、数据联动、外部数据源等高级能力。

**对标参考**：
- 钉钉宜搭：支持公式计算、数据字典、级联选择、远程数据源
- Flowable Form Engine：支持 `formProperty` 的 `type=enum` 可引用外部枚举服务

**落地建议**：

| 能力 | 说明 | 优先级 |
|------|------|--------|
| 公式字段增强 | 支持跨字段引用（如 `field_A + field_B`）、聚合函数（SUM/AVG） | P1 |
| 字段联动规则 | 值变化触发其他字段的可见/可编辑/必填变更（当前仅支持 SHOW/HIDE） | P1 |
| 远程数据源 | SELECT/RADIO/CHECKBOX 支持从 HTTP 接口动态拉取选项 | P1 |
| 子表单嵌套 | 当前 SUB_FORM 仅支持单层，需支持多层嵌套 + 表格模式 | P2 |
| 表单权限矩阵 | 按节点/角色/条件控制字段级可读/可写/隐藏 | P2 |
| 表单数据持久化 | 表单数据独立存储（当前仅 variable JSON），支持草稿恢复 | P2 |

### 3.3 AI 能力实质化（P1 — 影响产品竞争力）

**现状问题**：`FlowAiAssistantServiceImpl` 为骨架实现，所有方法返回占位结果（`aiGenerated=false`）。子目录 `service/ai/` 下 `definition/`、`delegate/`、`i18n/`、`instance/`、`notification/` 已规划但未填充。

**对标参考**：
- 飞书审批：AI 智能审批建议、异常检测、流程优化推荐
- Camunda 8 Optimize：基于历史数据的流程瓶颈分析和预测

**落地建议**：

| AI 能力 | 实现方案 | 优先级 |
|---------|----------|--------|
| 自然语言生成 BPMN | 对接 LLM（通义千问/DeepSeek），输入描述输出 BPMN XML 草稿，经设计器校验后部署 | P1 |
| 审批异常检测 | 基于历史数据训练模型，检测卡单、高驳回率、异常耗时，主动推送告警 | P1 |
| 智能委派推荐 | 基于办理人历史效率、当前负载、技能标签推荐最佳委派目标 | P2 |
| 流程瓶颈预测 | 基于历史运行数据预测当前实例可能的瓶颈节点，提前预警 | P2 |
| 审批文案优化 | 根据审批场景和上下文生成建议审批意见，减少用户输入 | P2 |
| 流程定义翻译 | 流程名称/节点名称的多语言翻译，支持国际化部署 | P3 |

**架构建议**：
- 定义 `LlmServiceClient` Gateway 接口，隔离具体 LLM 实现
- AI 调用统一走 `@Async` 异步 + 超时降级（3s 超时返回降级结果）
- 引入 Prompt Template 管理，支持运行时热更新

### 3.4 流程版本迁移增强（P2 — 影响运维效率）

**现状问题**：`FlowInstanceMigrationService` 已支持实例版本迁移和迁移预览，但缺少：
- 迁移回滚能力
- 批量迁移（按条件筛选后批量迁移）
- 迁移影响范围评估（多少运行中实例受影响）

**对标参考**：Camunda 的 `ProcessInstanceMigration` 支持迁移计划验证、执行、批量操作。

**落地建议**：
- 增加 `MigrationPlan` 概念：先验证→预览影响→执行→确认
- 支持迁移回滚（记录迁移前的定义版本，必要时回退）
- 增加迁移影响分析 API：输入目标定义版本，输出受影响的运行中实例数量和列表

### 3.5 多租户数据隔离增强（P2 — 影响安全性）

**现状问题**：当前通过 `tenant_id` 字段实现逻辑隔离，但缺少：
- 租户级数据库路由（分库分表能力）
- 租户级资源配额（流程定义数、并发实例数限制）
- 租户级数据加密（敏感字段按租户密钥加密）

**对标参考**：Camunda 多租户支持 `tenantId` 隔离 + 租户级权限控制。

**落地建议**：
- 引入租户级资源配额管理（`FlowTenantQuota`），限制每租户最大定义数/并发实例数
- 敏感变量字段支持租户级 AES 加密存储
- 预留分库分表扩展点（ShardingSphere），支持未来按租户分片

---

## 四、性能提升建议

### 4.1 高频查询路径优化（P0 — 影响响应速度）

**现状问题**：

| 问题 | 位置 | 影响 |
|------|------|------|
| `FlowVariableReplacer.resolveFieldValue` 每次反射无缓存 | engine/impl/FlowVariableReplacer.java:86 | 高频调用路径上重复反射 |
| `FlowSensitiveMasker.maskAuto` 遍历 12 个 Pattern × K 个 key | engine/FlowSensitiveMasker.java:130 | 大数据量变量脱敏耗时 |
| `FlowNodeVO.getExtMap` 使用 `synchronized` double-check | domain/vo/FlowNodeVO.java:60 | 高并发瓶颈 |
| `AviatorExpressionEvaluator` 和 `FlowServiceNodeExecutor` 各自独立 Aviator 实例 | engine/expr/ vs engine/ | 双份编译缓存，内存浪费 |
| 加签历史分页为内存分页 | web/controller/FlowTaskController.java:936 | 大数据量 OOM |

**落地建议**：
- 反射 Method/Field 对象缓存到 `ConcurrentHashMap`（key = Class + fieldName）
- 敏感词 Pattern 预编译为 `static final`，使用 `Trie` 结构合并多模式匹配
- `FlowNodeVO.getExtMap` 改为 `ConcurrentHashMap` + `computeIfAbsent`
- Aviator 实例统一为 Spring Bean，全局共享编译缓存
- 加签历史分页改为数据库分页（`LIMIT offset, size`）

### 4.2 数据库索引与查询优化（P0 — 影响稳定性）

**现状问题**：

| 问题 | 位置 | 影响 |
|------|------|------|
| `uk_business_type_id` 全表唯一约束 | ydsz-workflow.sql:232 | 驳回后无法重新发起 |
| `uk_instance_node_assignee` 含可 NULL 字段 `iter_var` | ydsz-workflow.sql:287 | 幂等防重失效 |
| `NOT EXISTS` 子查询 | FlowInstanceMapper.xml:291 | 高频场景性能差 |
| JSON 字段过多（5+ 个/表） | 多表 | 无法索引，查询全表扫描 |

**落地建议**：
- **唯一约束修复**：移除 `uk_business_type_id` 全表唯一约束，改为应用层幂等校验（启动时检查是否存在 RUNNING/SUSPENDED 状态的实例）
- **NULL 字段修复**：`iter_var` 列加 `DEFAULT ''`，或使用特殊占位符 `__NULL__` 代替 NULL
- **NOT EXISTS 改写**：改为 `LEFT JOIN ... IS NULL` 或使用 `idx_instance_id_task_status` 覆盖索引
- **JSON 字段治理**：将高频查询的 JSON 子字段拆列为独立字段（如 `sla_action`、`sla_due_hours`、`node_free_jump`）

### 4.3 缓存策略优化（P1 — 影响吞吐量）

**现状问题**：
- 流程定义缓存使用本地缓存 + Redis Pub/Sub 失效，但缓存穿透保护不足
- 待办任务数/超期数等统计指标无缓存，每次查询打 DB
- 用户/组织信息无本地缓存，每次审批人解析都 RPC 调用

**对标参考**：Flowable 使用 `ProcessDefinitionCache` + `CaseDefinitionCache` 多级缓存，Camunda 使用 `Cache` 接口抽象支持 Caffeine/Redis 实现。

**落地建议**：

| 缓存项 | 策略 | TTL |
|--------|------|-----|
| 流程定义 | 本地 Caffeine + Redis Pub/Sub 失效 | 30min |
| 流程节点/跳转 | 随定义缓存一起失效 | 30min |
| 待办任务数 | Redis INCR/DECR 实时维护 | 永久 |
| 超期任务数 | 定时刷新 + 实时修正 | 5min |
| 用户/组织信息 | 本地 Caffeine + 监听变更事件失效 | 15min |
| 表单 Schema | 本地缓存 + 版本号校验 | 1h |

### 4.4 异步化与批量操作优化（P1 — 影响并发能力）

**现状问题**：
- 批量操作（`batchPass`/`batchReject`/`batchTransfer`）在同一事务中处理，100 个任务 = 100 行锁 + 长事务
- `FlowQueuePublisher` 无重连机制，MQ 启动时不可用则永久丧失跨服务事件发布能力

**落地建议**：
- 批量操作改为"逐任务独立事务"模式（类似 `batchStartInstances` 的设计），每个任务独立提交
- 批量操作增加并发度控制（Semaphore 限制同时处理数，默认 10）
- `FlowQueuePublisher` 增加健康检查 + 延迟重连（指数退避，最大重试 5 次）
- 通知发送统一走 `@Async` + 线程池隔离，避免通知失败阻塞主流程

---

## 五、体验改善建议

### 5.1 REST API 规范化（P1 — 影响接入效率）

**现状问题**：
- 部分接口返回 `Map<String, Object>`，失去编译期类型安全
- Controller 间职责边界模糊（催办在 TaskController、SLA 在 DefinitionController）
- Feign 客户端仅暴露 3 个方法，能力覆盖不足

**对标参考**：阿里云工作流 API 采用统一的 `Action` + `RequestBody` + `ResponseBody` 模式，所有接口有明确的请求/响应结构。

**落地建议**：
- 所有 `Map<String, Object>` 返回值替换为强类型 VO（`FlowNodeDurationVO`、`FlowAuditTrailVO`）
- 催办接口从 `FlowTaskController` 迁移到 `FlowInstanceController`
- Feign 客户端扩展至 10+ 核心方法（覆盖任务办理、加签、委派、查询）
- 统一 API 错误响应格式（`errorCode` + `message` + `details` + `traceId`）

### 5.2 监控与可观测性增强（P1 — 影响运维效率）

**现状问题**：
- `FlowMetrics` 仅 6 个指标，缺少 SLA 达成率、缓存命中率、AI 调用成功率等
- 无分布式链路追踪（TraceID 未贯穿引擎内部调用链）
- 无慢查询告警阈值配置

**对标参考**：Camunda 通过 Micrometer 暴露 100+ 指标，支持 Prometheus + Grafana 看板。

**落地建议**：

| 新增指标 | 类型 | 用途 |
|----------|------|------|
| `ydsz_flow_sla_compliance_rate` | Gauge | SLA 达成率 |
| `ydsz_flow_cache_hit_ratio` | Gauge | 缓存命中率 |
| `ydsz_flow_ai_call_duration_ms` | Timer | AI 调用耗时 |
| `ydsz_flow_ai_fallback_total` | Counter | AI 降级次数 |
| `ydsz_flow_expression_eval_duration_ms` | Timer | 表达式求值耗时 |
| `ydsz_flow_listener_duration_ms` | Timer | 监听器执行耗时 |
| `ydsz_flow_db_query_duration_ms` | Timer | 数据库查询耗时 |

- 引入 `@Traceable` 注解 + Micrometer Tracing，将 TraceID 贯穿引擎内部全链路
- 慢查询告警：SQL 执行 > 500ms 自动记录 WARN 日志 + Sentry 上报

### 5.3 设计器体验优化（P2 — 影响配置效率）

**现状问题**：
- 缺少流程模拟运行（Simulation）能力
- 缺少 BPMN XML 校验结果的友好提示（当前仅抛异常）
- 缺少流程定义 Diff 对比（版本间变更可视化）

**对标参考**：
- Flowable Modeler：支持流程模拟、断点调试、变量追踪
- Camunda Modeler：支持 BPMN Lint 校验 + 错误定位到具体元素

**落地建议**：
- 增加流程模拟 API：输入定义 ID + 变量，输出预测路径（将经过哪些节点、办理人是谁）
- BPMN 校验结果结构化返回：`{elementId, elementName, errorCode, message, severity}`，前端可定位到具体图形元素
- 流程定义版本 Diff：可视化展示两个版本间的节点/连线/配置变更

---

## 六、过度设计识别与简化建议

### 6.1 可简化的设计

| 设计 | 现状 | 问题 | 简化建议 |
|------|------|------|----------|
| **双层监听器** | 节点级 `FlowListenerPlugin` + 全局级 `GlobalFlowListener` | 两套接口、两套执行器，学习成本高 | 合并为统一的 `FlowEventListener`，通过 `scope=NODE/GLOBAL` 区分作用域 |
| **表达式引擎三层架构** | `DefaultFlowVariableStrategy` → `ExpressionEvaluator` → `AviatorExpressionEvaluator` + 正则降级 | 过度抽象，实际只用 Aviator | 简化为 `ExpressionEvaluator` 单接口 + Aviator 实现，移除正则降级路径 |
| **AI 子目录结构** | 5 个子目录（definition/delegate/i18n/instance/notification） | 目录已建但无内容，增加导航成本 | 合并为单个 `FlowAiService`，待真正需要拆分时再重构 |
| **VO 数量膨胀** | 48 个 VO，部分字段高度相似 | 维护成本高，很多 VO 仅一个接口使用 | 合并相似 VO（`FlowRejectableNodeVO`/`FlowRecallableNodeVO` → `FlowNodeActionVO`） |
| **Gateway 抽象** | `FlowNodeExt` 与 `FlowNodeVO` 存在大量重复的 ext JSON 解析 getter | 同一逻辑两处维护 | 抽取为 `FlowNodeConfig` 值对象，两处共用 |

### 6.2 可移除的冗余

| 冗余 | 说明 | 建议 |
|------|------|------|
| `@Deprecated` 方法 | 多个 Repository 和 Service 中存在废弃方法未清理 | 确认无调用后移除 |
| `FlowExpressionEvaluator` 正则降级路径 | Aviator 稳定运行后，正则降级已无实际价值 | 移除，简化为纯 Aviator |
| `FlowEventListener` 旧版接口 | 21 个 default 方法，与 `FlowListenerPlugin` 功能重叠 | 迁移到新版监听器后移除 |
| `FlowQueuePublisher` 一次性判定 | MQ 启动失败则永久丧失能力 | 改为健康检查 + 自动重连 |

---

## 七、实施路线图

### 第一阶段：Bug 修复 + 稳定性提升（1-2 周）

| 项 | 优先级 | 预估工时 |
|----|--------|----------|
| 修复 XML Mapper 列名乱码 | P0 | 0.5d |
| 修复 `FlowDefinitionMapper.xml` 列名不一致 | P0 | 0.5d |
| 修复 PostgreSQL 专属语法 | P0 | 1d |
| 修复 `uk_business_type_id` 唯一约束 | P0 | 1d |
| 修复 `iter_var` NULL 导致唯一约束失效 | P0 | 0.5d |
| 加签历史分页改为数据库分页 | P1 | 1d |
| 反射 Method/Field 缓存 | P1 | 1d |

### 第二阶段：架构优化 + 性能提升（2-4 周）

| 项 | 优先级 | 预估工时 |
|----|--------|----------|
| 创建 `FlowInstanceStateMachine` | P0 | 2d |
| 领域模型充血化（FlowInstance 聚合根） | P0 | 3d |
| 值对象抽取（SlaConfig/ServiceNodeConfig 等） | P1 | 3d |
| 领域事件补充 + DomainEventPublisher | P1 | 3d |
| 缓存策略优化（统计指标/用户信息） | P1 | 2d |
| 批量操作事务粒度优化 | P1 | 2d |
| 监控指标补充 | P1 | 2d |

### 第三阶段：功能增强 + 体验改善（4-8 周）

| 项 | 优先级 | 预估工时 |
|----|--------|----------|
| BPMN 终止事件运行时 | P1 | 3d |
| 消息/信号运行时（Redis Pub/Sub） | P1 | 5d |
| AI 能力实质化（自然语言生成 BPMN） | P1 | 5d |
| 表单引擎增强（公式/联动/远程数据源） | P1 | 5d |
| REST API 规范化（强类型 VO 替换 Map） | P1 | 3d |
| 流程模拟运行能力 | P2 | 5d |
| 流程定义版本 Diff | P2 | 3d |
| Repository CQRS 拆分 | P2 | 5d |

### 第四阶段：长期演进（8-12 周）

| 项 | 优先级 | 预估工时 |
|----|--------|----------|
| 事件网关完整实现 | P2 | 5d |
| 业务规则任务对接 ydsz-literule | P2 | 3d |
| 补偿事件运行时 | P2 | 5d |
| 多租户分库分表 | P2 | 5d |
| AI 异常检测 + 瓶颈预测 | P2 | 8d |
| 分布式链路追踪 | P2 | 3d |

---

## 八、总结

ydsz-workflow 作为自研工作流引擎，在架构分层、安全防护、降级设计等方面已达到生产级水平。与 Flowable/Camunda 的差距主要在 BPMN 标准兼容性和领域模型丰富度上，而非基础能力。

**核心建议**：
1. **先修 Bug 再优化架构**：多数据库兼容硬伤和唯一约束缺陷是生产隐患，应优先修复
2. **领域模型充血化是长期收益最大的改造**：消除贫血模型带来的逻辑散落问题
3. **AI 能力是差异化竞争力**：骨架已搭好，应尽快对接 LLM 实现实质化
4. **BPMN 标准兼容性按需补齐**：优先实现终止事件和消息运行时，其他按业务需求驱动
5. **避免过度设计**：合并冗余监听器、简化表达式引擎、清理废弃代码

> 本建议基于代码静态分析得出，部分性能建议需结合实际运行数据（慢查询日志、APM 指标）进一步验证优先级。
