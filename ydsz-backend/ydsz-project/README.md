# ydsz-project

> 项目执行服务（Project）

## 模块定位

| 属性 | 值 |
|---|---|
| **类型** | 部署单元（独立启动） |
| **端口** | **9003** |
| **服务名** | `ydsz-project` |
| **数据库** | PostgreSQL（共享主库，34 张表） |
| **依赖** | Nacos、PostgreSQL、Redis、MinIO、literule |

## 核心职责

YDSZ 项目管理服务，覆盖**立项 → 执行 → 交付 → 收尾 → 售后**全链路，同时承载**商务销售**（商机/合同/变更/模板）与**财务会计**（发票/回款/费用/收入/利润/对账/信用）能力。

> **2026-07-16 合并**：原 `ydsz-sales`（端口 9010）和 `ydsz-finance`（端口 9011）已合并到本服务，跨域 Feign 契约全部下线，财务/销售数据通过同进程 Mapper 直接查询。API 路径统一为 `/api/project/**`，数据库表前缀统一为 `ydsz_project_*`。

### 1. 业务链路

```
立项（Initiation） → WBS 预算
   ↓ CDCP 门径评审（CD1-CD5）
   ↓
执行（Execution）
   ├── WBS 任务（WbsTask）
   ├── 工时归集（TimeEntry）
   ├── 成本归集（CostAllocation / CostPurchase）
   ├── EVM 挣值管理（PV/EV/AC + CPI/SPI）
   ├── 风险管理（Risk）
   └── 变更管理（ProjectChange）
   ↓
交付（Delivery）
   ├── 交付标准（DeliveryStandard）
   └── 交付物（DeliveryItem）
   ↓
收尾（Closure）
   ↓
售后（Aftersales）
   ├── 质保期（Warranty）
   ├── 运维工单（OpsTicket）P1-P4 SLA
   └── 满意度（Satisfaction）
```

### 2. 关键能力

| 能力 | 说明 |
|---|---|
| **立项管理** | 立项申请、WBS 预算、门径评审（CD1-CD5） |
| **预算管理** | 预算项管理、预算占用监控（80% 黄/95% 红） |
| **WBS 任务** | 树形任务管理、任务分配、进度跟踪 |
| **工时归集** | 工时填报、工时审核、工时分析 |
| **成本管理** | 成本归集、采购成本、成本分摊 |
| **EVM 挣值** | PV/EV/AC 计划/挣值/实际成本、CPI/SPI 指数 |
| **风险管理** | 风险识别、风险评估、风险应对 |
| **变更管理** | 项目变更（5 类：范围/进度/成本/质量/风险） |
| **交付管理** | 交付标准、交付物清单、交付验收 |
| **收尾管理** | 项目结项（正式/预结项/强制结项） |
| **质保管理** | 质保期管理、质保续期 |
| **工单管理** | 运维工单、SLA 分级（P1-P4） |
| **满意度** | 客户满意度调查 |
| **费率管理** | 对外报价费率（职级×技术栈×客户） |
| **资源利用率** | 可计费利用率快照 |

### 3. 关键 Controller

| 路径前缀 | 业务域 |
|---|---|
| `/initiation` | 立项 |
| `/budget` | 预算 |
| `/gate-review` | 门径评审 |
| `/wbs` / `/wbs-task` | WBS 任务 |
| `/time-entry` | 工时 |
| `/cost-allocation` | 成本归集 |
| `/purchase` | 采购成本 |
| `/evm` | EVM 挣值 |
| `/rate-card` / `/rate-internal` | 费率 |
| `/risk` | 风险 |
| `/change` | 变更管理 |
| `/delivery` | 交付 |
| `/closure` | 收尾 |
| `/warranty` | 质保 |
| `/ops-ticket` | 工单 |
| `/satisfaction` | 满意度 |
| `/utilization` | 资源利用率 |
| `/cockpit` | 驾驶舱 |
| `/report` | 报表 |

## 数据库表设计

本模块持有 **20 张表**，表归属依据：`ydsz-project/src/main/java/.../infra/mapper/`

| 表名 | 说明 | Mapper |
|---|---|---|
| `ydsz_project_initiation` | 立项主表（含 WBS 预算快照） | InitiationMapper |
| `ydsz_project_budget_item` | 立项预算项 | BudgetItemMapper |
| `ydsz_project_gate_review` | 门径评审（CD1-CD5） | GateReviewMapper |
| `ydsz_execution_wbs_task` | WBS 任务（树形） | WbsTaskMapper |
| `ydsz_execution_time_entry` | 工时归集 | TimeEntryMapper |
| `ydsz_cost_allocation` | 成本归集 | CostAllocationMapper |
| `ydsz_cost_purchase` | 采购成本 | PurchaseMapper |
| `ydsz_execution_risk` | 项目风险登记 | RiskMapper |
| `ydsz_project_change` | 项目变更（5 类） | ProjectChangeMapper |
| `ydsz_execution_delivery_standard` | 交付物标准 | DeliveryStandardMapper |
| `ydsz_execution_delivery_item` | 交付物实例 | DeliveryItemMapper |
| `ydsz_execution_closure` | 项目结项 | ProjectClosureMapper |
| `ydsz_evm_measure` | EVM 挣值测量（PV/EV/AC） | EvmMeasureMapper |
| `ydsz_rate_card` | 对外报价费率 | RateCardMapper |
| `ydsz_rate_internal` | 对内成本费率 | RateInternalMapper |
| `ydsz_warranty` | 质保期 | WarrantyMapper |
| `ydsz_ops_ticket` | 运维工单（P1-P4） | OpsTicketMapper |
| `ydsz_satisfaction` | 客户满意度 | SatisfactionMapper |
| `ydsz_billable_utilization_snapshot` | 可计费利用率快照 | BillableUtilizationSnapshotMapper |
| `ydsz_alert_dispatch` | 预警派发记录 | AlertDispatchMapper |

> **索引关键点**：
> - `ydsz_execution_wbs_task(project_id, parent_id)` 树形查询
> - `ydsz_execution_time_entry(project_id, task_id, work_date)` 工时查询
> - `ydsz_project_gate_review(initiation_id, gate_number)` 门径评审
> - `ydsz_evm_measure(project_id, measure_date)` EVM 快照
> - `ydsz_billable_utilization_snapshot(employee_id, snapshot_date)` 利用率快照

## DDD 架构

```
ydsz-project/
├── domain/
│   ├── entity/         # 领域实体 (20 个 DO)
│   ├── dto/            # 请求/响应 DTO
│   ├── enums/          # 枚举
│   ├── vo/             # 视图对象
│   └── query/          # 查询条件对象
├── infra/
│   └── mapper/         # MyBatis Mapper (20 个接口 + XML)
├── server/
│   ├── service/       # 领域服务 (~60 个)
│   ├── engine/        # 业务引擎
│   │   ├── EvmCalculator.java      # EVM 计算
│   │   ├── BudgetMonitor.java      # 预算监控
│   │   └── RiskAssessor.java       # 风险评估
│   ├── job/           # 定时任务
│   ├── assembler/     # 数据组装器
│   └── metrics/       # 指标收集
├── api/                # Feign Client（调用 sales/finance）
│   ├── SalesDataClient.java       # 调用 sales 服务
│   └── FinanceDataClient.java     # 调用 finance 服务
├── web/
│   ├── controller/     # REST Controller (~30 个)
│   ├── config/
│   └── ProjectApplication.java
└── resources/
    ├── bootstrap.yml
    ├── application.yml
    └── mapper/         # MyBatis XML 映射 (20 个)
```

## Feign Client

### 调用其他服务

| Client | 说明 | 用途 |
|---|---|---|
| `SalesDataClient` | 调用 sales 服务 | 查询商机/合同信息 |
| `FinanceDataClient` | 调用 finance 服务 | 查询发票/付款/利润快照 |

### 示例代码

```java
// 查询商机信息
OpportunityDTO opportunity = salesDataClient.getOpportunityById(initiation.getOpportunityId());

// 查询利润快照
ProfitSnapshotDTO profit = financeDataClient.getProfitSnapshot(projectId, date);
```

## 配置文件

| 变量 | 说明 |
|---|---|
| `SEATA_SERVER_ADDR` | Seata Server（默认 `127.0.0.1:8091`） |
| `MINIO_*` | MinIO 配置 |
| `DB_*` / `REDIS_*` | 数据库与缓存 |

`seata.tx-service-group = ydsz-tx-group`（与 deploy/seata 部署的分组对应）。

## 启动

```bash
cd ydsz-backend
mvn -pl ydsz-project spring-boot:run
```

## 测试

```bash
mvn -pl ydsz-project -am test
# 覆盖率：target/site/jacoco/index.html
```

## 跨服务集成

### 调用其他服务（通过 Feign）

- `ydsz-userinfo` → 获取用户/部门信息
- `ydsz-workflow` → 触发立项审批
- `ydsz-literule` → 规则引擎（风险预警）

### 被其他服务调用

- `ydsz-cronjob` → 拉取任务数据
- `ydsz-agent` → 获取项目数据（AI 分析）
- `ydsz-workflow` → 获取项目信息（流程节点）

## 常见问题

### Q1：Seata 事务回滚失败

A：检查：
1. Seata Server 是否启动（`http://127.0.0.1:7091`）
2. Nacos 中 `seata-client.properties` 配置的 `vgroup-mapping.ydsz-tx-group` 是否映射到 `default`
3. 数据源是否被 Seata 代理（`enable-auto-data-source-proxy: true`）

### Q2：EVM 看板数据延迟

A：EVM 快照由 cronjob 模块每日凌晨 4 点重算。可手动触发：
`POST /evm/snapshot?projectId=xxx&date=2026-07-08`

### Q3：分页查询慢

A：请使用游标分页（`CursorPageQuery`）替代 offset 分页，索引走主键避免深度分页慢 SQL。

### Q4：预算预警

A：预算占用监控：
- 80% 黄色预警（`BudgetMonitor` 检测）
- 95% 红色预警（`BudgetMonitor` 检测）
- 100% 冻结（禁止新增采购/报销）

---

> 本模块采用 **DDD 五层架构**，所有跨服务调用必须走 **Feign Client**，禁止直连其他模块的 Mapper。
> 严禁在 Controller 直接写业务逻辑，必须 Service 化。