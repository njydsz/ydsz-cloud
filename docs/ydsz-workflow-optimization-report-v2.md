# ydsz-workflow 模块优化完善建议报告（v2 · 以当前代码为准）

> **分析日期**：2026-08-27
> **分析基线**：当前 git 工作区最新代码（`ydsz-workflow` 6 子模块、400 个主源文件、约 71,400 行，工作树干净）
> **对标竞品**：Flowable 7.x / Camunda 7.x / 钉钉宜搭（表单）/ Camunda Optimize（分析）
> **对标规范**：云顶编码规范（§34 分层命名、FeignClientConstants、自研 json/cache/POI）、BPMN 2.0 OMG 标准
> **方法声明**：本文所有结论均附代码级证据（文件 + 行号）；对 v1 报告与 README 逐条交叉核验，**以代码事实为准**

---

## 一、现状总评

ydsz-workflow 是零第三方 BPMN 引擎依赖的自研工作流（Spring Boot 3 + MyBatis-Plus + PostgreSQL），DDD 六子模块分层（api/app/domain/infra/server/web）。

**整体成熟度较高**：
- 云顶规范执行到位：JSON 全走 `YdszJson`、缓存全走 `YdszCache`（TINYLFU + 空值防穿透）、无 fastjson/gson/Caffeine 直接依赖、Feign 用 `FeignClientConstants` 常量（`WorkflowServiceClient.java:26-29`）
- 引擎核心设计合格：实例级分布式锁（`DefaultFlowAdvancer.java:126-130`，wait 5s / lease 60s）、排他网关互斥 + BPMN default 出边兜底（`:514-563`）、并行 join N/M 令牌聚合 + Redis 异常降级路径（`:356-427`）、终止事件运行时已接线（`:158-165`）
- 生产化配置齐备：定义/用户/表单 Schema 多级缓存 + Redis Pub/Sub 集群失效广播、调度器 `@DistributedScheduled` 分布式安全、Prometheus Gauge 30s TTL 快照缓存
- 审批链路功能完整：委派回归、表单字段权限 + Schema 校验、附件、WebSocket 待办推送、审计留痕

**本报告四个关键修正判断**（相对 v1 报告与 README 的 doc drift）：
1. **v1 报告中大量"P0 硬伤"在当前代码中已不存在或已修复**（详见 §2.1）
2. **README 存在系统性漂移**：AI 骨架实际是空目录、audit_log 并未分区、实体无 DO 后缀等（§2.4）
3. **存在一批"形似合规、运行未接线"的领域工件**：15 个领域事件类、状态机、DomainEventPublisher 均为死代码（§3.5 过度设计），这是本模块最大的结构性问题
4. **发现新的真 P0 并发缺陷**：会签计数的假乐观锁（§3.1 A2）

---

## 二、doc drift 核对（v1 报告 / README vs 当前代码）

### 2.1 v1 建议 → 已落地项（不再列为待办）

| v1 建议 | 当前代码证据 |
|---|---|
| 创建 `FlowInstanceStateMachine` | 已创建于 `domain/statemachine/FlowInstanceStateMachine.java`（但未被服务层使用，见 §3.5 O3） |
| 领域事件补齐（Suspended/RolledBack/Claimed 等） | 已补到 15 个事件类（但全部为死代码，见 §3.5 O1） |
| XML Mapper 列名乱码 / `EXTRACT(EPOCH` / `FILTER (WHERE` 方言硬伤 | 全模块 mapper XML 已搜不到此类语法；`FlowDefinitionMapper.xml:10,38` 使用 `flow_version` 映射正确 |
| `uk_business_type_id` 全表唯一约束阻塞重新发起 | 三库 DDL 中均已不存在该约束 |
| 反射 Method/Field 无缓存 | `FlowVariableReplacer.java:21-35` 已用 `ConcurrentHashMap` 缓存（P1-8） |
| 加签历史内存分页 OOM | `FlowTaskController.java:942` 注明"P1-8 修复：数据库级分页" |
| 批量操作同事务长锁 | `FlowTaskBatchServiceImpl.java:21,51` 明确"不开启 @Transactional，逐任务独立提交" |
| 迁移缺 dry-run | `FlowInstanceMigrationServiceImpl.java:133,208` 已支持 previewMigration / dryRun |
| SpEL 双引擎并存 | `DefaultFlowVariableStrategy.java:35` "SpEL 自 P1-3 起废弃，收敛 Aviator"，正则仅作兼容兜底 |
| FlowNodeVO ext 弱类型 getter + synchronized double-check | `FlowNodeVO.java:55-67` 已有 volatile 解析缓存 + SlaConfig/ServiceNodeConfig/CountersignConfig/AssigneeConfig 四个值对象 |

### 2.2 v1 论断 → 不成立项

| v1 论断 | 实际情况 |
|---|---|
| "代码量约 15 万行" | 主源码实测 71,396 行 / 400 文件 |
| "5 种会签模式" | `FlowPerformType` 仅 OR / PARALLEL / WEIGHTED 3 种，对应 3 个策略类 |
| "多数据库支持（MySQL/Oracle/PostgreSQL）"是其优势 | 运行时仅 PostgreSQL；`data/{mysql,oracle,postgre}/` 维护三库 DDL 脚本，但 mapper 未做多方言路由 |

### 2.3 v1 论断 → 仍然成立项（保留）

- 统计查询返回 `Map<String,Object>`：`FlowHisTaskRepository.java:99,112`（selectOverviewStats / selectApproverEfficiency）
- Repository 方法膨胀：`FlowRunTaskRepository` 25 个方法、`FlowInstanceRepository` 20 个，命令查询混杂
- 消息/信号/补偿事件仅解析标记、事件网关降级映射（`BpmnElementHelper.java:57-77`）
- 流程监控指标过少：`FlowMetrics` 仅 3 Counter + 2 Timer + 1 Gauge（共 6 个）
- 事件网关 eventBasedGateway→CONDITION、complexGateway→INCLUSIVE 为降级映射

### 2.4 README 自身漂移清单（需修复文档）

| README 表述 | 代码事实 |
|---|---|
| AI 辅助为"显式降级骨架" | `server/service/ai/` 下 definition/delegate/i18n/instance/notification **5 个目录全空**（0 个 Java 文件），且 commit fcefb5064 已删除废弃的 FlowAI VO/Service。README 应改为"AI 能力未实现" |
| "实体命名使用 DO 后缀（如 FlowDefinition）" | 21 个实体均无 DO 后缀（`infra/entity/FlowRunTask.java` 等），描述示例本身就与规则矛盾 |
| "`ydsz_flow_audit_log` 为 PostgreSQL 范围分区表（按月分区）" | `data/postgre/ydsz-workflow.sql:1199-1226` 是普通单表，无 PARTITION BY |
| "DDL 由各部署环境统一维护，不在模块内" | 同一章节与 `data/` 目录内三库 DDL 脚本自相矛盾 |
| "暂无单元测试" | 已有 6 个测试类（枚举×2、状态机×1、引擎解析器×2、定义缓存×1） |
| Controller 数量口径（11 个 / 方法级路径说明） | `web/controller/` 实际 15 个 Controller 文件 |

---

## 三、五维度优化建议

### 3.1 架构优化（P0 居多）

#### A1（P0）：审批通过链路并发控制缺失——真实双审批风险

**证据链**：
- `start`/`advance` 有分布式锁（`DefaultFlowAdvancer.java:126,225`），但审批入口 `FlowTaskPassService.pass()`（`:108`）**没有** `@YdszDistributedLock`
- `pass()` 仅靠读取态检查幂等（`FlowTaskPassService.java:110-117`，`isFinished()` 读旧状态），非原子
- 会签计数更新是 VO 读改写：`ParallelCountersignStrategy.onUserPassed`（`:43-46`）`task.setApproveFinished(finished); taskRepository.update(task)` —— 两个并行审批人同时读到 `finished=1` 各写回 `2`，丢失一次计数，导致会签少一人生效或流程卡死

**最讽刺的一点**：Mapper 里已有原子自增 SQL 但没被使用——`FlowRunTaskMapper.xml:163` `SET approve_finished = approve_finished + 1`。

**另一处假防护**：`updated = taskRepository.update(task) != null ? 1 : 0`（`ParallelCountersignStrategy.java:46`）——update 返回的是 VO 对象，恒非 null，注释声称的"乐观锁冲突抛异常"（`:48-53`）是**永远不会触发的死分支**。

**落地动作**（三选一，推荐 ①+③ 组合）：
1. 计数改走 Mapper 已有的原子 SQL（`approve_finished = approve_finished + 1` + WHERE 条件），并按受影响行数判定冲突；
2. 任务表增加 `version` 字段 + MyBatis-Plus `@Version` 真乐观锁；
3. `pass/reject` 入口补 `@YdszDistributedLock`（键对齐 `flow:instance:op:{instanceId}` 或 `flow:task:{taskId}`），并给 `markProcessed` 加条件更新（`WHERE task_status IN ('PENDING','CLAIMED')`）按行数判定是否已被处理。
4. 补一个双线程并发 pass 的集成测试作回归证明。

#### A2（P0）：`NameServiceClient` 无任何实现——启动装配风险

**证据**：`domain/gateway/NameServiceClient.java` 定义了 getUserName/getUserNames/getUserNameByType 三方法；全仓 grep（含 ydsz-common、ydsz-userinfo）**无任何 `implements NameServiceClient`**、无 `@Bean` 装配（`FlowAutoConfiguration.java` 中亦无）。而 `FlowUserCacheService` 是 `@Component` 且构造注入该接口（`FlowUserCacheService.java:41-58`）——若运维环境无法提供此 bean，应用上下文启动即失败。

**落地动作**：在 infra/server 提供适配器（Feign 调 userinfo 服务，参照 `com.njydsz.common.feign.NotificationClient` 在 `FlowNotificationServiceImpl.java:19` 的引用模式），或在 ydsz-common-feign 定义统一常量后注入；同时在 CI 增加"应用上下文可启动"冒烟测试，防止此类装配缺口再次静默引入。

#### A3（P1）：领域事件体系"名存实亡"——重构而非再追加

**证据**：15 个事件类 + `DomainEventPublisher` 接口 + `FlowDomainEvent` 基类，除 package-info 外**零引用**（`grep "new FlowTaskCompletedEvent"` 等均无命中）；实际运行时事件走两条通道：进程内 Spring 事件 `FlowWorkflowEvent`（字符串 eventType，弱契约，`FlowTaskSupport.java:327-332`）+ 跨服务 MQ `FlowQueuePublisher`（`FlowQueueChannels.FLOW_EVENT`）。

**落地动作**（二选一，推荐 ①）：
1. 收编：让现有业务点改为发布强类型领域事件（复用 15 个已写好的类），实现 `DomainEventPublisher`（Spring 同步发布 + `@TransactionalEventListener(AFTER_COMMIT)` 异步桥接 MQ）；`FlowDomainEvent` 基类补 `eventId / correlationId / causationId / tenantId / version` 五字段（当前仅有 source + occurredAt，`FlowDomainEvent.java:28-31`）；
2. 若短期不接线，删掉整个 domain/event 包，避免后人误以为事件体系已建成。

#### A4（P1）：状态机未接线——状态流转绕过校验

**证据**：`FlowInstanceStateMachine.requireTransition` 与实体行为 `FlowInstance.transitTo/reject`（`infra/entity/FlowInstance.java:159,175-177`）存在，但 server 层 `.transitTo(` **零调用**；实例状态由 `instanceRepository.updateStatus(...)` 直写（如 `DefaultFlowAdvancer.java:170-177`、`FlowTaskPassService.java:290-298`），不经过任何合法性校验。

**落地动作**：最小侵入方案是在 `FlowInstanceRepositoryImpl.updateStatus` 内先调 `requireTransition` 校验再落库；非法流转抛 `WorkflowExceptionCode` 语义错误码。这能让两套状态机从"装饰品"变为真正的护栏。

#### A5（P2）：Repository CQRS 与强类型统计

沿用 v1 结论（仍成立）：将统计类方法（Map 返回者全部收敛到 analytics 查询侧）拆出独立 QueryRepository，统一返回强类型 VO；高频指标（待办数/超期数）已有 Gauge TTL 缓存兜底，仅需把统计口径从实时 COUNT 固化为分钟级快照。

### 3.2 功能增强

#### F1（P1）：消息/信号事件运行时（对标 Flowable/Camunda Runtime）

当前 `messageEventDefinition/signalEventDefinition` 仅解析标记。架构上现成的落地路径：Redis Pub/Sub 广播基础设施已有（`FlowDefinitionCacheBroadcaster` 模式可复制），新增 `signal` 频道即可支撑跨实例信号投递；`ydsz_flow_event_subscription` 表与 `FlowEventSubscriptionServiceImpl` 已就位。预计 3~5 天完成 signal 语义（message 因需实例级相关键匹配，放二期）。

#### F2（P1）：表单引擎补三项高价值能力（对标钉钉宜搭）

- 公式字段：跨字段引用计算（Aviator 引擎已在 classpath，直接复用求值器）
- 字段联动扩展：当前仅 SHOW/HIDE，增加 READONLY/REQUIRED 动作
- 远程数据源：SELECT/RADIO/CHECKBOX 从 Feign 接口拉选项（对齐 `NameServiceClient` gateway 模式抽象为 `FormOptionsProvider` SPI）

注意把公式求值纳入 §A1 的并发视角：变量提交 → schema 校验 → 变量持久化在同一事务内。

#### F3（P1）：流程模拟运行（Simulation）

输入 definitionId + 模拟变量 → dry-run 走 `advance` 纯路由逻辑（`DefaultFlowAdvancer.advance` 本身就是"只算不改"的设计，天然适合模拟），输出预测路径 + 每节点办理人预览 + 条件分支走向。实现成本低（engine 侧约 1 天），对设计器可用性提升极大；需挡掉 join 令牌副作用（模拟时 skip token 写入或使用 shadow instanceId）。

#### F4（P2）：业务规则节点对接 ydsz-literule

`businessRuleTask` 映射为 SERVICE 节点 + literule 规则集调用，正好消化 ydsz_flow_dmn_rule 废弃表的迁移诉求（README 已标注待迁移 literule）。

#### F5（P2）：迁移增强收尾

dry-run 已具备（`:133,208`），补两件事：① 影响评估 API（目标版本下受影响运行中实例数预查）；② 迁移前快照记录源版本号，支持回退。参考 Camunda `ProcessInstanceMigration` 计划验证语义。

#### F6（P2）：AI 能力实质化的前置清理

空目录（见 §2.4）要么删除要么落第一个真实能力。若重启：按 v1 架构建议定义 `LlmServiceClient` Gateway 接口 + 3s 超时降级 + 不阻塞主链路，优先做"审批异常检测"（数据都在 his_task/his_instance 里，纯读路径风险最低）。

### 3.3 性能提升

| 编号 | 问题 | 证据 | 动作 |
|---|---|---|---|
| P-1（P1） | join 降级路径循环查库：每个入边一次 `findPendingByNode` | `DefaultFlowAdvancer.countActiveIncomingTasks`（`:671-688`） | 改一条聚合 SQL：按源节点集合一次查出 active counts |
| P-2（P1） | pass 链路同一节点重复回查 node 表（≥2 次） | `FlowTaskPassService.validateFormFieldPerms`（`:222`）与 `firePersonalCompletedEvent`（`:313-316`）各查一次 | 单次查询结果作为参数传递 |
| P-3（P1） | 审计日志表无分区却按年积累（README 还宣称分区） | `data/postgre/ydsz-workflow.sql:1199-1226` 普通表 | 若确定 PostgreSQL-only，补 pg_partman 月分区脚本进 data/postgre；否则修 README 口径 |
| P-4（P2） | 深分页风险：任务列表 offset 分页 | 常规列表接口 | 待办列表增加 `(tenant_id, status, due_date, id)` 游标分页参数 |
| P-5（P2） | 可观测性纵深不足 | FlowMetrics 6 个指标 vs Camunda 100+ | 优先补 4 个：表达式求值耗时 Timer、join 等待时长、监听器/MQ 发布耗时、缓存命中率；同时把 Micrometer Tracing traceId 贯穿 FlowQueuePublisher 发布体（MQ 消息目前仅 eventType+payload） |

### 3.4 体验改善

| 编号 | 问题 | 证据 | 动作 |
|---|---|---|---|
| E-1（P1） | DTO 校验注解覆盖不全 | 24 个 DTO 中仅 14 个含 @NotNull/@NotBlank/@Size | 首先把 DTO 与 DB NOT NULL 对齐一遍（特别是 FlowTaskOperateDTO.taskId/userId） |
| E-2（P1） | BPMN 校验错误无元素定位 | BpmnXmlParser 解析失败直接抛异常 | 校验错误结构化：{elementId, elementName, errorCode, severity}，前端定位到画布节点 |
| E-3（P2） | @Validated 覆盖 11/15 Controller | web/controller 包抽查 | 补齐缺失的 4 个；错误响应统一带 traceId |
| E-4（P2） | 术语与错误码体验 | WorkflowExceptionCode B70001-B75099 区间已有 | 错误 key i18n 全量核对（发现个别 message 直接传中文串如 `error.workflow.reject.target.not.found` 作为 message 而非 key：`DefaultFlowAdvancer.java:276,495`，应统一为 key+params 风格） |
| E-5（P0，零成本） | README 系统性漂移（§2.4 清单） | — | 按代码事实重写 AI 章节/分区说明/测试章节/实体命名规则；建立"发布前 README 与 grep 清单比对"的习惯 |

### 3.5 过度设计识别与简化

| 编号 | 设计 | 现状证据 | 处置建议 |
|---|---|---|---|
| O1（P0） | domain/event 死码包 | 15 个事件类 + Publisher + 基类零生产引用（§A3） | 见 §A3：二选一，**不允许保持现状**（"看着有"比"没有"更危险） |
| O2（P0） | service/ai 五个空目录 | `find` 递归结果 0 文件；近期 commit 已删 FlowAI 类 | 删除目录（git 保底可恢复）；README 同步修改 |
| O3（P1） | 两套状态机游离于主链路外 | server 层 `.transitTo(` 零调用（§A4） | 按 A4 接线；若团队评估不接，则连同 `FlowInstanceStateMachineTest` 一并移除 |
| O4（P2） | 正则兜底表达式链 | `DefaultFlowVariableStrategy.java:31` Aviator 失败回退正则 | 设定淘汰期（观察 2 个迭代 WARN 日志频次后删除），避免永久双路径维护 |
| O5（P2） | VO 数量 52 个 | domain/vo 52 文件 | 低优先；待 CQRS 拆分时顺带合并相似 VO |
| O6（P2） | `FlowWorkflowEvent` 弱类型契约 | 字符串 eventType（`FlowTaskSupport.java:332`） | 若不执行 A3 方案①，至少把 eventType 收敛为枚举 |

---

## 四、落地路线图

### 第一阶段：正确性止血（本周内，约 3 人日）

| 项 | 优先级 | 工时 |
|---|---|---|
| A1 会签原子计数 + 假乐观锁死码清除 + 并发集成测试 | P0 | 1d |
| A2 NameServiceClient 适配器补齐 + 启动冒烟测试 | P0 | 0.5d |
| iter_var 幂等约束修复（DDL：iter_var 非 FOREACH 节点写入 `__NULL__` 占位符，或唯一索引部分化 partial index） | P0 | 0.5d |
| O1/O2 死码处置决策（接线 or 删除） | P0 | 0.5d |
| E5 README 漂移修复 | P0 | 0.5d |

> 说明：`uk_ydsz_flow_run_task_instance_node_assignee` 含 `DEFAULT NULL` 的 `iter_var`（`data/postgre/ydsz-workflow.sql:465,475`），PostgreSQL 唯一约束视 NULL 为互异值，FOREACH 场景防重失效；MySQL 下 NULL 相互比较不相等的特性同样使该索引失效。

### 第二阶段：架构补强（1-2 周）

A3 事件体系收编（含 FlowDomainEvent 基类五字段扩充）、A4 状态机接线、F1 信号事件运行时、E-1/E-2 校验体系、P-1/P-2/P-3 性能项。

### 第三阶段：能力增值（2-4 周）

F2 表单三件套、F3 流程模拟、测试覆盖专项（引擎网关/join/REJECT 回归套件 + Testcontainers PostgreSQL 集成测试，当前仅 6 个测试类的覆盖率远低于 400 文件规模应有水平）、F4/F5/F6 按业务节奏推进。

---

## 五、结语

本模块工程底座（规范合规、缓存、锁、调度、指标）已达生产水位，v1 报告中的多数硬伤已被高质量修复。当前真正的风险集中在两类问题上：

1. **并发正确性盲区**（A1 假乐观锁、iter_var 幂等失效）——这类问题单测测不出来，需要并发回归测试 + 条件更新的防御性写法；
2. **"形式合规、运行未接线"的领域工件**（事件包、状态机、gateway 空壳）——DDD 的形做了，DDD 的实还没贯通。下一阶段的主线不是继续堆功能，而是把这些建好的"骨头"接上神经，让结构资产真正运转起来。

---
*报告生成：WorkBuddy · 基于 git c116ada03 工作区最新代码逐文件核验*
