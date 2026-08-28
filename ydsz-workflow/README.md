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
| **节点类型** | 11 种：START / APPROVAL / CC / CONDITION / PARALLEL / INCLUSIVE / END / SUBPROCESS / SERVICE / FOREACH / LEVEL_APPROVAL（加签/转交/委派属任务操作） |
| **分支规则** | 条件分支 / 默认分支 / 跳过规则 |
| **定时器** | 节点超时 / 流程超时（cron 表达式） |
| **事件订阅** | 流程开始 / 结束 / 节点进入 / 离开事件 |
| **SLA 提醒** | REMIND / NOTIFY / ESCALATE / AUTO_PASS / AUTO_REJECT 五种 SLA 动作 |
| **审批人去重** | 同一审批人在多级审批中自动合并 |
| **流程模板** | 模板库 + 版本管理 + 复制/导入/导出 + 智能推荐 |
| **流程监控** | 实例状态 / 节点耗时 / 拥堵分析 / 分析报表 |
| **实例迁移** | 流程实例版本迁移 + 预览（dry run） |
| **设计器** | bpmn-js 拖拽设计 + 表单设计 + 表达式编辑 |
| **审批扩展** | 评论/常用意见、嵌入式审批面板、合并审批/加签投票/离线转交（advanced 端点） |
| **办理人类型** | 12 种：USER / ROLE / DEPT / SPEL / INITIATOR / LEADER / POSITION / DEPT_LEADER / SELF_SELECT / MULTI_LEADER / GROUP_CLAIM（分组抢办）/ GROUP_ALL（分组全办） |
| **会签模式** | 或签（OR）/ 并行会签（PARALLEL）/ 票签（WEIGHTED，加权投票） |
| **跳转类型** | PASS / REJECT / FORWARD / BACK |
| **加签类型** | ORIGINAL / BEFORE / AFTER / PARALLEL |
| **AI 辅助** | 定义生成 / 实例分析 / 通知优化 / 委派推荐 / 国际化。⚠️ **当前未实现**：原降级骨架代码及空目录已于近期清理移除（commit fcefb5064），待二阶段按 `LlmServiceClient` Gateway 抽象 + 超时降级方案实质化，不阻塞核心审批链路 |
| **多租户** | 集团 + 公司 + 部门三级隔离（MULTI 模式） |
| **历史归档** | 定时归档 + 清理（可配置 cron / 阈值天数） |
| **附件预览** | 外部预览服务集成（kkFileView / Office Online） |

### 2. 关键 Controller（基路径 `/api/v1/workflow`）

| Controller | 路径前缀 | 作用 |
|---|---|---|
| `FlowDefinitionController` | `/api/v1/workflow/engine` | 流程定义 CRUD / 部署 / SLA 扫描 |
| `FlowInstanceController` | `/api/v1/workflow/engine` | 流程实例（发起 / 审批 / 驳回 / 审计轨迹 / 自动触发器） |
| `FlowTaskController` | `/api/v1/workflow/engine` | 审批任务（待办 / 已办 / 委派授权 / 批量催办 / 附件） |
| `FlowDesignerController` | `/api/v1/workflow/engine` | 设计器（表单 / 表达式 / SLA 配置 / 模板 / 版本对比） |
| `FlowMonitorDashboardController` | `/api/v1/workflow/engine` | 流程监控看板（概览 / 趋势 / 异常检测 / 健康度评分） |
| `FlowEmbeddedApprovalController` | `/api/v1/workflow/embedded` | 嵌入式审批面板 |
| `FlowAdvancedController` | `/api/v1/workflow/advanced` | 合并审批 / 加签投票 / 去重 / 周报月报 / 催办限流等 |
| `FlowAnalyticsController` | `/api/v1/workflow/analytics` | 审批分析仪表盘（总览 / 效率排行 / 节点耗时 / 趋势） |
| `FlowCommentController` | `/api/v1/workflow/comment` | 评论（树形回复）/ 常用意见 |
| `FlowCategoryController` | `/api/v1/workflow/categories` | 流程分类（树形） |
| `FlowTemplateController` | `/api/v1/workflow/template` | 流程模板市场（查询 / 导入 / 导出 / 版本 / 智能推荐） |

> **Controller 数量说明**：`web/controller/` 共 15 个 Controller 文件。除 `embedded`、`advanced`、
> `analytics`、`comment`、`categories`、`template` 外，其余 Controller 统一挂载在 `/api/v1/workflow/engine`
> 前缀下，通过方法级路径区分功能。

## 数据库表设计

实体共映射 **21 张主表**（另有已废弃的 `ydsz_flow_dmn_rule` 表，仅存 Mapper XML 遗迹，无 Java 实体/Mapper 接口）。
数据库实体位于 `ydsz-workflow-infra` 模块的 `com.njydsz.workflow.infra.entity` 包下，
类名**无 DO 后缀**（如 `FlowDefinition` / `FlowRunTask`，MyBatis-Plus 实体 + MapStruct 转换器风格）。

**DDL 维护口径（2026-08-27 修订）**：三库全量 DDL 脚本随仓库维护于根目录 `data/{mysql,oracle,postgre}/ydsz-workflow.sql`；
部署环境的差异配置（数据源连接等）由 Nacos 统一下发。历史文档中"DDL 不在模块内"的说法已作废。

| 业务域 | 表名 | 说明 |
|---|---|---|
| **流程定义** | `ydsz_flow_definition` | 流程定义主表（YDSZ-Flow XML/JSON/BPMN 2.0） |
| | `ydsz_flow_template` | 流程模板（版本管理 + 复制/导入/导出） |
| | `ydsz_flow_category` | 流程分类（树形） |
| | `ydsz_flow_node` | 流程节点 |
| | `ydsz_flow_skip` | 跳过规则 |
| | `ydsz_flow_timer` | 节点/流程超时定时器（cron） |
| | `ydsz_flow_dmn_rule` | DMN 规则表（⚠️ 已废弃：残留 Mapper XML 已清理，无实体/无 Mapper 接口，待迁移 ydsz-pmis-literule） |
| **实例** | `ydsz_flow_instance` | 流程实例（运行中） |
| | `ydsz_flow_his_instance` | 历史实例（已结束） |
| **任务** | `ydsz_flow_run_task` | 待办/运行任务 |
| | `ydsz_flow_his_task` | 已办/历史任务 |
| **审批/评论** | `ydsz_flow_comment` | 审批意见 |
| | `ydsz_flow_quick_comment` | 常用意见 |
| **抄送/委派** | `ydsz_flow_cc` | 抄送记录 |
| | `ydsz_flow_cc_rule` | 抄送规则 |
| | `ydsz_flow_delegate_auth` | 委托授权（代理人/时间窗） |
| **附件** | `ydsz_flow_attachment` | 流程附件 |
| **审计日志** | `ydsz_flow_audit_log` | 流程审计（按月分区） |
| **事件/触发** | `ydsz_flow_event_subscription` | 事件订阅（开始/结束/节点进出） |
| | `ydsz_flow_auto_trigger` | 自动触发器（业务事件发起流程） |
| **用户** | `ydsz_flow_user` | 流程用户（含离职/兼职/加签类型） |
| **管理角色** | `ydsz_flow_admin_role` | 流程管理角色 |

> **索引关键点**：
> - `ydsz_flow_definition(template_key, version)` 唯一（最新版查找）
> - `ydsz_flow_instance(definition_id, status)` 监控
> - `ydsz_flow_run_task(assignee, status, due_date)` 待办列表 + SLA
> - `ydsz_flow_his_instance(definition_id, end_time)` 历史归档
> - `ydsz_flow_cc(recipient_id, read_flag)` 抄送分页
> - `ydsz_flow_event_subscription(event_type, listener)` 事件分发
>
> **幂等约束（2026-08-27 GAP-P0 修复）**：
> `ydsz_flow_run_task` 的唯一约束 `uk_..._instance_node_assignee` 含 `iter_var` 列，
> 该列在 PostgreSQL / Oracle 下为 **NOT NULL DEFAULT ''**（空字符串占位，非 NULL）——
> 唯一约束视 NULL 为互异值，允许 NULL 会令非 FOREACH 任务的防重失效。MySQL 版此前已修复，
> PG/Oracle 版已对齐，存量环境升级脚本见各 DDL 文件尾部注释段。
>
> **分区说明**：⚠️ 历史文档曾宣称 `ydsz_flow_audit_log` 为按月分区表，经核对三库 DDL 实为普通单表。
> 表为只追加型审计流，若数据量增长显著需启用 pg_partman 月分区，属待规划事项而非现状。

## 平台适配硬约束

> 自 2026-07-06 起明确：**本模块永远不适配移动端 App 与独立 H5 应用**。

| 维度 | 范围 |
|---|---|
| ✅ 支持 | PC Web（`ydsz-frontend`，Vue 3.5 + Element Plus，桌面浏览器 ≥ 1280px） |
| ❌ 不支持 | 原生 iOS/Android App、uni-app / Taro 移动端、独立的移动 H5 子应用、PWA 移动模式 |
| 🔁 移动端审批替代方案 | ① 对接企业微信 / IM 平台（签名工具位于 `ydsz-common-notify` 模块）；② 独立「轻审批 H5」应用 |

**为什么不做移动端适配**：
1. 流程设计器（bpmn-js）强依赖桌面交互
2. 流程监控信息密度高，移动端无法保证可用性
3. 业务定位（B 端内部工具）天然服务 PC 场景
4. 移动端需求已通过 IM 审批通道完整覆盖

**实施约束**：
- 前端严禁引入 `vant` / `uni-ui` / `taro-ui`
- 后端 API 默认响应 PC 端字段结构，不为移动端裁剪
- 任何 PR 不得引入工作流模块移动端适配相关代码

## 电子签章硬约束

> **本模块永远不会集成电子签章（e-sign）能力**。详见根 README 7.5。

## 启动顺序

依赖 `common` + `nacos`，**应在 `userinfo` 之后**启动（通过 Feign 获取审批人信息）。

## 目录结构

```
ydsz-workflow/
├── pom.xml                              # 父 POM（6 模块 DDD 架构）
├── README.md
├── ydsz-workflow-api/                   # API 层 — Feign 客户端 + Fallback
│   └── src/main/java/.../api/
│       ├── client/                      # Feign 客户端接口（WorkflowServiceClient）
│       └── fallback/                    # Feign 降级实现
├── ydsz-workflow-app/                   # App 端基座模块 — 自动配置 + 健康检查
│   └── src/main/java/.../app/
│       ├── config/                      # 自动配置（WorkflowAppAutoConfiguration）
│       └── health/                      # 健康检查（WorkflowAppHealthIndicator）
├── ydsz-workflow-domain/                # 领域层 — 枚举 + DTO + VO + Repository 接口
│   └── src/main/java/.../domain/
│       ├── dto/                         # 数据传输对象（23 个）
│       ├── enums/                       # 枚举（9 个）
│       │   ├── FlowNodeType.java        # 节点类型（11 种）
│       │   ├── FlowInstanceStatus.java  # 实例状态（RUNNING/SUSPENDED/COMPLETED/TERMINATED/REJECTED/ERROR/ROLLED_BACK）
│       │   ├── FlowTaskStatus.java      # 任务状态（PENDING/COMPLETED/REJECTED 等 11 种）
│       │   ├── FlowAssigneeType.java    # 办理人类型（12 种，含 GROUP_CLAIM/GROUP_ALL）
│       │   ├── FlowPerformType.java     # 会签类型（OR/PARALLEL/WEIGHTED）
│       │   ├── FlowSignType.java        # 加签类型（ORIGINAL/BEFORE/AFTER/PARALLEL）
│       │   ├── FlowSkipType.java        # 跳转类型（PASS/REJECT/FORWARD/BACK）
│       │   ├── FlowSlaAction.java       # SLA 动作（REMIND/NOTIFY/ESCALATE/AUTO_PASS/AUTO_REJECT）
│       │   └── WorkflowExceptionCode.java # 异常码（B70001-B75099 区间）
│       ├── event/                       # 领域事件（15 个 ⚠️ 当前未接线：全为预留类，二阶段统一收编，见优化报告 v2 §A3）
│       ├── gateway/                     # 网关接口（NameServiceClient / NotificationClient）
│       ├── query/                       # 查询对象（3 个）
│       ├── repository/                  # Repository 接口（20 个）
│       └── vo/                          # 视图对象（52 个）
├── ydsz-workflow-infra/                 # 基础设施层 — Entity + Mapper + XML + Repository 实现
│   └── src/main/
│       ├── java/.../infra/
│       │   ├── converter/               # 对象转换器（WorkflowConverter / WorkflowRepositoryConverter）
│       │   ├── entity/                  # 数据库实体（21 个，DO 后缀）
│       │   ├── mapper/                  # MyBatis-Plus Mapper 接口（20 个）
│       │   └── repository/              # Repository 实现
│       └── resources/mapper/            # MyBatis XML 映射文件（17 个）
├── ydsz-workflow-server/                # 服务层 — 核心业务逻辑
│   └── src/main/
│       ├── java/.../workflow/
│       │   ├── WorkflowFacade.java      # 模块门面
│       │   └── server/
│       │       ├── config/              # 自动配置 + 属性（FlowAutoConfiguration / FlowProperties，prefix `ydsz.flow`）
│       │       ├── engine/              # 流程引擎核心
│       │       │   ├── BpmnXmlParser.java      # BPMN 2.0 XML 解析器
│       │       │   ├── BpmnModel.java          # BPMN 模型
│       │       │   ├── BpmnNodeParser.java     # 节点解析器
│       │       │   ├── BpmnDiagramParser.java  # 图形解析器
│       │       │   ├── BpmnSkipParser.java     # 跳过规则解析器
│       │       │   ├── FlowAssigneeResolver.java # 办理人解析器
│       │       │   ├── FlowDefinitionCacheService.java  # 定义缓存服务
│       │       │   ├── FlowGraphValidator.java # 流程图校验器
│       │       │   ├── FlowSensitiveMasker.java # 敏感字段脱敏器
│       │       │   ├── FlowServiceNodeExecutor.java # 服务节点执行器
│       │       │   ├── FlowClusterLockHelper.java # 集群锁助手
│       │       │   ├── FlowDefinitionCacheBroadcaster.java # 定义缓存广播
│       │       │   ├── FlowSkipUtils.java      # 跳过工具
│       │       │   ├── FlowUrgeLimiter.java    # 催办限流器
│       │       │   ├── expr/                   # 表达式引擎（Aviator + SpEL）
│       │       │   └── impl/                   # 引擎实现（DefaultFlowAdvancer 等）
│       │       ├── facade/              # 门面封装（YdszWorkflowFacade）
│       │       ├── form/                # 表单引擎（FlowFormEngineService + 字段类型 + 校验器）
│       │       ├── health/              # 健康检查（FlowHealthIndicator）
│       │       ├── listener/            # 事件监听器（ProjectInitiationFlowListener）
│       │       ├── metrics/             # Prometheus 指标（FlowMetrics）
│       │       ├── queue/               # 消息队列（FlowQueuePublisher + FlowQueueChannels）
│       │       ├── scheduler/           # 调度器（FlowAutoUrgeScheduler）
│       │       ├── search/              # 搜索 Provider SPI（WorkflowSearchProvider）
│       │       ├── service/             # 业务服务接口 + 实现
│       │       │   ├── impl/            # 业务服务实现
│       │       │   ├── ai/              # AI 辅助能力（定义生成 / 实例分析 / 通知优化 / 委派推荐 / 国际化 / 策略 / 集成）
│       │       │   └── instance/        # 实例域服务（AssigneeResolutionService / DelegateRedirectService / EmptyAssigneeStrategyService / ServiceNodeExecuteService）
│       │       └── template/            # 模板库（FlowPresetTemplateLibrary + FlowTemplateDefinition）
│       └── resources/META-INF/          # Spring Boot 自动配置
│           ├── additional-spring-configuration-metadata.json
│           └── spring/AutoConfiguration.imports
└── ydsz-workflow-web/                   # Web 层 — Controller + 启动类 + 配置
    └── src/main/
        ├── java/.../web/
        │   ├── WorkflowApplication.java  # Spring Boot 启动类
        │   └── controller/                # REST Controller（15 个，含 advanced/embedded/analytics 等）
        │       ├── FlowDefinitionController.java      # 流程定义（definition 子包）
        │       ├── FlowCategoryController.java        # 流程分类（definition 子包）
        │       ├── FlowDesignerController.java        # 设计器（definition 子包）
        │       ├── FlowTemplateController.java        # 流程模板（definition 子包）
        │       ├── FlowInstanceController.java        # 流程实例（instance 子包）
        │       ├── FlowTaskController.java            # 审批任务（instance 子包）
        │       ├── FlowAdvancedController.java        # 高级功能（instance 子包）
        │       ├── FlowMonitorDashboardController.java # 监控看板（根 controller 包）
        │       ├── FlowEmbeddedApprovalController.java # 嵌入式审批（integration 子包）
        │       ├── FlowAnalyticsController.java       # 分析报表（analytics 子包）
        │       └── FlowCommentController.java          # 评论/常用语（notification 子包）
        └── resources/
            ├── application.yml            # 模块配置
            ├── bootstrap.yml              # Nacos 注册配置
            └── config/                    # 环境配置（dev/sit/uat）
```

## 配置文件

标准 `DB_*` / `REDIS_*` / `NACOS_*` 环境变量。模块级配置统一在 `ydsz.flow.*` 前缀下（`FlowProperties`）。

| 配置项 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `ydsz.flow.enabled` | Boolean | true | 是否启用工作流模块 |
| `ydsz.flow.health-enabled` | Boolean | true | 是否启用健康检查 |
| `ydsz.flow.publish-block-on-high-risk` | Boolean | true | 发布时是否阻断 HIGH 风险 |
| `ydsz.flow.designer-lock-timeout-minutes` | Long | 30 | 设计器协同编辑锁定超时（分钟） |
| `ydsz.flow.subprocess.max-nesting-depth` | Integer | 3 | 子流程最大嵌套深度 |
| `ydsz.flow.attachment.preview-server-url` | String | （空） | 附件预览服务地址 |
| `ydsz.flow.auto-urge.threshold-hours` | Long | 24 | 自动催办超时阈值（小时） |
| `ydsz.flow.auto-urge.batch-size` | Integer | 100 | 自动催办单次扫描批量 |
| `ydsz.flow.definition-cache.definition-cache-ttl-minutes` | Long | 60 | 定义缓存 TTL（分钟） |
| `ydsz.flow.definition-cache.definition-cache-max-size` | Long | 1000 | 定义缓存最大容量 |
| `ydsz.flow.history.archive-enabled` | Boolean | true | 是否启用历史归档 |
| `ydsz.flow.history.retention-days` | Integer | 30 | 归档阈值天数 |
| `ydsz.flow.history.batch-size` | Integer | 100 | 单次归档批量 |
| `ydsz.flow.history.max-process-ms` | Long | 30000 | 单次归档最大耗时（毫秒） |
| `ydsz.flow.history.cron-expression` | String | 0 0 3 * * ? | 归档任务 cron |
| `ydsz.flow.history.purge-enabled` | Boolean | false | 是否启用归档数据清理 |
| `ydsz.flow.history.purge-days` | Integer | 1825 | 归档清理阈值天数（默认5年） |

## 启动

```bash
cd ydsz-cloud
mvn -pl ydsz-common -am install -DskipTests
mvn -pl ydsz-workflow -am spring-boot:run
```

## 测试

> 当前模块测试覆盖仍偏薄（6 个测试类，2026-08-27 更新）：
> domain 层枚举×2（FlowNodeType / FlowTaskStatus）、状态机×1（FlowTaskStateMachine）、
> server 层引擎解析器×2（BpmnXmlParser / BpmnElementHelper）、定义缓存×1（FlowDefinitionCacheService）。
> 二阶段重点补齐：引擎网关/join/REJECT 推进回归套件、会签并发（GAP-A1 原子计数）集成测试、
> Testcontainers PostgreSQL 上下文启动冒烟测试。

## Feign 接口

### 被调用

- 业务服务（userinfo 等）→ 通过 `WorkflowServiceClient`（Feign）启动流程
- 立项审批触发已由 `InitiationFeignClient` 同步调用**迁移至消息队列异步路径**（`FlowQueuePublisher` 订阅事件）

### Feign Client 列表

| Client | 模块 | 说明 |
|---|---|---|
| `WorkflowServiceClient` | `ydsz-workflow-api` | 工作流服务 Feign 客户端 |
| `WorkflowServiceClientFallback` | `ydsz-workflow-api` | Feign 降级实现 |
| `NameServiceClient` | `ydsz-workflow-domain` | 用户/姓名查询网关 |
| `NotificationClient` | `ydsz-workflow-domain` | 通知推送网关 |

## 核心依赖

| 依赖 | 说明 |
|---|---|
| `ydsz-common-*` | YDSZ 公共组件（jdbc / auth / web / cache / queue / search / socket / notify / audit / config / tenant / sentry / thread / lock / feign / core / domain / util） |
| `spring-cloud-starter-alibaba-nacos-discovery` | Nacos 服务注册 |
| `spring-cloud-starter-alibaba-nacos-config` | Nacos 配置中心 |
| `springdoc-openapi-starter-webmvc-ui` | OpenAPI / Swagger UI |
| `mybatis-plus` | ORM 框架 |
| `dynamic-datasource-spring-boot3-starter` | 多数据源 |
| `aviator` | Aviator 表达式引擎 |
| `micrometer-registry-prometheus` | Prometheus 指标注册 |
| `mapstruct` | 编译期对象映射 |

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

- 流程引擎只负责标记 `dueDate`，超时扫描由 cronjob 模块承担
- 确认 cronjob 模块中对应的 SLA 扫描 JobHandler 已注册并启用

### Q4：流程实例状态与任务状态的关系

- 实例状态（`FlowInstanceStatus`）：RUNNING / SUSPENDED / COMPLETED / TERMINATED / REJECTED / ERROR / ROLLED_BACK
- 任务状态（`FlowTaskStatus`）：PENDING / CLAIMED / COMPLETED / REJECTED / SKIPPED / CANCELLED / TIMEOUT / DELEGATED / FROZEN / SUSPENDED / DRAFT
- 两者独立流转，实例挂起（SUSPENDED）会连带冻结任务（FROZEN），但任务级挂起（SUSPENDED）不影响实例

---

> **本模块是 PC 端专属，移动端审批请走 IM 通道。**
> 任何 PR 引入移动端适配代码都会被立即驳回。
