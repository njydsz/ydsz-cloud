# ydsz-pmis-workflow

> 自研工作流引擎（PMIS-Flow v2 + BPMN 2.0）

## 模块定位

| 属性 | 值 |
|---|---|
| **类型** | 部署单元（独立启动） |
| **端口** | **9006**（按构建顺序 7/8） |
| **服务名** | `ydsz-pmis-workflow` |
| **构建顺序** | 7/8 |
| **数据库** | PostgreSQL（`pmis_flow_*` 表） |
| **依赖** | Nacos、PostgreSQL、Redis |
| **平台** | ⚠️ **仅 PC Web 端**（不支持移动端 / 独立 H5） |

## 核心职责

本模块是 PMIS 的**自研工作流引擎**，提供流程定义、审批、监控完整链路。

### 1. 核心能力

| 能力 | 说明 |
|---|---|
| **流程定义** | PMIS-Flow XML / JSON / BPMN 2.0 解析 |
| **节点类型** | 开始 / 审批 / 加签 / 减签 / 转交 / 抄送 / 委派 / 代理 |
| **分支规则** | 条件分支 / 默认分支 / 跳过规则 |
| **定时器** | 节点超时 / 流程超时（cron 表达式） |
| **事件订阅** | 流程开始 / 结束 / 节点进入 / 离开事件 |
| **SLA 提醒** | P1-P4 SLA 倒计时 + 飞书/钉钉告警 |
| **审批人去重** | 同一审批人在多级审批中自动合并 |
| **流程模板** | 模板库 + 版本管理 + 复制/导入/导出 |
| **灰度发布** | 流程模板 canary 发布 |
| **流程监控** | 实例状态 / 节点耗时 / 拥堵分析 |
| **50 步模拟** | 流程图模拟运行（不实际执行任务） |
| **设计器** | bpmn-js 拖拽设计 + 表单设计 + 表达式编辑 |

### 2. 关键 Controller

| 路径前缀 | 作用 |
|---|---|
| `/flow/definition` | 流程定义（增删改查 + 部署） |
| `/flow/instance` | 流程实例（发起 / 审批 / 驳回） |
| `/flow/task` | 审批任务（待办 / 已办） |
| `/flow/template` | 流程模板 |
| `/flow/dmn` | DMN 决策表 |
| `/flow/form` | 表单设计 |
| `/flow/delegation` | 委托授权 |
| `/flow/history` | 历史实例 |
| `/flow/instance-migration` | 实例迁移 |
| `/flow/canary` | 灰度发布 |
| `/flow/monitor` | 流程监控 |
| `/flow/sla` | SLA 规则 |
| `/flow/simulator` | 流程模拟器 |

## 数据库表设计

本模块在 `deploy/sql/V1.0.0.sql` 中持有 **34 张表**，覆盖流程定义/实例/任务/历史/通知/委托/评论/审批/审计/AI 反馈/触发器。

| 业务域 | 表名 | 说明 |
|---|---|---|
| **流程定义** | `pmis_flow_definition` | 流程定义主表（PMIS-Flow XML/JSON/BPMN 2.0） |
| | `pmis_flow_template` | 流程模板（版本管理 + 复制/导入/导出） |
| | `pmis_flow_category` | 流程分类（树形） |
| | `pmis_flow_node` | 流程节点（审批/加签/转交/抄送） |
| | `pmis_flow_skip` | 跳过规则 |
| | `pmis_flow_timer` | 节点/流程超时定时器（cron） |
| | `pmis_flow_dmn_table` | DMN 决策表 |
| **实例** | `pmis_flow_instance` | 流程实例（运行中） |
| | `pmis_flow_his_instance` | 历史实例（已结束） |
| **任务** | `pmis_flow_run_task` | 待办/运行任务 |
| | `pmis_flow_his_task` | 已办/历史任务 |
| | `pmis_flow_his_variable` | 历史变量快照 |
| **审批/评论** | `pmis_flow_comment` | 审批意见 |
| | `pmis_flow_task_comment` | 任务评论 |
| | `pmis_flow_quick_comment` | 常用意见 |
| **抄送/委派** | `pmis_flow_cc` | 抄送记录 |
| | `pmis_flow_cc_rule` | 抄送规则 |
| | `pmis_flow_delegate_auth` | 委托授权（代理人/时间窗） |
| | `pmis_flow_delegate_log` | 委托日志 |
| | `pmis_flow_delegate_message` | 委托消息（IM 通知） |
| **附件** | `pmis_flow_attachment` | 流程附件 |
| **审计日志** | `pmis_flow_audit_log` | 流程审计（按月分区） |
| | `pmis_flow_audit_log_default` | 审计默认分区 |
| **事件/触发** | `pmis_flow_event_subscription` | 事件订阅（开始/结束/节点进出） |
| | `pmis_flow_auto_trigger` | 自动触发器（业务事件→发起流程） |
| **通知** | `pmis_flow_notify_channel` | 通知渠道（飞书/钉钉/邮件） |
| | `pmis_flow_notify_outbox` | 通知发件箱（异步发送） |
| | `pmis_flow_notify_preference` | 通知偏好 |
| | `pmis_flow_notify_template` | 通知模板 |
| **第三方** | `pmis_flow_third_party_account` | 第三方账号（企业微信/钉钉/飞书） |
| | `pmis_flow_third_party_log` | 第三方交互日志 |
| **AI 增强** | `pmis_flow_ai_feedback` | AI 辅助审批反馈（用于学习） |
| **用户** | `pmis_flow_user` | 流程用户（含离职/兼职） |
| **Webhook** | `pmis_flow_webhook_subscription` | Webhook 订阅（流程事件回调） |

> **索引关键点**：
> - `pmis_flow_definition(template_key, version)` 唯一（最新版查找）
> - `pmis_flow_instance(definition_id, status)` 监控
> - `pmis_flow_run_task(assignee, status, due_date)` 待办列表 + SLA
> - `pmis_flow_his_instance(definition_id, end_time)` 历史归档
> - `pmis_flow_cc(recipient_id, read_flag)` 抄送分页
> - `pmis_flow_event_subscription(event_type, listener)` 事件分发
>
> **分区说明**：`pmis_flow_audit_log` 为 PostgreSQL 范围分区表（按月分区），历史月份可走 `pg_partman` 归档。

## ⚠️ 平台适配硬约束

> 自 2026-07-06 起明确：**本模块永远不适配移动端 App 与独立 H5 应用**。

| 维度 | 范围 |
|---|---|
| ✅ 支持 | PC Web（`ydsz-pmis-frontend`，Vue 3.5 + Element Plus，桌面浏览器 ≥ 1280px） |
| ❌ 不支持 | 原生 iOS/Android App、uni-app / Taro 移动端、独立的移动 H5 子应用、PWA 移动模式 |
| 🔁 移动端审批替代方案 | ① 对接企业微信 / 钉钉 / 飞书（已实现 `WeComSignatureUtil` / `DingTalkSignatureUtil` / `FeishuSignatureUtil`）；② 独立「轻审批 H5」应用 |

**为什么不做移动端适配**：
1. 流程设计器（bpmn-js）强依赖桌面交互
2. 流程监控 / 模拟运行信息密度高，移动端无法保证可用性
3. 业务定位（B 端内部工具）天然服务 PC 场景
4. 移动端需求已通过 IM 审批通道完整覆盖

**实施约束**：
- 前端严禁引入 `vant` / `uni-ui` / `taro-ui`
- 后端 API 默认响应 PC 端字段结构，不为移动端裁剪
- 任何 PR 不得引入工作流模块移动端适配相关代码

## ⚠️ 电子签章硬约束

> **本模块永远不会集成电子签章（e-sign）能力**。详见根 README 7.5。

## 启动顺序

依赖 `common` + `nacos`，**应在 `userinfo` 之后**启动（通过 Feign 获取审批人信息）。

## 目录结构

```
ydsz-pmis-workflow/
├── pom.xml
├── README.md
└── src/main/
    ├── java/com/njydsz/pmis/workflow/
    │   ├── WorkflowApplication.java
    │   ├── controller/
    │   ├── service/
    │   │   ├── FlowDefinitionService.java
    │   │   ├── FlowInstanceService.java
    │   │   ├── FlowTaskService.java
    │   │   ├── BpmnParserService.java
    │   │   ├── FlowSimulatorService.java
    │   │   └── SlaMonitorService.java
    │   ├── engine/            # 流程引擎核心
    │   │   ├── FlowEngine.java
    │   │   ├── NodeExecutor.java
    │   │   ├── BpmnParser.java
    │   │   └── EventDispatcher.java
    │   ├── designer/          # 设计器数据 API
    │   ├── mapper/ / entity/ / enums/
    │   └── config/
    ├── resources/
    │   ├── bootstrap.yml
    │   ├── mapper/            # classpath*:mapper/flow/**/*.xml
    │   └── config/            # 原 nacos-config（已重命名）
    │       ├── ydsz-pmis-workflow-dev.yaml
    │       ├── ydsz-pmis-workflow-sit.yaml
    │       └── ydsz-pmis-workflow-uat.yaml
    └── test/
```

## 配置文件

标准 `DB_*` / `REDIS_*` / `NACOS_*` 环境变量。无特殊配置。

## 启动

```bash
cd ydsz-pmis-backend
mvn -pl ydsz-pmis-common -am install -DskipTests
mvn -pl ydsz-pmis-workflow spring-boot:run
```

## 测试

```bash
mvn -pl ydsz-pmis-workflow -am test
```

测试覆盖：
- `BpmnParserTest` BPMN 2.0 解析
- `FlowInstanceTest` 流程实例生命周期
- `FlowSimulatorTest` 50 步模拟
- `SlaMonitorTest` SLA 计算
- `FormEngineTest` 表单引擎

## Feign 接口

### 被调用

- `InitiationFeignClient`（位于 common）→ 触发立项审批
- 业务服务（project / userinfo）→ 通过 `FeignClient` 启动流程

## 常见问题

### Q1：流程发起失败 "找不到节点审批人"

审批人为动态解析时，配置的 `assigneeResolver` 未注册。检查：
- 是否实现 `AssigneeResolver` 接口
- `@Component` 扫描路径是否正确

### Q2：BPMN 文件解析失败

- 检查 XML 是否符合 BPMN 2.0 规范
- 中文 / 特殊字符需用 CDATA 包裹
- 上传文件大小限制 50MB（`spring.servlet.multipart.max-file-size`）

### Q3：流程超时未触发

- 检查 `pmis.cronjob.sla.enabled`（实际在 cronjob 模块执行）
- 流程引擎只负责标记 `dueDate`，超时扫描由 cronjob 模块承担

---

> **本模块是 PC 端专属，移动端审批请走 IM 通道（企业微信 / 钉钉 / 飞书）。**
> 任何 PR 引入移动端适配代码都会被立即驳回。
