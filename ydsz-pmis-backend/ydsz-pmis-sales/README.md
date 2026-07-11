# ydsz-pmis-sales

> 商务销售服务（Sales）

## 模块定位

| 属性 | 值 |
|---|---|
| **类型** | 部署单元（独立启动） |
| **端口** | **9010** |
| **服务名** | `ydsz-pmis-sales` |
| **数据库** | PostgreSQL（共享主库，6 张表） |
| **依赖** | Nacos、PostgreSQL、Redis、MinIO |

## 核心职责

PMIS 商务销售模块，覆盖**商机漏斗 → 合同签署**全流程。

### 1. 业务链路

```
商机（Opportunity）
   ↓ A/B/C 分级
   ↓ 跟进（拜访/电话/报价/谈判）
   ↓ 赢单率预测
   ↓
合同（Contract）
   ├── 模板（ContractTemplate）
   ├── 补充协议（ContractSupplement）
   └── 合同变更（ContractChange）
```

### 2. 关键能力

| 能力 | 说明 |
|---|---|
| **商机管理** | 销售线索录入、A/B/C 分级、赢率预测、漏斗视图 |
| **商机跟进** | 拜访记录、电话记录、报价历史、谈判痕迹 |
| **合同管理** | 合同主表、签署状态、付款条款、关联客户 |
| **合同模板** | 标准合同模板库（固定/框架/单次/里程碑） |
| **补充协议** | 合同补充条款管理 |
| **合同变更** | 合同变更记录（价格/日期/条款变更） |

### 3. 关键 Controller

| 路径前缀 | 业务域 |
|---|---|
| `/opportunity` | 商机 |
| `/opportunity-follow` | 商机跟进 |
| `/contract` | 合同 |
| `/contract-supplement` | 补充协议 |
| `/contract-change` | 合同变更 |
| `/contract-template` | 合同模板 |
| `/data` | 跨域数据查询（Feign 对外接口） |

## 数据库表设计

本模块持有 **6 张表**，表归属依据：`ydsz-pmis-sales/src/main/java/.../infra/mapper/`

| 表名 | 说明 | Mapper |
|---|---|---|
| `pmis_project_opportunity` | 商机主表（A/B/C 分级，漏斗管理） | OpportunityMapper |
| `pmis_project_opportunity_follow` | 商机跟进记录（拜访/电话/报价/谈判） | OpportunityFollowMapper |
| `pmis_project_contract` | 合同主表（签署状态/付款条款） | ContractMapper |
| `pmis_project_contract_supplement` | 合同补充协议 | ContractSupplementMapper |
| `pmis_project_contract_change` | 合同变更记录 | ContractChangeMapper |
| `pmis_project_contract_template` | 合同模板库 | ContractTemplateMapper |

> **索引关键点**：
> - `pmis_project_opportunity(tenant_id, customer_id, status)` 漏斗视图
> - `pmis_project_opportunity(tenant_id, created_at DESC)` 商机中心列表
> - `pmis_project_opportunity(expected_sign_date)` 赢单率加权收入预测
> - `pmis_project_opportunity_follow(opportunity_id, follow_time DESC)` 时间线展示

## DDD 架构

```
ydsz-pmis-sales/
├── domain/
│   ├── entity/         # 领域实体 (6 个 DO)
│   ├── dto/            # 请求 DTO (10 个)
│   ├── enums/          # 枚举 (6 个)
│   └── package-info.java
├── infra/
│   └── mapper/         # MyBatis Mapper (6 个接口 + XML)
├── server/
│   ├── engine/         # 业务引擎
│   │   ├── WinRateEvaluator.java      # 赢单率评估
│   │   └── ContractRiskEvaluator.java # 合同风险评估
│   ├── service/       # 领域服务 (6 个接口 + 8 个实现)
│   │   ├── opportunity/   # 商机相关
│   │   └── contract/      # 合同相关
│   └── package-info.java
├── api/                # Feign Client 对外接口
│   └── SalesDataClient.java  # 跨域数据查询 (7 个方法)
├── web/
│   ├── controller/     # REST Controller (7 个)
│   ├── config/
│   └── SalesApplication.java
└── resources/
    ├── bootstrap.yml
    ├── application.yml
    └── mapper/         # MyBatis XML 映射 (6 个)
```

## Feign Client

### 对外接口（被其他服务调用）

`com.njydsz.pmis.sales.api.SalesDataClient`

| 方法 | 说明 | 调用方 |
|---|---|---|
| `getOpportunityById` | 根据商机 ID 查询 | project |
| `getContractById` | 根据合同 ID 查询 | project |
| `listContractsByProjectId` | 查询项目相关合同 | project |
| `sumContractAmount` | 合同金额汇总 | project (报表) |
| `getContractByCode` | 根据合同号查询 | project |
| `getOpportunityByCode` | 根据商机号查询 | project |
| `getOpportunityStats` | 商机统计 | project (驾驶舱) |

## 配置文件

| 变量 | 说明 |
|---|---|
| `DB_*` / `REDIS_*` | 数据库与缓存 |
| `NACOS_*` | 注册中心与配置中心 |

## 启动

```bash
cd ydsz-pmis-backend
mvn -pl ydsz-pmis-sales spring-boot:run
```

## 测试

```bash
mvn -pl ydsz-pmis-sales -am test
# 覆盖率：target/site/jacoco/index.html
```

## 跨服务集成

### 调用其他服务（通过 Feign）

- `ydsz-pmis-project` → 获取项目信息（商机转立项）
- `ydsz-pmis-userinfo` → 获取用户/部门信息

### 被其他服务调用

- `ydsz-pmis-project` → 查询商机/合同信息
- `ydsz-pmis-finance` → 查询合同金额（开票/付款）
- `ydsz-pmis-workflow` → 合同签署审批

## 常见问题

### Q1：赢率预测不准

A：赢率评估基于 `WinRateEvaluator`，考虑因素包括：
- 商机级别（A/B/C）
- 预计签约日期
- 跟进频次
- 竞争对手分析

可通过调整权重系数提升准确率（`server/engine/WinRateEvaluator.java`）。

### Q2：合同模板怎么配置

A：合同模板支持 4 种类型（固定/框架/单次/里程碑），通过 `ContractTemplateController` 维护模板库。

---

> 本模块采用 **DDD 五层架构**，所有跨服务调用必须走 **Feign Client**，禁止直连其他模块的 Mapper。