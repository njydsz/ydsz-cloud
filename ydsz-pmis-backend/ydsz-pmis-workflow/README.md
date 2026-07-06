<!--
================================================================================
YDSZ PMIS · ydsz-pmis-workflow 模块说明
--------------------------------------------------------------------------------
本文件是 `ydsz-pmis-workflow` 后端模块的入口文档，向模块维护者 / Code Reviewer
解释：
  1. 模块是什么、对外暴露什么能力
  2. 模块不做哪些事情（硬约束清单）
  3. 关键设计决策与理由
  4. 变更流程

任何针对本模块的 PR 提交前请先通读本文档。
================================================================================
-->

# ydsz-pmis-workflow · 自研工作流 v2

> 自研 `pmis_flow_*` 表 + BPMN 2.0 解析 + 流程设计器 + 审批中心 + 流程监控
>
> **模块状态**：稳定运行 · 批次 22-23 持续演进
> **最后更新**：2026-07-06

---

## 一、模块定位

`ydsz-pmis-workflow` 是 PMIS 项目运营管理系统的**审批流引擎**，关注「**审批流转**」本身，不承载「签署生效」「法律证据」「移动端交互」等正交职责。

| 职责 | 说明 |
|---|---|
| 流程定义 | BPMN 2.0 XML 解析、节点/连线/网关建模、版本管理 |
| 流程设计器 | 数据 API（`FlowDesignerController`）+ 表单设计器 + 表达式编辑器 |
| 流程实例 | 启动/推进/跳转/驳回/转办/委派/加签/减签/催办 |
| 流程模板 | 预置模板 + 业务方引用 |
| 流程通知 | 多通道（站内 / 邮件 / IM 钉钉/企微/飞书） |
| 流程监控 | 实时仪表盘 + SLA + 50 步模拟运行 |
| 第三方审批 | 企业微信/钉钉/飞书审批动作回调（IM 通道，非签章） |
| 嵌入式审批 | 业务页面内嵌审批面板（PC 端） |

**不承载**的职责见第三节「硬约束清单」。

---

## 二、技术栈与依赖

| 维度 | 选型 |
|---|---|
| Spring Boot / Cloud / SCA | 4.0.7 / 2025.1.2 / 2025.1.0.0 |
| BPMN 解析 | 自研 `BpmnXmlParser`（`engine/BpmnXmlParser.java`），兼容 BPMN 2.0 标准 |
| DMN 决策 | 自研 `DmnEngine`（`dmn/DmnEngine.java`），Aviator 表达式 |
| 表达式 | Aviator（与 literule 共享） |
| 路由 | `DefaultFlowAssigneeResolver` + `FeignFlowAssigneeResolver` + `NameAssembler` |
| 缓存 | Caffeine（流程定义元数据）+ Redis（分布式锁 / 幂等） |
| 消息 | RocketMQ 5.x（`FlowNotifyOutboxListener` 事件外发） |
| 数据库 | PostgreSQL 18（`pmis_flow_*` 物理表共 26 张，详见 `deploy/sql/V1.0.0.sql`） |

依赖关系：

```text
ydsz-pmis-workflow
  ├─ ydsz-pmis-common（基础组件库）
  ├─ ydsz-pmis-system (Feign：文件 / 通知)
  ├─ ydsz-pmis-project (optional，DMN 决策表)
  └─ ydsz-pmis-literule (optional，规则链路由)
```

---

## 三、硬约束清单（不会做）

> 本节列出本模块**永远不会**承担的职责。Code Review 遇到违反项必须拦截。

### 3.1 ❌ 永远不适配移动端 / 独立 H5

**适用范围**：

- ❌ 原生 iOS / Android App
- ❌ uni-app / Taro 移动端
- ❌ 独立的移动 H5 子应用
- ❌ PWA 移动模式

**替代方案**（已在 `thirdparty/` 实现）：

- ✅ 企业微信 / 钉钉 / 飞书 IM 通道审批（`WeComSignatureUtil` / `DingTalkSignatureUtil` / `FeishuSignatureUtil`）
- ✅ 独立的「轻审批 H5」应用（仅查询/同意/驳回，不含设计器/监控）

**理由**（详见仓库根 README §7.4）：

1. 流程设计器强依赖桌面交互（bpmn-js 拖拽、属性面板）；
2. 流程监控信息密度高，移动屏无法承载；
3. 业务定位是 B 端内部 PC 工具，IM 通道已完整覆盖移动端需求；
4. 强制边界可防止适配层兼容反复消耗研发资源。

**实施约束**：

- 不得引入 `vant` / `uni-ui` / `taro-ui` 等移动端 UI 库；
- API 默认返回 PC 端字段结构（完整流程图 JSON、表单 Schema、审批历史）；
- PR 标题 / 描述含「workflow mobile」「workflow h5」等关键词直接拒绝。

---

### 3.2 ❌ 永远不集成电子签章（e-sign）

**自 2026-07-06 起明确**：本模块**永远不会**集成电子签章能力。

**不集成的范围**（不限于）：

| 维度 | 不集成的内容 |
|---|---|
| ❌ 第三方 SaaS | e签宝 / 法大大 / 上上签 / 契约锁 / DocuSign / Adobe Sign 等 OpenAPI / SDK |
| ❌ 私有化签章 | 私有化电子签章服务器、签章前置机、SM2/RSA 数字证书、PDF/OFD 签章后处理 |
| ❌ 基础设施 | 时间戳服务（TSA）、CA 认证网关、电子证据保全、司法存证 |
| ❌ 数据对象 | 电子合同原文存证、签署轨迹哈希、签章图片、证书链、骑缝章等 |

**理由**：

1. **业务定位决定**：本系统关注「审批流转」，电子签章关注「签署生效」与法律效力。两者职责正交，合并会污染领域模型（流程节点与签署节点语义不同：审批是「同意/驳回」，签署是「数字签名 + 证书链 + 不可抵赖」）。
2. **合规与法律风险**：电子签章涉及 CA 认证、密评、等保三级、《电子签名法》《合同法》合规审计、证据链保全。集成到自研工作流引擎会引入不可控的法律责任（一旦签署无效需由系统方举证）。
3. **避免厂商锁定**：电子签章 SaaS 普遍采用年度授权 + 证书计费 + 私有化部署差异，自研引擎不应承担这部分采购与运维成本。
4. **解耦架构**：签章是合同生命周期的一环，应在「合同管理」（`ydsz-pmis-project` 模块的 `ContractDO` 链路）独立抽象，由合同服务对接电子签章平台，本工作流引擎仅作为「审批节点触发方」。

**实施约束**（Code Review 必查）：

- 后端本模块不得新增 `ElectronicSign*` / `Esign*` / `SignatureCert*` / `PdfSeal*` / `ContractSign*` 等 Controller / Service / Entity / Mapper；
- `pom.xml` 不得引入任何电子签章相关依赖（如 `esign-sdk` / `fadada-sdk` / 扩展签章的 `bouncycastle-*` / `itextpdf` 签章模块等）；
- 前端工作流相关页面 / 组件不得引入签章相关组件库（如 `vue-esign` / `pdf-lib` 签章插件 / `signature_pad` 在工作流场景的复用等）；
- 权限码（`PermissionCodes`）不得增加 `esign:*` / `contract.sign:*` / `workflow:esign:*` 等命名空间；
- SQL 脚本（`deploy/sql/V*.sql`）不得新增 `pmis_sign_*` / `pmis_cert_*` / `pmis_contract_sign_*` 表；
- 如业务侧确有签署需求，须在 `ydsz-pmis-project` 的合同服务通过「外部跳转 / Webhook 回调」方式对接独立电子签章服务，本工作流引擎仅传递 `contractId` + `signStatus` 等轻量状态字段，不持有签署原文或证书数据。

**未来扩展点（不包含在本约束内）**：

- 合同服务（`ydsz-pmis-project`）可按需集成电子签章能力，但必须走独立 RFC + 法务/合规评审，不允许直接绕过本约束。
- 若后续评估结果为「工作流引擎亦需支持签章节点」，需发起 RFC 修订本约束并经架构委员会评审。

---

### 3.3 ❌ 不引入 Flyway / Liquibase 等自动 schema 迁移框架

由项目级硬约束继承：见 `project_memory.md` 顶层约束。

---

### 3.4 ❌ 不绕过 NameAssembler 进行跨服务名称解析

由项目级硬约束继承：见 `project_memory.md` 顶层约束。

---

## 四、关键设计决策

### 4.1 自研 BPMN 解析而非引入 Flowable / Camunda

- **理由**：BPMN 2.0 标准覆盖范围广，自研引擎只实现必要子集（UserTask / ServiceTask / ExclusiveGateway / ParallelGateway / StartEvent / EndEvent），减少 30+ 张历史表与庞大依赖。
- **代价**：复杂子流程 / 定时器边界 / 多实例任务等高级特性需手动实现（当前已实现子流程 + 定时器 + 加签减签）。
- **可逆性**：若未来需支持完整 BPMN 规范，可平滑切换到 Flowable（`BpmnModel` 抽象层已隔离）。

### 4.2 流程定义元数据本地缓存（Caffeine）

- 详见 `engine/FlowDefinitionCacheService.java`。
- TTL 默认 5 分钟，eviction LRU 10000 条。
- 流程部署 / 更新 / 删除时通过 Redis Pub/Sub 广播失效事件。

### 4.3 事件外发 + 异步持久化

- `engine/FlowEventListener` 在流程关键节点发布 `FlowWorkflowEvent`；
- `listener/FlowNotifyOutboxListener` 监听事件后通过 RocketMQ 外发；
- `scheduler/NotifyOutboxScanner` 定时扫描 `pmis_flow_notify_outbox` 表确保 at-least-once。

### 4.4 审批人自动去重

- `engine/impl/DefaultFlowAdvancer.java` 中 `assignee` Set 去重，避免同一审批人被并行重复通知。

### 4.5 50 步模拟运行

- `FlowDefinitionService#simulate` 支持在不持久化实例的前提下模拟流程推进，最多 50 步。
- 用于流程设计阶段的回归测试与培训演示。

---

## 五、API 概览

| Controller | 路径前缀 | 职责 |
|---|---|---|
| `FlowDefinitionController` | `/api/workflow/definition` | 流程定义 CRUD + 部署 + 模拟 |
| `FlowInstanceController` | `/api/workflow/instance` | 流程实例启动/查询/变量 |
| `FlowTaskController` | `/api/workflow/task` | 待办/已办/审批动作（同意/驳回/转办/委派/加签/催办） |
| `FlowDesignerController` | `/api/workflow/designer` | 设计器数据 API（PC 端 bpmn-js 适配） |
| `FlowTemplateController` | `/api/workflow/template` | 流程模板管理 |
| `FlowMonitorController` | `/api/workflow/monitor` | 流程监控仪表盘 |
| `FlowSlaController` | `/api/workflow/sla` | SLA 规则与超时处理 |
| `FlowCcController` | `/api/workflow/cc` | 抄送管理 |
| `FlowDelegateController` | `/api/workflow/delegate` | 委托授权 |
| `FlowCanaryController` | `/api/workflow/canary` | 金丝雀发布 |
| `FlowDmnController` | `/api/workflow/dmn` | DMN 决策表 |
| `FlowAiGenerateController` | `/api/workflow/ai/generate` | AI 草稿（流程骨架/审批意见） |
| `FlowAiAssistController` | `/api/workflow/ai/assist` | AI 智能推荐审批人 |
| `FlowAutoTriggerController` | `/api/workflow/auto-trigger` | 业务事件自动触发流程 |
| `FlowEventController` | `/api/workflow/event` | 事件订阅/发布 |
| `FlowHistoryArchiveController` | `/api/workflow/history-archive` | 历史归档 |
| `FlowInstanceMigrationController` | `/api/workflow/instance-migration` | 实例迁移（流程定义升级） |
| `FlowEmbeddedApprovalController` | `/api/workflow/embedded` | 业务页嵌入式审批 |
| `FlowThirdPartyApprovalController` | `/api/workflow/thirdparty` | 企微/钉钉/飞书审批动作回调 |

> ⚠️ **再次强调**：上述 API 严禁新增电子签章相关端点（如 `/api/workflow/sign` `/api/workflow/contract-sign` 等）。

---

## 六、关键数据表

| 表名 | 说明 |
|---|---|
| `pmis_flow_definition` | 流程定义（含 BPMN XML） |
| `pmis_flow_instance` | 流程实例 |
| `pmis_flow_his_instance` | 流程历史实例（归档前） |
| `pmis_flow_run_task` | 待办任务运行态 |
| `pmis_flow_his_task` | 已办任务 |
| `pmis_flow_node` | 节点配置（审批人/表单/SLA） |
| `pmis_flow_skip` | 跳转规则 |
| `pmis_flow_cc` | 抄送 |
| `pmis_flow_template` | 流程模板 |
| `pmis_flow_delegate_auth` | 委托授权 |
| `pmis_flow_delegate_log` | 委托日志 |
| `pmis_flow_audit_log` | 审计日志 |
| `pmis_flow_task_comment` | 任务评论/意见 |
| `pmis_flow_user` | 流程用户映射 |
| `pmis_flow_dmn_table` | DMN 决策表 |
| `pmis_flow_auto_trigger` | 自动触发规则 |
| `pmis_flow_event_subscription` | 事件订阅 |
| `pmis_flow_notify_outbox` | 工作流通知外发箱（Outbox Pattern，事件 PENDING/SENT/DEAD） |
| `pmis_flow_notify_channel` | 通知渠道配置 |
| `pmis_flow_thirdparty_account` | 第三方平台账号 |
| `pmis_flow_thirdparty_log` | 第三方平台交互日志 |
| `pmis_flow_his_variable` | 历史变量 |
| `pmis_flow_timer` | 定时器（含边界事件） |

> ⚠️ **禁止新增** `pmis_sign_*` / `pmis_cert_*` / `pmis_contract_sign_*` 等签章相关表。

---

## 七、测试现状

| 维度 | 数量 | 备注 |
|---|---|---|
| 测试类 | 30+ | 覆盖 Controller / Service / Engine / Facade / Job / 第三方 |
| 覆盖率 | 100% 单测覆盖目标 | 批次 18-19 已完成 |
| 命令 | `mvn -pl ydsz-pmis-backend/ydsz-pmis-workflow -am test` | |

关键测试类：

- `BpmnXmlParserTest` — BPMN 解析
- `DefaultFlowAdvancerTest` — 流程推进
- `FlowTaskServiceImplTest` / `FlowTaskCompleteServiceImplTest` / `FlowTaskSignServiceImplTest` — 任务核心（注意：此处 `Sign` 指「会签/或签」业务节点，非「签章」）
- `FlowInstanceServiceImplTest` / `FlowInstanceServiceImplRollbackTest` — 实例生命周期与回滚
- `PmisWorkflowFacadeTest` — Facade 聚合
- `DmnEngineTest` — DMN 决策
- `DingTalkSignatureUtilTest` / `FeishuSignatureUtilTest` / `WeComSignatureUtilTest` — 第三方 IM 通道签名工具（与电子签章无关）

---

## 八、变更流程

1. **功能新增**：先开 RFC，列明对硬约束清单（第三节）的影响，提交架构组评审；
2. **Bug 修复**：直接修复 + 补充单测，无需 RFC；
3. **依赖变更**：`pom.xml` 新增依赖需在 PR 描述中说明用途与许可证；电子签章相关依赖**一律拒绝**；
4. **SQL 变更**：遵循项目级 `deploy/sql/V*.sql` 版本化规范，不得新增签章相关表；
5. **前端联动**：在 `ydsz-pmis-frontend/src/views/workflow/**` 修改，需同步更新前端 PR。

---

## 九、相关文档

- 仓库根 [README.md §7.4 平台适配范围（PC-only）](../../README.md#七四-平台适配范围团队共识--硬约束)
- 仓库根 [README.md §7.5 电子签章能力范围（不会做）](../../README.md#七五-电子签章能力范围团队共识--硬约束)
- 项目记忆 [project_memory.md](file:///c:/Users/Marvin/.trae-cn/memory/projects/-d-Code-ydsz-ydsz-pmis/project_memory.md)
- 部署手册 [deploy/README.md](../../deploy/README.md)
- 数据库初始化 [deploy/sql/V1.0.0.sql](../../deploy/sql/V1.0.0.sql)

---

> 本文件由 PMIS 团队维护（v1.0_2026-07-06）。
> 任何变更请走 PR + Code Review 流程。
