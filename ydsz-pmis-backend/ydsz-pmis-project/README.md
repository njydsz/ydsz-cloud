# ydsz-pmis-project

> 项目主业务（Project + Execution 合并）

## 模块定位

| 属性 | 值 |
|---|---|
| **类型** | 部署单元（独立启动） |
| **端口** | **9003**（按构建顺序 4/8） |
| **服务名** | `ydsz-pmis-project` |
| **构建顺序** | 4/8 |
| **数据库** | PostgreSQL（共享主库，**业务最大模块**） |
| **依赖** | Nacos、PostgreSQL、Redis、MinIO、Seata、userinfo(Feign)、workflow(Feign)、literule |

## 核心职责

PMIS 核心业务模块，覆盖**项目全生命周期 + 执行管理**。

### 1. 项目主链路（商机 → 售后）

```
商机（Opportunity）
   ↓ A/B/C 分级
立项（Initiation） → WBS 预算
   ↓
合同（Contract）→ 模板/补充/变更/风险
   ↓
执行（Execution）
   ├── WBS 任务
   ├── 工时归集（TimeEntry）
   ├── 成本归集（Expense / Purchase）
   ├── 收入确认（Revenue / Invoice）
   ├── 利润核算（双费率：Rate Card 对外 + RateInternal 对内）
   ├── EVM 挣值管理（PV/EV/AC + CPI/SPI）
   └── 风险/预警（Risk / Alert）
   ↓
收尾（Closure） → 售后（Aftersales）
   ├── 质保期（Warranty）
   ├── 运维工单（OpsTicket）P1-P4 SLA
   └── 满意度（Satisfaction）
```

### 2. 关键 Controller

| 路径前缀 | 业务域 |
|---|---|
| `/opportunity` | 商机 |
| `/initiation` | 立项 |
| `/contract` / `/contract-template` / `/contract-change` | 合同 |
| `/wbs` / `/wbs-task` | WBS 任务 |
| `/time-entry` | 工时 |
| `/expense` / `/purchase` / `/reimburse` | 费用 |
| `/invoice` / `/payment` / `/receivable` | 收付款 |
| `/profit` / `/profit-simulation` | 利润 |
| `/evm` | EVM 挣值 |
| `/risk` | 风险 |
| `/alert` | 预警 |
| `/rate-card` / `/rate-internal` | 费率 |
| `/delivery` | 交付 |
| `/closure` | 收尾 |
| `/warranty` / `/ops-ticket` / `/satisfaction` | 售后 |
| `/customer-credit` | 客户信用 |
| `/cockpit` / `/report/executive` | 报表 |
| `/change` | 变更管理 |
| `/rule-engine/*` | 规则引擎（依托 literule） |

### 3. 分布式事务（Seata AT 模式）

跨服务事务场景：

- 商机转立项（project + workflow）
- 工时归集（project + finance）
- 利润快照（project + agent）
- 合同签署联动工作流

## 数据库表设计

本模块是**业务最重**模块，在 `deploy/sql/V1.0.0.sql` 中持有 **50 张表**，覆盖项目全生命周期 + 财务 + 规则引擎。

| 业务域 | 表名 | 说明 |
|---|---|---|
| **商机** | `pmis_project_opportunity` | 商机主表（A/B/C 分级） |
| | `pmis_project_opportunity_follow` | 商机跟进记录 |
| **立项** | `pmis_project_initiation` | 立项单（含 WBS 预算快照） |
| | `pmis_project_budget_item` | 立项预算项 |
| | `pmis_project_gate_review` | 立项门径评审（CD1-CD5） |
| **合同** | `pmis_project_contract` | 合同主表 |
| | `pmis_project_contract_supplement` | 合同补充协议 |
| | `pmis_project_contract_change` | 合同变更 |
| | `pmis_project_contract_template` | 合同模板 |
| **执行-WBS** | `pmis_execution_wbs_task` | WBS 任务 |
| | `pmis_execution_time_entry` | 工时归集 |
| **执行-成本** | `pmis_cost_allocation` | 成本归集 |
| | `pmis_cost_purchase` | 采购成本 |
| | `pmis_cost_expense` | 费用报销 |
| **执行-收入** | `pmis_finance_invoice` | 发票 |
| | `pmis_finance_payment` | 收付款 |
| | `pmis_finance_customer_credit` | 客户信用额度 |
| | `pmis_profit_revenue` | 收入确认 |
| **利润/EVM** | `pmis_profit_snapshot` | 利润快照（双费率） |
| | `pmis_profit_simulation` | 利润模拟（多版本） |
| | `pmis_evm_measure` | EVM 挣值测量（PV/EV/AC） |
| **风险/预警** | `pmis_execution_risk` | 项目风险登记 |
| | `pmis_alert_dispatch` | 预警派发记录 |
| **变更/交付/收尾** | `pmis_project_change` | 项目变更（5 类） |
| | `pmis_execution_delivery_standard` | 交付物标准 |
| | `pmis_execution_delivery_item` | 交付物实例 |
| | `pmis_execution_closure` | 项目结项（正式/预/强制） |
| **售后** | `pmis_warranty` | 质保期 |
| | `pmis_ops_ticket` | 运维工单（P1-P4） |
| | `pmis_satisfaction` | 客户满意度 |
| **费率** | `pmis_rate_card` | 对外报价费率（职级×技术栈×客户） |
| | `pmis_rate_internal` | 对内成本费率（职级×部门） |
| **对账/报表** | `pmis_reconcile_daily` | 月度对账结果 |
| | `pmis_billable_utilization_snapshot` | 可计费利用率快照 |
| **规则引擎（literule 库）** | `pmis_rule_def` | 规则定义 |
| | `pmis_rule_version_history` | 规则版本历史 |
| | `pmis_rule_template` | 规则模板 |
| | `pmis_rule_test_case` | 规则测试用例 |
| | `pmis_rule_execution_trace` | 规则执行追踪 |
| | `pmis_rule_decision_table` | 决策表 |
| | `pmis_rule_decision_tree` | 决策树 |
| | `pmis_rule_canary_bucket` | 灰度分桶 |
| | `pmis_rule_scorecard` | 规则评分卡 |
| | `pmis_rule_script` | 脚本规则（Aviaro 表达式） |
| | `pmis_rule_variable_def` | 规则变量定义 |
| | `pmis_rule_chain_graph` | 规则链图 |
| | `pmis_rule_dependency` | 规则依赖 |
| | `pmis_rule_ab_policy` | A/B 策略 |
| | `pmis_rule_ab_rollback` | A/B 回滚 |
| | `pmis_rule_pack` | 规则包 |
| | `pmis_rule_pack_install` | 规则包安装记录 |

> **索引关键点**：
> - `pmis_execution_wbs_task(project_id, parent_id)` 树形查询
> - `pmis_profit_snapshot(project_id, snapshot_date)` 唯一
> - `pmis_evm_measure(project_id, measure_date)` 唯一
> - `pmis_rule_def(rule_code, version)` 唯一
>
> **双库隔离**：`pmis_finance_*` 财务表未来可拆分到独立 schema（参见根 README 路线图）。

## 启动顺序

依赖 `common` + `nacos` + 可选 `userinfo` / `workflow` Feign，**应在 `gateway` 之后**启动。

## 目录结构

```
ydsz-pmis-project/
├── pom.xml
└── src/main/
    ├── java/com/njydsz/pmis/project/
    │   ├── ProjectApplication.java
    │   ├── controller/        # ~30 个 Controller
    │   ├── service/           # ~60 个 Service
    │   ├── mapper/
    │   ├── entity/            # ~40 个 DO
    │   ├── dto/ / vo/
    │   ├── enums/             # ~30 个枚举
    │   ├── calculator/        # EVM / 利润计算
    │   ├── event/             # 业务事件
    │   └── config/
    ├── resources/
    │   ├── bootstrap.yml
    │   ├── application.yml
    │   ├── mapper/            # 13 个 XML 映射文件
    │   │   ├── ContractMapper.xml
    │   │   ├── ExpenseMapper.xml
    │   │   ├── InvoiceMapper.xml
    │   │   ├── OpsTicketMapper.xml
    │   │   ├── PaymentMapper.xml
    │   │   ├── PurchaseMapper.xml
    │   │   ├── RateCardMapper.xml
    │   │   ├── RevenueMapper.xml
    │   │   ├── RiskMapper.xml
    │   │   ├── RulePackMapper.xml
    │   │   ├── TimeEntryMapper.xml
    │   │   ├── WarrantyMapper.xml
    │   │       └── WbsTaskMapper.xml
    │   └── config/            # 原 nacos-config（已重命名）
    │       ├── ydsz-pmis-project-dev.yaml
    │       ├── ydsz-pmis-project-sit.yaml
    │       └── ydsz-pmis-project-uat.yaml
    └── test/
```

## 配置文件

| 变量 | 说明 |
|---|---|
| `SEATA_SERVER_ADDR` | Seata Server（默认 `127.0.0.1:8091`） |
| `MINIO_*` | MinIO 配置（同 system） |
| `DB_*` / `REDIS_*` | 数据库与缓存 |

`seata.tx-service-group = pmis-tx-group`（与 deploy/seata 部署的分组对应）。

## 启动

```bash
cd ydsz-pmis-backend
mvn -pl ydsz-pmis-common,ydsz-pmis-literule -am install -DskipTests
mvn -pl ydsz-pmis-project spring-boot:run
```

## 测试

```bash
mvn -pl ydsz-pmis-project -am test
# 覆盖率：target/site/jacoco/index.html
```

## Feign 接口

### 主动调用

- `InitiationFeignClient` → ydsz-pmis-project（自身）
- `ExecutionClient` → ydsz-pmis-project
- `MessageFeignClient` → ydsz-pmis-message（发送通知）

### 被调用

- ydsz-pmis-cronjob → 通过 `ExecutionClient` 拉取任务数据
- ydsz-pmis-agent → 通过 `InitiationFeignClient` / `ExecutionClient` 取数
- ydsz-pmis-workflow → 通过 `InitiationFeignClient` 触发立项审批

## 常见问题

### Q1：Seata 事务回滚失败

检查：
1. Seata Server 是否启动（`http://127.0.0.1:7091`）
2. Nacos 中 `seata-client.properties` 配置的 `vgroup-mapping.pmis-tx-group` 是否映射到 `default`
3. 数据源是否被 Seata 代理（`enable-auto-data-source-proxy: true`）

### Q2：EVM 看板数据延迟

EVM 快照由 cronjob 模块每日凌晨 4 点重算。可手动触发：
`POST /evm/snapshot?projectId=xxx&date=2026-07-08`

### Q3：分页查询慢

请使用游标分页（`CursorPageQuery`）替代 offset 分页，索引走主键避免深度分页慢 SQL。

---

> 本模块是**业务最重**的模块，**严禁**在 Controller 直接写业务逻辑，必须 Service 化。
> 所有跨服务调用必须走 `NameAssembler`（Feign + try-catch 降级），禁止直接远程调用。
