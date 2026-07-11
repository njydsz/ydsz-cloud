# ydsz-pmis-finance

> 财务会计服务（Finance）

## 模块定位

| 属性 | 值 |
|---|---|
| **类型** | 部署单元（独立启动） |
| **端口** | **9011** |
| **服务名** | `ydsz-pmis-finance` |
| **数据库** | PostgreSQL（共享主库，8 张表） |
| **依赖** | Nacos、PostgreSQL、Redis、MinIO |

## 核心职责

PMIS 财务会计模块，覆盖**收入确认、成本核算、利润分析**全链路。

### 1. 业务链路

```
费用报销（Expense）
   ↓ 审批流程
   ↓ 成本归集
   ↓
收入确认（Revenue）
   ↓ 开票（Invoice）
   ↓ 付款（Payment）
   ↓
利润核算（ProfitSnapshot）
   ├── 利润快照（按项目/日期）
   └── 利润模拟（多版本对比）
   ↓
对账（DailyReconcile）
   └── 月度对账结果
```

### 2. 关键能力

| 能力 | 说明 |
|---|---|
| **费用管理** | 费用报销、成本归集、审批流程 |
| **收入确认** | 按里程碑/按工时/按比例确认收入 |
| **开票管理** | 开票申请、发票状态、收款关联 |
| **付款管理** | 付款申请、付款状态、合同关联 |
| **利润核算** | 利润快照、利润模拟、多版本对比 |
| **客户信用** | 客户信用额度管理、超限预警 |
| **日对账** | 收入/成本/利润月度对账 |

### 3. 关键 Controller

| 路径前缀 | 业务域 |
|---|---|
| `/expense` | 费用报销 |
| `/revenue` | 收入确认 |
| `/invoice` | 发票管理 |
| `/payment` | 付款管理 |
| `/profit` | 利润核算 |
| `/profit-simulation` | 利润模拟 |
| `/customer-credit` | 客户信用 |
| `/reconcile` | 日对账 |
| `/data` | 跨域数据查询（Feign 对外接口） |

## 数据库表设计

本模块持有 **8 张表**，表归属依据：`ydsz-pmis-finance/src/main/java/.../infra/mapper/`

| 表名 | 说明 | Mapper |
|---|---|---|
| `pmis_cost_expense` | 费用报销（差旅/采购/其他） | ExpenseMapper |
| `pmis_profit_revenue` | 收入确认记录 | RevenueMapper |
| `pmis_profit_snapshot` | 利润快照（按项目/日期） | ProfitSnapshotMapper |
| `pmis_finance_invoice` | 发票主表 | InvoiceMapper |
| `pmis_finance_payment` | 付款记录 | PaymentMapper |
| `pmis_finance_customer_credit` | 客户信用额度 | CustomerCreditMapper |
| `pmis_profit_simulation` | 利润模拟（多版本） | ProfitSimulationMapper |
| `pmis_reconcile_daily` | 月度对账结果 | DailyReconcileMapper |

> **索引关键点**：
> - `pmis_finance_invoice(contract_id, invoice_date)` 合同开票历史
> - `pmis_finance_payment(contract_id, payment_date)` 合同付款历史
> - `pmis_profit_snapshot(project_id, snapshot_date)` 项目利润快照
> - `pmis_customer_credit(customer_id)` 客户信用查询

## DDD 架构

```
ydsz-pmis-finance/
├── domain/
│   ├── entity/         # 领域实体 (8 个 DO)
│   ├── dto/            # 请求 DTO (10 个)
│   ├── enums/          # 枚举 (8 个)
│   └── package-info.java
├── infra/
│   └── mapper/         # MyBatis Mapper (8 个接口 + XML)
├── server/
│   ├── engine/         # 业务引擎
│   │   ├── ProfitCalculator.java   # 利润计算
│   │   ├── ReconcileHandler.java   # 对账处理
│   │   └── AlertCodeGen.java       # 预警编码生成
│   ├── job/            # 定时任务
│   │   └── DailyReconcileJobHandler.java  # 每日对账
│   ├── service/       # 领域服务 (10 个接口 + 18 个实现)
│   │   └── finance/    # 财务相关
│   └── package-info.java
├── api/                # Feign Client 对外接口
│   └── FinanceDataClient.java  # 跨域数据查询 (13 个方法)
├── web/
│   ├── controller/     # REST Controller (11 个)
│   ├── config/
│   └── FinanceApplication.java
└── resources/
    ├── bootstrap.yml
    ├── application.yml
    └── mapper/         # MyBatis XML 映射 (8 个)
```

## Feign Client

### 对外接口（被其他服务调用）

`com.njydsz.pmis.finance.api.FinanceDataClient`

| 方法 | 说明 | 调用方 |
|---|---|
| `getInvoiceById` | 根据发票 ID 查询 | project |
| `getPaymentById` | 根据付款 ID 查询 | project |
| `listInvoicesByContractId` | 查询合同开票记录 | project |
| `listPaymentsByContractId` | 查询合同付款记录 | project |
| `sumInvoiceAmount` | 发票金额汇总 | project (报表) |
| `sumPaymentAmount` | 付款金额汇总 | project (报表) |
| `getProfitSnapshot` | 查询利润快照 | project (驾驶舱) |
| `sumRevenue` | 收入汇总 | project (报表) |
| `sumExpense` | 费用汇总 | project (报表) |
| `getCustomerCredit` | 查询客户信用 | sales |
| `getReconcileResult` | 查询对账结果 | project (报表) |
| `getProfitSimulation` | 查询利润模拟 | project (驾驶舱) |
| `calcProjectProfit` | 计算项目利润 | project (实时计算) |

## 定时任务

| 任务 | 说明 | Cron 表达式 |
|---|---|---|
| `DailyReconcileJobHandler` | 每日对账（收入/成本/利润） | `0 0 4 * * ?` |

## 配置文件

| 变量 | 说明 |
|---|---|
| `DB_*` / `REDIS_*` | 数据库与缓存 |
| `NACOS_*` | 注册中心与配置中心 |
| `SEATA_SERVER_ADDR` | Seata Server（可选，分布式事务） |

## 启动

```bash
cd ydsz-pmis-backend
mvn -pl ydsz-pmis-finance spring-boot:run
```

## 测试

```bash
mvn -pl ydsz-pmis-finance -am test
# 覆盖率：target/site/jacoco/index.html
```

## 跨服务集成

### 调用其他服务（通过 Feign）

- `ydsz-pmis-project` → 获取项目信息（利润核算）
- `ydsz-pmis-sales` → 获取合同信息（开票/付款）
- `ydsz-pmis-userinfo` → 获取用户/部门信息

### 被其他服务调用

- `ydsz-pmis-project` → 查询发票/付款/利润快照
- `ydsz-pmis-sales` → 查询客户信用
- `ydsz-pmis-cronjob` → 触发每日对账

## 常见问题

### Q1：利润计算方式

A：利润计算基于 `ProfitCalculator`，公式：
```
利润 = 收入 - 成本
收入 = 已开票金额 + 确认未开票金额
成本 = 直接成本 + 间接成本（分摊）
```

支持双费率模式（对外报价费率 + 对内成本费率）。

### Q2：对账失败怎么办

A：检查：
1. `DailyReconcileJobHandler` 是否正常运行（XXL-Job 控制台）
2. 项目是否有收入/成本数据
3. 客户信用额度是否超限
4. 发票/付款数据是否完整

可通过 `ReconcileController` 手动触发对账。

### Q3：客户信用预警

A：客户信用预警触发条件：
- 超额开票（开票金额 > 信用额度）
- 逾期付款（付款截止日期 > 当前日期）
- 风险客户（信用等级 < B）

预警通过 `AlertCodeGen` 生成并推送到 `pmis_alert_dispatch`。

---

> 本模块采用 **DDD 五层架构**，所有跨服务调用必须走 **Feign Client**，禁止直连其他模块的 Mapper。