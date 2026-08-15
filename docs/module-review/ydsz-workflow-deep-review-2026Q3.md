# ydsz-workflow 深度审查报告（2026 Q3）

> 分析基准：最新代码（2026-08-15，306 个 Java 类 / 32,390 行，5 层 DDD：api / domain / infra / server / web）
> 对标对象：Flowable / Camunda / Activiti（BPMN 引擎）、钉钉 / 飞书 / 企微审批（产品体验）、若依 / Pig / SpringBlade（平台治理）、阿里巴巴 Java 开发手册、Google Java Style Guide
> 关联文档：`README.md`、`docs/云顶编码规范.md`

---

## 0. 定位与总览

ydsz-workflow 是一个**自研 BPMN 2.0 工作流引擎**（YDSZ-Flow v2），零第三方 BPMN 依赖，能力面已相当完整：

| 能力域 | 现状 |
|---|---|
| 节点类型 | START / END / APPROVAL / SERVICE / CONDITION(排他) / PARALLEL / INCLUSIVE / CC(抄送) / SUBPROCESS / FOREACH 等 |
| 会签 | 6 种策略（OR / SEQUENTIAL / PARALLEL / VOTE / WeightedVote / Foreach）+ 动态加减签 |
| 网关 | 排他互斥、包容、并行 join（Redis Lua 令牌，支持 N/M 聚合） |
| 扩展 | DMN 决策表、SLA/催办、灰度发布(Canary)、三方审批(钉钉/飞书/企微)、子流程嵌套、定时器、表单引擎、i18n、模板库 |
| 治理 | 分布式锁、缓存(多级+集群广播)、Outbox 事件、Seata 预留、Prometheus 指标 |

总体评价：**引擎骨架质量中上**（BPMN 解析已做 XXE 防护、join 令牌用 Lua 原子化、缓存做了 sourceRef 索引优化），但存在**几处 P0 正确性缺口**与**系统性架构债**，且**测试覆盖为零**，是当前最大的风险敞口。

---

## 1. 🔴 P0 正确性 / 数据一致性缺口

### 1.1 退回目标解析错误：`resolveRejectTarget` 未用 sourceRef

**发现**：`DefaultFlowAdvancer.resolveRejectTarget()` 用 `incoming.get(0).getSkipName()` 反查前驱节点，而 `getSkipName()` 是 sequenceFlow 的 `name` 属性（bpmn-js 设计器通常留空）；当其为空时直接返回 **`currentNodeCode`（当前节点自身）**，导致默认退回「退回给自己」。正确的前驱编码明明存在——`ext.sourceRef`，且类内已有 `extractSourceNodeCode()` 方法，却仅在 `countActiveIncomingTasks` 中使用。

```java
public String resolveRejectTarget(String definitionId, String currentNodeCode) {
    List<FlowSkip> incoming = flowDefinitionCacheService.getSkipsByNextNode(definitionId, currentNodeCode);
    if (!incoming.isEmpty()) {
        return incoming.get(0).getSkipName() == null
                ? currentNodeCode          // ← BUG：退回自身
                : lookupNodeCodeByName(definitionId, incoming.get(0).getSkipName()); // ← 名称匹配脆弱
    }
    ...
}
```

**后果**：默认「退回上一节点」大概率退回自身或依赖名称巧合，是高频路径的正确性缺陷。
**建议**：改为 `String src = extractSourceNodeCode(incoming.get(0)); return src != null ? src : startNode.getNodeCode();`，删除 `lookupNodeCodeByName` 名称匹配路径。

### 1.2 推进非原子 + 「对账任务」实际不存在

**发现**：`DefaultFlowAdvancer.start()` 的 Javadoc 明确承认：

> 本方法自身不开事务，任务生成与状态回写各自落在 `instanceService` 的方法事务内；若回写阶段失败，已生成的任务不会回滚，**需依赖对账任务修复**。

但全模块搜索「对账 / reconcile」，**没有任何对账 Job/Scheduler 实现**（仅存在于注释与 `FlowQueuePublisher`/`FlowThirdPartySyncServiceImpl` 的「兜底」描述里）。`scheduler/` 与 `job/` 目录仅有 AutoUrge / HistoryArchive / ThirdPartyRetry / Timeout 四类，无一致性对账任务。

**后果**：任务生成成功、状态回写失败时会产生「孤儿任务」，且无任何自动修复机制——这是 BPMN 引擎最不该有的脏数据风险。
**建议（P0）**：
1. 短期：在 `FlowAdvancer` 外层用一个 `@Transactional` 事务 + 命令模式（对标 Flowable 的 `CommandContext`）把「路由计算 + 任务生成 + 状态回写」收敛为单事务提交；
2. 落地前先补一个 `FlowConsistencyJobHandler` 对账任务（扫描「实例 RUNNING 但无 PENDING 任务」/「任务存在但实例已完成」等异常态并告警/自愈）。

### 1.3 多租户隔离依赖全局拦截器 + 手写过滤不一致

**发现**：核心查询（`selectByInstanceId` / `selectTodoByAssignee` / `selectPendingByInstance` / `countPendingByNode` 等）**均无 tenant_id 过滤**，依赖 `ydsz-common-tenant` 的 `TenantIsolationInterceptor` 自动注入；但部分查询（`selectOverdue` / `selectOverdueTopN` / `selectWorkloadByAssignee`）又手写了 `<if test="tenantId != null">`。两者混用，一旦拦截器对某条自定义 SQL 失效（如原生 `EXTRACT(EPOCH...)` 语句），即出现跨租户串数。

**建议**：统一租户策略——要么全部交给拦截器（移除手写 `<if>`），要么在 infra 层显式声明 `@InterceptorIgnore(tenantLine = "false")` 的例外清单并评审；核心业务表补 tenant_id 联合索引。

---

## 2. 🟡 P1 架构债

### 2.1 ext JSON 承载结构化数据（最大架构债）

`FlowSkip` 表无 `source_node_code` / `sequence_flow_id` 列，`FlowNode` 无 `default_flow_id` / `join_required` / `priority` / `perform_type` 列，全部塞进 `ext` JSON 字符串。导致：

- **逻辑三处重复**：`extractSourceRef` 在 `FlowDefinitionCacheService` / `FlowGraphValidator` / `DefaultFlowAdvancer(extractSourceNodeCode)` 各自实现一份；
- **热路径反复 parse**：每次推进都要 `YdszJson.parseMap(node.ext)`、`parseMap(skip.ext)` 多次（`resolveDefaultSkip` / `parseJoinRequired` / `extractSequenceFlowId` / `extractSourceNodeCode`）；
- **无法建索引 / 无法 SQL 过滤 / 类型不安全**。

**建议**：为 `flow_skip` 增加 `source_node_code`、`sequence_flow_id` 列，为 `flow_node` 增加 `default_flow_id`、`join_required` 等高频结构化列；`ext` 仅保留真正的自由扩展项。同步收敛 `extractSourceRef` 到单一工具类。

### 2.2 God Class 三巨头

| 类 | 行数 | 职责 |
|---|---|---|
| `FlowDefinitionServiceImpl` | 2215 | 部署/发布/版本/迁移/设计器/表单/SLA/锁定/回滚…40+ 方法 |
| `FlowInstanceServiceImpl` | 1894 | 启动/终止/挂起/召回/回滚/模拟/重提/批量… |
| `FlowTaskCreateService` | 1611 | 任务创建/办理人解析/会签/边界定时… |

**建议**：按「命令」拆分为 `DefinitionDeployService` / `DefinitionVersionService` / `DefinitionDesignerService` / `InstanceLifecycleService` / `InstanceMigrateService` 等；`FlowTaskCreateService` 拆出 `AssigneeResolutionService`（办理人解析已较独立，可先抽）。

### 2.3 双表达式引擎 + 三层 fallback

条件求值链路：`DMN → FlowRoutingService(Aviator) → DefaultFlowVariableStrategy(Aviator→自研正则)`，而 `FlowConditionExprServiceImpl` 里还维护一套 **SpEL 映射**（`OPERATOR_MAP` 的 `T(String)...` 类型引用）但 `previewExpression` 对 SpEL 明确返回「暂未实现」。注释自述要「避免双引擎并存」，实际却三引擎并存 + 一个未接线的第四引擎。

**建议**：定版为「Aviator 唯一引擎」，删除未接线的 SpEL 映射分支；DMN 作为独立前置路由保留；自研正则仅作 Aviator 不可用时的降级，并加「降级告警」指标。

### 2.4 命名污染：`ydsznder` / `lastYdszndedAt`

「催办 reminder/urge」相关字段被全局误替换为无意义串 `ydsznder`，共 **49 处**，且**泄漏到 DB 列名**（`ydsznder_count` / `last_ydsznded_at`）与 API/JSON 字段（`ydsznderCount`）。

**建议**：重命名为 `urgeCount` / `lastUrgedAt` / `incrementUrgeCount`；若 DB 已上线，通过 `@TableField("ydsznder_count")` 映射 + 迁移脚本平滑过渡，避免前端继续对接污染字段。

### 2.5 文档与实现不符

`FlowJoinTokenServiceImpl` 类 Javadoc 写的是「加签 Token（加签/减签/转签）」，实际实现是「并行网关 join 令牌」——复制粘贴遗留，误导维护者。

---

## 3. 🟢 P2 性能

| # | 位置 | 问题 | 建议 |
|---|---|---|---|
| 1 | `FlowDefinitionCacheService.evictLocal` | 对三个缓存的 `keySet` 做 `endsWith` 全量扫描（O(N)） | 用 `definitionId → Set<key>` 反向索引，或按租户分桶 |
| 2 | `getNodeByCode` / `getStartNode` / `getSkipsByNextNode` | 每次 `stream().filter()` 全量遍历（O(N)） | 建 `nodeCode→node`、`nextNodeCode→skips` 反向索引（出边已优化，入边/节点未优化） |
| 3 | `countActiveIncomingTasks`（join 降级路径） | 每条入边一次 `countPendingByNode`，N 次 DB 查询 | 合并为 `WHERE node_code IN (...)` 单条 GROUP BY |
| 4 | `selectTodoByAssigneePage` | `LIMIT/OFFSET` 深分页性能差 | keyset/游标分页，或待办量大时走 ES（`WorkflowSearchProvider` 已预留） |
| 5 | 线程池 `flowQueue` core=2/max=8 | 事件发布吞吐上限低 | 评估虚拟线程（`BoundedVirtualThreadScheduler` 语义），按实例量调参 |
| 6 | 部署/推进路径 `ext` 多次 parse+serialize | 见 §2.1 | 结构化列落地后自然消除 |

---

## 4. 功能增强（对标竞品差距）

| 能力 | 现状 | 对标差距 / 建议 |
|---|---|---|
| 事件网关 / 复杂网关 | `eventBasedGateway`→CONDITION、`complexGateway`→INCLUSIVE 近似 + `ext.gatewayType` 标记 | 语义未真正实现；建议明确「不支持」或补齐（P2） |
| 定时器边界事件 | 仅 `ext.timer` 标记 + `scheduleBoundaryTimerIfPresent` | 校验「超时自动 PASS/REJECT/升级」是否闭环，补超时触发测试 |
| 消息/信号事件 | `signalEventDefinition` 仅存 `signalRef` | 未实现信号广播/订阅；若定位不覆盖应显式下线避免误导 |
| CEP（时间窗口/序列模式） | 无 | 已知短板，建议作为 literule 侧能力而非 workflow 内实现 |
| 一致性对账 | 无（见 §1.2） | P0 补 `FlowConsistencyJobHandler` |
| 委托/代理/转签 | 有 `FlowDelegateAuthService` / `FlowTaskTransferService` | 建议对齐钉钉/飞书「离职自动交接」链路（`FlowAssigneeLeaveHandler` 已存在，验证闭环） |
| 多租户物理隔离 | 仅逻辑隔离（`TenantIsolationInterceptor`） | 已知短板，评估 ISOLATE_DB 分库方案 |
| 回放/追踪 | `BpmnModel` 已存 BPMNDI 坐标 | 建议补「流程实例时间线 + 节点耗时」可视化（`FlowMonitorDashboardController` 已有雏形） |

---

## 5. 体验改善

1. **错误码可读性**：`error.workflow.msg_67a10717` 这类哈希串（多处）排查困难，建议附人类可读描述或收敛到 `WorkflowExceptionCode` 枚举统一管理。
2. **字段命名**：`ydsznderCount`（§2.4）污染 API，前端对接体验差。
3. **API 面收敛**：36 个 Controller，建议按「定义 / 实例 / 任务 / 治理」四个域做 OpenAPI tag 分组，统一分页/幂等/审计约定（`batchStartInstances` 已做 self 代理事务示范，可推广）。
4. **Javadoc 对齐实现**：清理复制粘贴遗留（§2.5）。

---

## 6. 过度设计评估（需以采用率佐证）

以下能力**实现完整但需确认实际采用率**，若长期零采用则属过度设计，建议季度盘点：

| 能力 | 评估 |
|---|---|
| 三层条件求值 + SpEL 未接线 | 明显过度，收敛为 Aviator 单引擎 |
| `FlowInstanceMergeService`（实例合并） | 场景罕见、复杂度高，无采用证据建议标注 @Deprecated 候选 |
| `FlowTemplateRecommendService` 智能推荐 | 依赖 AI/规则，若未接 LLM 则价值存疑 |
| `FlowCanaryService` 灰度发布 | 中小团队价值有限，但保留合理 |
| `FlowI18nService` 独立 i18n | 若仅中文场景可后置 |

> 参考 `ydsz-common-util` 审查结论：**「零采用」不等于「质量差」**。此处用「采用率」而非「行数」作为下线/保留判据。

---

## 7. 测试缺口（贯穿所有维度）

**全模块 0 个测试文件**（32,390 行 / 306 类）。对一个含状态机、网关路由、6 种会签策略、DMN、join 令牌、SLA、灰度的工作流引擎而言，这是最大风险。对标竞品（Flowable/Camunda）均有数万级测试用例。

**建议（P0，最高杠杆）**：
1. 先补**纯函数层单测**：`FlowGraphValidator`（图校验）、`FlowConditionExprServiceImpl.buildExpression`、`DefaultFlowVariableStrategy`（正则/比较语义）、`BpmnXmlParser`（含 XXE 用例）；
2. 再补**策略层**：6 种会签策略的通过率/加权/顺序语义；
3. 最后补**集成**：`Testcontainers`（PG+Redis）验证 `DefaultFlowAdvancer` 的 start/advance/join/退回全链路，覆盖 §1.1、§1.2 两个 P0 bug。

---

## 8. 优先级路线图

### P0（本迭代，正确性收口）
| # | 事项 | 来源 |
|---|---|---|
| 1 | 修复 `resolveRejectTarget` 退回目标解析（改用 sourceRef） | §1.1 |
| 2 | 推进事务原子化（命令模式单事务）或先补对账任务 | §1.2 |
| 3 | 补齐引擎核心单测 + 集成测试（验证上述两 bug） | §7 |
| 4 | `ydsznder` 命名污染清理 | §2.4 |

### P1（下迭代，架构债）
| # | 事项 | 来源 |
|---|---|---|
| 1 | `flow_skip` 增 `source_node_code`/`sequence_flow_id` 列，收敛 `extractSourceRef` | §2.1 |
| 2 | 拆 `FlowDefinitionServiceImpl` / `FlowInstanceServiceImpl` / `FlowTaskCreateService` | §2.2 |
| 3 | 收敛表达式引擎为 Aviator 单引擎，移除 SpEL 未接线分支 | §2.3 |
| 4 | 统一多租户过滤策略 + tenant_id 联合索引 | §1.3 |

### P2（长期治理）
| # | 事项 | 来源 |
|---|---|---|
| 1 | 缓存反向索引（evictLocal / getNodeByCode / getSkipsByNextNode） | §3 |
| 2 | join 降级路径 N 次查询合并为单条 IN 查询 | §3 |
| 3 | 待办分页 keyset 化 / ES 化 | §3 |
| 4 | 事件/复杂网关语义补全或显式下线 | §4 |
| 5 | 错误码枚举化 + API 面收敛 | §5 |
| 6 | 零采用能力季度盘点（实例合并/智能推荐/i18n） | §6 |

---

## 9. 关键证据位置

| 发现 | 文件位置 |
|---|---|
| 退回目标解析 bug | `engine/impl/DefaultFlowAdvancer.java#resolveRejectTarget`（540-550） |
| 推进非原子（注释自承） | `engine/impl/DefaultFlowAdvancer.java#start`（107-119） |
| 对账任务缺失 | `scheduler/`、`job/` 目录仅 4 类，无 reconcile |
| ext 承载 sourceRef | `engine/BpmnXmlParser.java#parseSkip`（588-627） |
| extractSourceRef 三处重复 | `FlowDefinitionCacheService`(286) / `FlowGraphValidator`(203) / `DefaultFlowAdvancer`(627) |
| evictLocal 全量扫描 | `engine/FlowDefinitionCacheService.java#evictLocal`（130-145） |
| ydsznder 命名污染 | `FlowRunTaskMapper.xml`(40-41/292/324-327) 等 49 处 |
| join 令牌 Javadoc 错误 | `service/impl/instance/FlowJoinTokenServiceImpl.java`（18-28） |
| 事件/复杂网关近似 | `engine/BpmnXmlParser.java#parseNode`（253-260）、`mapNodeType`（657-658） |
| 零测试 | `find . -path "*/src/test/*"` 结果为空 |

---

## 10. 总结

ydsz-workflow 的**引擎骨架质量中上**：BPMN 解析已做 XXE 防护、并行网关 join 用 Lua 原子化、缓存做了 sourceRef 索引与集群广播，这些都是对标 Flowable/Camunda 的正确工程意识。

但**正确性（退回解析 bug、推进非原子且无对账）、架构（ext 承载结构化数据、God Class、双引擎）、质量（零测试）**三个层面存在系统性缺口，其中「零测试」是放大其它一切风险的根本原因。

建议按 P0 → P1 → P2 顺序推进：先以「补测试 + 修退回 bug + 补对账/事务原子化」收口正确性，再以「结构化列 + 拆类 + 引擎收敛」清架构债，最后做性能与采用率治理。这与 `ydsz-common-util` 上一轮审查的治理节奏一致，可复用同一套「证据位置 + 验证方式」的落地模板。
