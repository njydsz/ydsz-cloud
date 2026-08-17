# ydsz-workflow

> 自研工作流引擎（YDSZ-Flow v2 + BPMN 2.0）

## 模块定位

| 属性 | 值 |
|---|---|
| **类型** | 部署单元（独立启动） |
| **端口** | **9005**（按构建顺序 6/10） |
| **服务名** | `ydsz-workflow` |
| **构建顺序** | 6/10 |
| **数据库** | PostgreSQL（`ydsz_flow_*` 表） |
| **依赖** | Nacos、PostgreSQL、Redis |
| **平台** | ⚠️ **仅 PC Web 端**（不支持移动端 / 独立 H5） |

## 核心职责

本模块是 YDSZ 的**自研工作流引擎**，提供流程定义、审批、监控完整链路。

### 1. 核心能力

| 能力 | 说明 |
|---|---|
| **流程定义** | YDSZ-Flow XML / JSON / BPMN 2.0 解析 |
| **节点类型** | 开始 / 审批 / 加签 / 减签 / 转交 / 抄送 / 委派 / 代理 |
| **分支规则** | 条件分支 / 默认分支 / 跳过规则 |
| **定时器** | 节点超时 / 流程超时（cron 表达式） |
| **事件订阅** | 流程开始 / 结束 / 节点进入 / 离开事件 |
| **SLA 提醒** | P1-P4 SLA 倒计时 + 飞书/钉钉告警 |
| **审批人去重** | 同一审批人在多级审批中自动合并 |
| **流程模板** | 模板库 + 版本管理 + 复制/导入/导出 |
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
| `/flow/form` | 表单设计 |
| `/flow/delegation` | 委托授权 |
| `/flow/history` | 历史实例 |
| `/flow/instance-migration` | 实例迁移 |
| `/flow/monitor` | 流程监控 |
| `/flow/sla` | SLA 规则 |
| `/flow/simulator` | 流程模拟器 |

## 数据库表设计

本模块在 `deploy/sql/V1.0.0.sql` 中持有 **34 张表**，覆盖流程定义/实例/任务/历史/通知/委托/评论/审批/审计/AI 反馈/触发器。

| 业务域 | 表名 | 说明 |
|---|---|---|
| **流程定义** | `ydsz_flow_definition` | 流程定义主表（YDSZ-Flow XML/JSON/BPMN 2.0） |
| | `ydsz_flow_template` | 流程模板（版本管理 + 复制/导入/导出） |
| | `ydsz_flow_category` | 流程分类（树形） |
| | `ydsz_flow_node` | 流程节点（审批/加签/转交/抄送） |
| | `ydsz_flow_skip` | 跳过规则 |
| | `ydsz_flow_timer` | 节点/流程超时定时器（cron） |
| **实例** | `ydsz_flow_instance` | 流程实例（运行中） |
| | `ydsz_flow_his_instance` | 历史实例（已结束） |
| **任务** | `ydsz_flow_run_task` | 待办/运行任务 |
| | `ydsz_flow_his_task` | 已办/历史任务 |
| | `ydsz_flow_his_variable` | 历史变量快照 |
| **审批/评论** | `ydsz_flow_comment` | 审批意见 |
| | `ydsz_flow_task_comment` | 任务评论 |
| | `ydsz_flow_quick_comment` | 常用意见 |
| **抄送/委派** | `ydsz_flow_cc` | 抄送记录 |
| | `ydsz_flow_cc_rule` | 抄送规则 |
| | `ydsz_flow_delegate_auth` | 委托授权（代理人/时间窗） |
| | `ydsz_flow_delegate_log` | 委托日志 |
| | `ydsz_flow_delegate_message` | 委托消息（IM 通知） |
| **附件** | `ydsz_flow_attachment` | 流程附件 |
| **审计日志** | `ydsz_flow_audit_log` | 流程审计（按月分区） |
| | `ydsz_flow_audit_log_default` | 审计默认分区 |
| **事件/触发** | `ydsz_flow_event_subscription` | 事件订阅（开始/结束/节点进出） |
| | `ydsz_flow_auto_trigger` | 自动触发器（业务事件→发起流程） |
| **通知** | _(已收敛至 ydsz-message 模块)_ | 通知渠道/模板/外发箱/偏好均已移除 |
| **第三方** | `ydsz_flow_third_party_account` | 第三方账号（企业微信/钉钉/飞书） |
| | `ydsz_flow_third_party_log` | 第三方交互日志 |
| **AI 增强** | `ydsz_flow_ai_feedback` | AI 辅助审批反馈（用于学习） |
| **用户** | `ydsz_flow_user` | 流程用户（含离职/兼职） |

> **索引关键点**：
> - `ydsz_flow_definition(template_key, version)` 唯一（最新版查找）
> - `ydsz_flow_instance(definition_id, status)` 监控
> - `ydsz_flow_run_task(assignee, status, due_date)` 待办列表 + SLA
> - `ydsz_flow_his_instance(definition_id, end_time)` 历史归档
> - `ydsz_flow_cc(recipient_id, read_flag)` 抄送分页
> - `ydsz_flow_event_subscription(event_type, listener)` 事件分发
>
> **分区说明**：`ydsz_flow_audit_log` 为 PostgreSQL 范围分区表（按月分区），历史月份可走 `pg_partman` 归档。

## ⚠️ 平台适配硬约束

> 自 2026-07-06 起明确：**本模块永远不适配移动端 App 与独立 H5 应用**。

| 维度 | 范围 |
|---|---|
| ✅ 支持 | PC Web（`ydsz-frontend`，Vue 3.5 + Element Plus，桌面浏览器 ≥ 1280px） |
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
ydsz-workflow/
├── pom.xml                              # 父 POM（5 模块 DDD 架构）
├── README.md
├── ydsz-workflow-api/                   # API 层 — Feign 客户端 + Fallback
│   └── src/main/java/.../api/
│       ├── client/                      # Feign 客户端接口
│       └── fallback/                    # Feign 降级实现
├── ydsz-workflow-domain/                # 领域层 — 实体 + 枚举 + DTO
│   └── src/main/java/.../domain/
│       ├── dto/                         # 数据传输对象（20+）
│       ├── entity/                      # 数据库实体（26 个，无 DO 后缀，符合 entity-naming 规范）
│       │                                # FlowAdminRole/FlowAttachment/FlowAuditLog/FlowAutoTrigger
│       │                                # FlowCategory/FlowCc/FlowCcRule/FlowComment/FlowDefinition
│       │                                # FlowDelegateAuth/FlowEventSubscription
│       │                                # FlowHisInstance/FlowHisTask/FlowInstance/FlowNode
│       │                                # FlowQuickComment/FlowRunTask/FlowSkip/FlowTemplate
│       │                                # FlowThirdPartyAccount/FlowThirdPartyLog/FlowTimer/FlowUser
│       └── enums/                       # 枚举（12 个）
├── ydsz-workflow-infra/                 # 基础设施层 — Mapper + XML
│   └── src/main/
│       ├── java/.../infra/mapper/       # MyBatis-Plus Mapper 接口（24+）
│       └── resources/mapper/            # MyBatis XML 映射文件（20+）
├── ydsz-workflow-server/                # 服务层 — 核心业务逻辑
│   └── src/main/
│       ├── java/.../workflow/
│       │   ├── WorkflowFacade.java      # 模块门面
│       │   └── server/
│       │       ├── config/              # 自动配置 + 属性（FlowAutoConfiguration / FlowProperties）
│       │       ├── engine/              # 流程引擎核心
│       │       │   ├── BpmnXmlParser.java      # BPMN 2.0 XML 解析器
│       │       │   ├── FlowAdvancer.java       # 流程推进器接口
│       │       │   ├── FlowDefinitionCacheService.java  # 定义缓存服务
│       │       │   ├── FlowGraphValidator.java # 流程图校验器
│       │       │   ├── FlowSensitiveMasker.java # 敏感字段脱敏器
│       │       │   ├── JsonHelper.java          # JSON 安全类型提取工具
│       │       │   └── impl/                   # 引擎实现（DefaultFlowAdvancer 等）
│       │       ├── form/                # 表单引擎（FlowFormValidator + 字段类型）
│       │       ├── health/              # 健康检查（FlowHealthIndicator）
│       │       ├── job/                 # 定时任务（归档/超时/重试 JobHandler）
│       │       ├── listener/            # 事件监听器（ProjectInitiationFlowListener）
│       │       ├── metrics/             # Prometheus 指标（FlowMetrics）
│       │       ├── queue/               # 消息队列（FlowQueuePublisher + 频道定义）
│       │       ├── scheduler/           # 调度器（FlowAutoUrgeScheduler）
│       │       ├── service/             # 业务服务接口（40+ Service）
│       │       │   └── impl/            # 业务服务实现（20+ ServiceImpl）
│       │       ├── template/            # 模板库（预设流程模板）
│       │       └── thirdparty/          # 第三方集成（钉钉/飞书签名工具）
│       └── resources/META-INF/          # Spring Boot 自动配置
│           ├── additional-spring-configuration-metadata.json
│           └── spring/AutoConfiguration.imports
└── ydsz-workflow-web/                   # Web 层 — Controller + 启动类 + 配置
    └── src/main/
        ├── java/.../web/
        │   ├── WorkflowApplication.java  # Spring Boot 启动类
        │   └── controller/                # REST Controller
        └── resources/
            ├── bootstrap.yml            # Nacos 注册配置
            └── config/                  # 环境配置（dev/sit/uat）
```

## 配置文件

标准 `DB_*` / `REDIS_*` / `NACOS_*` 环境变量。无特殊配置。

## 启动

```bash
cd ydsz-cloud
mvn -pl ydsz-common -am install -DskipTests
mvn -pl ydsz-workflow spring-boot:run
```

## 测试

```bash
mvn -pl ydsz-workflow -am test
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

- 检查 `ydsz.cronjob.sla.enabled`（实际在 cronjob 模块执行）
- 流程引擎只负责标记 `dueDate`，超时扫描由 cronjob 模块承担

---

> **本模块是 PC 端专属，移动端审批请走 IM 通道（企业微信 / 钉钉 / 飞书）。**
> 任何 PR 引入移动端适配代码都会被立即驳回。
