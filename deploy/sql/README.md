# PMIS · 数据库 Schema 管理

> 本目录是 PMIS 主库 schema 的**唯一事实源(Single Source of Truth)**。
> 本文回答三个问题:用什么管 schema、怎么改 schema、怎么在新环境初始化。
> 文档版本:v3.0 · 2026-07-12(DDD 拆分:project → sales/finance/project + literule 迁移)

---

## 目录

1. [核心决策:单文件 V1.0.0.sql,无增量脚本](#1-核心决策单文件-v100sql无增量脚本)
2. [为什么不要增量脚本](#2-为什么不要增量脚本)
3. [目录结构](#3-目录结构)
4. [Schema 变更流程](#4-schema-变更流程)
5. [新环境初始化流程](#5-新环境初始化流程)
6. [禁止事项清单](#6-禁止事项清单)
7. [常见问题(FAQ)](#7-常见问题faq)

---

## 1. 核心决策:单文件 V1.0.0.sql,无增量脚本

**PMIS 项目当前未上线,仍处于开发阶段,`deploy/sql/` 下只允许存在一个 SQL 文件 —— `V1.0.0.sql`**。

- 所有建表 / 字段变更 / 索引 / 约束 / 视图 / 种子数据,**一律直接编辑 `V1.0.0.sql`**;
- 禁止新增 `V1.0.1.sql` / `V1.1.0.sql` / `patch_*.sql` / `migration_*.sql` / `*__*.sql` 等任何「增量脚本」;
- 禁止引入 Flyway / Liquibase / jOOQ DDL / 自研 `db-migration` 启动器等任何自动 schema-migration 框架。

```text
deploy/sql/
├── V1.0.0.sql   # 唯一 SQL 文件:完整 DDL + DML(给新环境一次性初始化用)
└── README.md    # 本文件
```

---

## 2. 为什么不要增量脚本

| 维度 | 增量脚本(传统模式) | 当前方案(单文件 V1.0.0.sql) |
|---|---|---|
| **脑力负担** | 必须按版本号顺序串接,漏一个就破链 | 永远只有一个文件,看到的就是全量 |
| **合并冲突** | 多人同时改不同 V 文件 → git rebase 地狱 | 所有人编辑同一个文件 → 冲突点集中、可见、可消解 |
| **新环境初始化** | 必须按顺序跑 V1.0.0、V1.0.1、V1.0.2…… | `psql -f V1.0.0.sql` 一把梭 |
| **未上线项目的现实** | 还没发版就要维护 N 个 V 文件,无意义 | 还没发版,历史全在 init 文件里,所见即所得 |
| **回顾成本** | 「这个字段啥时候加的?」要 git log + 版本号推算 | `git log -p V1.0.0.sql` 直接看到 |

**结论**:项目未上线、迭代节奏快、变更密度高时,**单文件 V1.0.0.sql** 是最低负担的方案。等真正上线、并出现「多环境、按发布窗口、零停机升级」的诉求时,再评估把 `V1.0.0.sql` 拆为 `V*__*.sql` 序列(届时本 README 同步更新,且**只在那一刻**才允许出现增量脚本)。

---

## 3. 目录结构

```text
deploy/sql/
├── V1.0.0.sql              # 唯一汇总 SQL 文件(完整 DDL + DML,新环境初始化用)
├── README.md               # 本文件
└── modules/                # 按后端服务拆分的独立 SQL(便于单独初始化/审查)
    ├── V1.0.0_all.sql          # 模块引用脚本(\i 依次引用各子模块,等价于 V1.0.0.sql)
    ├── V1.0.0_common.sql       # 已合并至 system(存根, ydsz-pmis-common 非独立服务)
    ├── V1.0.0_system.sql       # 系统管理 (配置/文件/审计/导出/扩展/触发器/undo_log)
    ├── V1.0.0_userinfo.sql     # 用户信息 (认证/用户/组织/权限/资源/考勤)
    ├── V1.0.0_sales.sql        # 商务销售 (商机/合同/合同模板, port 9010, 6 张表)
    ├── V1.0.0_finance.sql      # 财务会计 (发票/付款/利润/对账, port 9011, 8 张表)
    ├── V1.0.0_project.sql      # 项目执行 (立项/WBS/工时/风险/交付/售后, port 9003, 20 张表)
    ├── V1.0.0_cronjob.sql      # 定时任务 (作业/DAG/调度/告警/日志/配额)
    ├── V1.0.0_message.sql      # 消息中心 (通知/模板/回执/批量/灰度/偏好)
    ├── V1.0.0_workflow.sql     # 工作流引擎 (定义/实例/委派/通知/DMN/集成/AI辅助)
    ├── V1.0.0_agent.sql        # AI Agent (Agent/编排/知识库/工具/人机协同)
    ├── V1.0.0_literule.sql     # 规则引擎 (规则/决策表/评分卡/AB测试/变量 + 8 张业务表)
    └── V1.0.0_local_message.sql # 本地消息表 (分布式事务, pmis_local_message)
```

### 3.1 汇总文件 vs 模块文件

| 场景 | 使用文件 | 说明 |
|---|---|---|
| **新环境完整初始化** | `V1.0.0.sql` | 一把梭,所有 DDL + DML 都在里面 |
| **新环境模块化初始化** | `modules/V1.0.0_all.sql` | 通过 `\i` 依次引用各子模块,等价于 V1.0.0.sql |
| **单独初始化某模块** | `modules/V1.0.0_{module}.sql` | 仅初始化对应模块的表(需先跑 system 模块,因含全局扩展/触发器) |
| **Schema 变更** | 直接编辑 `V1.0.0.sql` | **唯一事实源**,模块文件由拆分生成 |

### 3.2 模块与表的数量分布(2026-07-12 DDD 拆分后)

| 模块 | 端口 | 表数量 | 主要表归属说明 |
|---|---|---|---|
| common | - | 0(存根) | **已合并至 system**。`ydsz-pmis-common` 是公共依赖库(lib),非独立后端服务,无 Mapper/Service,不持有独立 DDL |
| system | - | 11 | `pmis_config` / `pmis_tenant_quota` / `pmis_file` / `pmis_operation_log`(+ DEFAULT 分区) / `pmis_login_audit` / `pmis_data_export_audit` / `pmis_dict_version` / `pmis_report_subscription` / `pmis_export_record` / `pmis_meta_schema_version` + 全局 PG 扩展 / PL/pgSQL 函数 / 触发器 / undo_log |
| userinfo | - | 22 | RBAC(`pmis_role` / `pmis_permission` / `pmis_user_*`)+ 用户/部门/岗位/字典主表(`pmis_dict_type` / `pmis_dict_item` / `pmis_department` / `pmis_employee` / `pmis_position`)+ 职级系列(**`pmis_rank` / `pmis_rank_rate`**,RankMapper 在 userinfo)+ 资源/考勤(`pmis_resource_assignment` / `pmis_bench_record` / `pmis_attendance` / `pmis_overtime` / `pmis_leave`)+ 兼职/外包费率 |
| **project** | **9003** | **34** | 立项/预算/门审(`pmis_project_initiation` / `pmis_project_budget_item` / `pmis_project_gate_review`)+ 执行-WBS/工时(`pmis_execution_wbs_task` / `pmis_execution_time_entry`)+ 执行-成本/采购(`pmis_cost_allocation` / `pmis_cost_purchase`)+ EVM/费率(`pmis_evm_measure` / `pmis_rate_card` / `pmis_rate_internal`)+ 风险/变更(`pmis_execution_risk` / `pmis_project_change`)+ 交付/结项(`pmis_execution_delivery_standard` / `pmis_execution_delivery_item` / `pmis_execution_closure`)+ 售后/工单/满意度(`pmis_warranty` / `pmis_ops_ticket` / `pmis_satisfaction`)+ 资源利用/预警(`pmis_billable_utilization_snapshot` / `pmis_alert_dispatch`)+ **原 sales 6 张**(商机/合同)+ **原 finance 8 张**(`pmis_project_expense` / `pmis_project_revenue` / `pmis_project_profit_snapshot` / `pmis_project_profit_simulation` / `pmis_project_invoice` / `pmis_project_payment` / `pmis_project_customer_credit` / `pmis_project_reconcile_daily`) |
| cronjob | - | 20 | `pmis_job`(任务定义主表)+ 18 张 `pmis_job_*` 子表(节点/日志/DAG/告警/历史/慢日志/产物/WebHook) |
| message | - | 24 | `pmis_msg_*` (含 7 张月度分区表) + `pmis_notification_*` + 模板/回执/统计 |
| workflow | - | 34 | `pmis_flow_*` + 流程审计日志 `pmis_flow_audit_log` + 视图 |
| agent | - | 12 | `pmis_agent_*` / `pmis_knowledge_*` / `pmis_token_*` / `pmis_tool_*` / `pmis_hitl_*` / `pmis_mcp_*` |
| literule | - | 17 | 规则引擎主表 9 张(`pmis_rule_def` / `pmis_rule_version` / 规则模板/测试/变量/链/依赖/包/安装)+ **业务表 8 张**(`pmis_rule_execution_trace` / `pmis_rule_decision_table` / `pmis_rule_canary_bucket` / `pmis_rule_scorecard` / `pmis_rule_decision_tree` / `pmis_rule_script` / `pmis_rule_ab_policy` / `pmis_rule_ab_rollback`, 2026-07-12 从 project 迁移) |
| local_message | - | 1 | 本地消息表(`pmis_local_message`, 分布式事务) |
| **合计** | - | **167** | |

### 3.3 模块拆分规则(2026-07-12 DDD 拆分后)

**核心原则:DDL 跟着「物理 Mapper 实际所在后端模块」走,而不是按表名「看起来像哪个模块」**。

- 任何表的归属以 `ydsz-pmis-{module}/src/main/java/.../infra/mapper/XxxMapper.java` 的物理路径为准

#### 2026-07-12 DDD 拆分（project → sales/finance/project + literule 迁移）

原 `V1.0.0_project.sql` (42 张表) 已拆分为 4 个模块:

| 源表 | 迁移至 | 理由 |
|---|---|---|
| `pmis_project_opportunity` / `pmis_project_opportunity_follow` | `V1.0.0_sales.sql` | Mapper 在 `sales/infra/mapper/` |
| `pmis_project_contract` / `pmis_project_contract_supplement` / `pmis_project_contract_change` / `pmis_project_contract_template` | `V1.0.0_sales.sql` | Mapper 在 `sales/infra/mapper/` |
| `pmis_cost_expense` / `pmis_profit_revenue` / `pmis_profit_snapshot` / `pmis_profit_simulation` | `V1.0.0_finance.sql` | Mapper 在 `finance/infra/mapper/` |
| `pmis_finance_invoice` / `pmis_finance_payment` / `pmis_finance_customer_credit` / `pmis_reconcile_daily` | `V1.0.0_finance.sql` | Mapper 在 `finance/infra/mapper/` |
| `pmis_rule_execution_trace` / `pmis_rule_decision_table` / `pmis_rule_canary_bucket` / `pmis_rule_scorecard` / `pmis_rule_decision_tree` / `pmis_rule_script` / `pmis_rule_ab_policy` / `pmis_rule_ab_rollback` | `V1.0.0_literule.sql` (追加) | Mapper 已从 `project` 迁移至 `literule/infra/mapper/` |

剩余 20 张表保留在 `V1.0.0_project.sql`:
- 立项/预算/门审: `pmis_project_initiation` / `pmis_project_budget_item` / `pmis_project_gate_review`
- 执行-WBS/工时: `pmis_execution_wbs_task` / `pmis_execution_time_entry`
- 执行-成本/采购: `pmis_cost_allocation` / `pmis_cost_purchase`
- EVM/费率: `pmis_evm_measure` / `pmis_rate_card` / `pmis_rate_internal`
- 风险/变更: `pmis_execution_risk` / `pmis_project_change`
- 交付/结项: `pmis_execution_delivery_standard` / `pmis_execution_delivery_item` / `pmis_execution_closure`
- 售后/工单/满意度: `pmis_warranty` / `pmis_ops_ticket` / `pmis_satisfaction`
- 资源利用/预警: `pmis_billable_utilization_snapshot` / `pmis_alert_dispatch`

#### 2026-07-16 合并（sales/finance → project，表前缀统一）

sales 和 finance 模块已合并回 project 模块，`V1.0.0_sales.sql` 和 `V1.0.0_finance.sql` 已删除，内容合并至 `V1.0.0_project.sql`（34 张表）。finance 的 8 张表已重命名为 `pmis_project_*` 前缀:

| 旧表名 | 新表名 |
|---|---|
| `pmis_cost_expense` | `pmis_project_expense` |
| `pmis_profit_revenue` | `pmis_project_revenue` |
| `pmis_profit_snapshot` | `pmis_project_profit_snapshot` |
| `pmis_finance_invoice` | `pmis_project_invoice` |
| `pmis_finance_payment` | `pmis_project_payment` |
| `pmis_finance_customer_credit` | `pmis_project_customer_credit` |
| `pmis_profit_simulation` | `pmis_project_profit_simulation` |
| `pmis_reconcile_daily` | `pmis_project_reconcile_daily` |

> 索引名（如 `idx_pmis_finance_invoice_trace`）保持不变，仅表名引用已更新。

#### 历史拆分规则(2026-07-10 重构)

- 职级表原命名 `pmis_job_level` 带 `job_` 易与 cronjob 任务引擎混淆,已重命名为 `pmis_rank` / `pmis_rank_rate`,`RankMapper` 在 `userinfo` 模块 → 归 `V1.0.0_userinfo.sql`,**不归 cronjob**
- `pmis_dict_type` / `pmis_dict_item` 主表 Mapper 在 userinfo → 归 userinfo;`pmis_dict_version` 字典版本 Mapper 在 system → 归 system
- 通用预警派发表 `pmis_alert_dispatch` 由 project + cronjob + agent 共用 → 归 `V1.0.0_project.sql`(物理 Mapper 在 project)
- `common` 脚本**已合并至 system**。`ydsz-pmis-common` 不是独立后端服务,是公共依赖库(lib),无 Mapper/Service,不持有独立 DDL。全局 PG 扩展 / PL/pgSQL 函数 / 触发器 / undo_log 统一由 `V1.0.0_system.sql` 承载

文件顶部使用清晰的 `=====` 注释块对每张表/视图进行分段,便于 PR Review 与 diff 阅读。

---

## 4. Schema 变更流程

### 4.1 改 schema 的标准动作

1. **本地编辑** `deploy/sql/V1.0.0.sql`,在对应表附近追加 `ALTER TABLE` / `CREATE INDEX` / `COMMENT` 等语句
   - **不要**新建任何 `V*.sql` 增量文件
   - 字段新增请紧跟原表 `CREATE TABLE` 块,并在文件顶部的目录注释里登记
2. **本地验证**:`psql -f deploy/sql/V1.0.0.sql` 跑一次,确认 DDL 全部通过(`-v ON_ERROR_STOP=1`)
3. **提交 PR**:标题格式 `schema: <表名> <变更摘要>`,例如 `schema: pmis_flow_run_task add iter_var`
4. **PR 必含内容**:
   - `V1.0.0.sql` 的 diff
   - 字段/表/索引含义说明(写到 PR 描述,而不是文件头)
   - 对应的 Java 实体 / Mapper / Service 变更(保持 entity 与 SQL 严格对齐)

### 4.2 单文件合并的实操要点

- `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` 是允许的(新环境跑时不会重复加)
- 历史变更以「在 V1.0.0.sql 中直接合并为最终形态」为终态,不要保留 `V1.0.1 时期加了什么` 的历史切片
- 如果某张表被废弃 → 直接在 `V1.0.0.sql` 里 `DROP TABLE IF EXISTS`,不留 `-- [SKIPPED-CLEANUP]` 之类的历史注释(可选,见下条)
- 长期保留 `-- [SKIPPED-CLEANUP]` 注释(避免被误重建)只在历史需要时使用,默认不必加

### 4.3 校验机制(无 Flyway 时的漂移检测)

通过 PostgreSQL 自带能力 + 项目自建快照:

```sql
-- 当前 schema 的 fingerprint(部署到任何环境都跑一次)
SELECT pg_catalog.pg_get_userbyid(relowner) AS owner,
       relname AS table_name,
       pg_catalog.pg_total_relation_size(oid) AS size
  FROM pg_catalog.pg_class
 WHERE relkind = 'r' AND relnamespace = 'public'::regnamespace
 ORDER BY relname;
```

未来可在 CI 引入 `pg_dump --schema-only` 与 `V1.0.0.sql` 解析出的期望 schema 做 diff(批次 29 评估)。

---

## 5. 新环境初始化流程

```bash
# 1. 创建库（通过 PGPASSWORD 环境变量传入数据库密码，请替换为你的实际密码）
PGPASSWORD=<your-postgres-password> createdb -h 127.0.0.1 -U postgres ydsz-pmis

# 2. 单文件初始化(本项目唯一的 SQL 文件,所有 DDL + DML 都在里面)
PGPASSWORD=<your-postgres-password> psql -h 127.0.0.1 -U postgres -d ydsz-pmis \
  -v ON_ERROR_STOP=1 \
  -f deploy/sql/V1.0.0.sql

#    或者使用模块化引用脚本(等价于上面的单文件,按模块顺序执行):
# PGPASSWORD=<your-postgres-password> psql -h 127.0.0.1 -U postgres -d ydsz-pmis \
#   -v ON_ERROR_STOP=1 \
#   -f deploy/sql/modules/V1.0.0_all.sql

# 3. 导入 Nacos 配置
./deploy/ubuntu/scripts/import-nacos-config.sh pmis dev

# 4. 启动 PMIS 7 个服务
./deploy/ubuntu/scripts/start-all.sh
```

详细中间件部署见 [`../README.md §3`](../README.md#3-一键快速开始3-步)。

> **注**:本节「初始化」对应「全新空库」场景。项目未上线,**不存在「存量环境升级」场景** —— 任何 schema 调整都直接改 `V1.0.0.sql` 即可,所有开发/测试库统一从该单文件初始化。

---

## 6. 禁止事项清单

| # | 禁止项 | 反例 |
|---|---|---|
| 1 | 新增任何 `V*.sql` 增量脚本 | `V1.0.1__xxx.sql` / `V1.1.0__yyy.sql` / `patch_*.sql` |
| 2 | 引入 Flyway / Liquibase 框架 | `pom.xml` 引入 `flyway-core` / `liquibase-core` |
| 3 | 在 `application*.yml` 中配 `spring.flyway.*` / `spring.liquibase.*` | 启用自动迁移 |
| 4 | 在 `application*.yml` 中配 `spring.sql.init.*` | 用 Spring 自带 init 脚本 |
| 5 | 在 `src/main/resources/db/migration/` 放 SQL | 任何业务模块 |
| 6 | 应用代码中执行 DDL | `@PostConstruct` 里 `jdbcTemplate.execute("CREATE TABLE...")` |
| 7 | 跳过 PR Review 直接 push V1.0.0.sql 的 schema 变更 | DBA + 后端未确认 |
| 8 | 在 `V1.0.0.sql` 里加 BEGIN/COMMIT 包装单条 DDL | PG 已支持 DDL 事务,过度包装影响可读性 |
| 9 | 用 `V1.0.0.sql` 给已有数据的存量环境覆盖执行 | 任何已有数据的环境都必须用 pg_dump 备份后手动 ALTER |

CI 会通过以下方式做静态扫描拦截:

```bash
# 1) 禁止增量脚本
git diff --name-only origin/main -- deploy/sql/ \
  | grep -E '^deploy/sql/V(1\.[1-9]|[2-9]\.)' && exit 1

# 2) 禁止 Flyway / Liquibase 依赖
mvn -pl ydsz-pmis-backend -am dependency:tree | grep -iE 'flyway|liquibase' && exit 1
```

---

## 7. 常见问题(FAQ)

### Q1.为什么不要增量脚本?

A:项目未上线、迭代密度高,增量脚本只会制造合并冲突、版本串接负担和「为啥 V1.0.3 没跑」类的运维疑问。所有 schema 直接编辑 `V1.0.0.sql`,所见即所得,PR Review 也最简单。

### Q2.什么时候才允许出现 V1.0.1.sql?

A:**项目正式上线后**,且同时满足以下条件**之一**时:
- 月均 DB 升级 ≥ 4 次
- 多个团队并行改表
- DBA 资源紧张,无法人工 review

届时把 `V1.0.0.sql` 拆为 `V1.0.1__*.sql` / `V1.0.2__*.sql` 序列,**且在那一刻才允许存在增量脚本**。本 README 同步更新。

### Q3.万一我已经写了 V1.0.1 / V1.0.2 怎么办?

A:把里面的 `ALTER TABLE` / `COMMENT` 等 DDL **直接复制粘贴到 `V1.0.0.sql` 对应表附近**,然后**删除 V1.0.1 / V1.0.2 文件**。`V1.0.0.sql` 在新环境一次性跑时,所有列/索引/约束自然就位。

### Q4.`pmis_migration_log` 表是什么?

A:**和 DB schema migration 无关**。它是 `ydsz-pmis-common::EncryptedFieldMigrationService` 用来跟踪「敏感字段从明文 → 密文」灰度切换的审计表,业务向的、一次性的、可清空。

### Q5.`pmis_database_change_log` 表为什么在 `PmisTenantLineHandler` 的忽略列表里?

A:**历史占位**。该类提到 `pmis_database_change_log*` 是 Liquibase 默认的 changelog 表名,代码里「兼容预留」以防万一。但 PMIS **从未启用 Liquibase**,该表在生产环境中**不存在**,此忽略项实际是 no-op。**未来若引入 Liquibase,该忽略项即可激活;不引入则保持原样。**

### Q6.回滚怎么办?

A:项目未上线,**没有生产数据** → 直接 `dropdb` + 重新 `psql -f V1.0.0.sql` 即可。如果一定要做单次 schema 变更的回滚,使用 `git revert` 还原 `V1.0.0.sql` 的对应 commit,然后重新跑单文件。

---

## 8. 相关链接

- [`../README.md`](../README.md) — 部署总入口
- [`../common/README.md`](../common/README.md) — 中间件配置 + 通用 SQL(XXL-Job)
- [`../../ydsz-pmis-backend/Dockerfile`](../../ydsz-pmis-backend/Dockerfile) — 后端多阶段构建
- [`../../README.md`](../../README.md) — 项目仓库入口
- 项目记忆:`.trae-cn/memory/projects/-d-Code-ydsz-ydsz-pmis/project_memory.md` — Hard Constraints

---

## 9. CHANGELOG — 任务 ID 映射表

> 互联网大厂标准:每次 schema 优化必须能通过「任务 ID」反查到 PR / 讨论。
> 本节记录所有针对 `V1.0.0.sql` 做过的批次元数据优化任务,按优先级 (P0/P1/P2/P3) 排序。

### 9.1 任务 ID 命名规范

- **H2.x** — 历史修复 (Hotfix,来自 GAP 报告或线上事故)
- **GAP** — 通用规范差距 (General Alignment Pitfall)
- **P0-x** — 阻塞性、立即修复
- **P1-x** — 高优先级、本迭代完成
- **P2-x** — 中优先级、可后续迭代
- **P3-x** — 低优先级、长期演进

### 9.2 任务清单(2026-07-12 DDD 拆分批次,本 README v3.0 更新版)

| 任务 ID | 类别 | 章节 | 内容概述 | 状态 |
|---|---|---|---|---|
| P1-1 | 拆分 | DDD 拆分 | 原 `V1.0.0_project.sql` (42 张表) 拆分为 `V1.0.0_sales.sql` (6 表) / `V1.0.0_finance.sql` (8 表) / `V1.0.0_project.sql` (20 表) + 8 张 literule 业务表追加到 `V1.0.0_literule.sql` | DONE |
| P1-2 | 拆分 | DDD 拆分 | 表归属基于物理 Mapper 位置:`sales/infra/mapper/` → `V1.0.0_sales.sql`, `finance/infra/mapper/` → `V1.0.0_finance.sql`, `project/infra/mapper/` → `V1.0.0_project.sql`, `literule/infra/mapper/` → `V1.0.0_literule.sql` | DONE |
| P1-3 | 文档 | 模块 README | 创建 `ydsz-pmis-sales/README.md` (商机/合同管理,6 表,Feign Client 对外 7 方法) | DONE |
| P1-4 | 文档 | 模块 README | 创建 `ydsz-pmis-finance/README.md` (财务会计,8 表,Feign Client 对外 13 方法) | DONE |
| P1-5 | 文档 | 模块 README | 更新 `ydsz-pmis-project/README.md` (项目执行,20 表,DDD 五层架构) | DONE |
| P1-6 | 文档 | SQL README | 更新 `deploy/sql/README.md` 至 v3.0,反映 sales/finance/project 拆分,更新目录结构/表分布/拆分规则 | DONE |
| P1-7 | 引用 | V1.0.0_all.sql | 更新 `modules/V1.0.0_all.sql` 引用顺序:system → userinfo → **sales → finance → project** → cronjob → message → workflow → agent → literule | DONE |

### 9.3 历史任务清单(2026-07-06 批次)

| 任务 ID | 类别 | 章节 | 内容概述 | 状态 |
|---|---|---|---|---|
| H2.7 / P1-1 | 字段类型统一 | [060] | `pmis_flow_run_task.assignor_id` 等 7 处字段类型从 BIGINT → VARCHAR(64) | DONE |
| P0-3 | 表合并 | [061] | `pmis_export_record` + `pmis_report_export_record` 合并为单表(source / subscription_id / report_type) | DONE |
| P1-4 | 分区 | [062] | `pmis_operation_log` / `pmis_flow_audit_log` 月度 RANGE 分区(24 个月 + DEFAULT 兜底) | DONE |
| P1-5 | 触发器 | [063] | 通用 `pmis_set_updated_at()` 函数 + 15 张核心业务表挂载 | DONE |
| P1-6 | 清理 | 文件头 + 全文 | 移除所有 `[SKIPPED-CLEANUP]` / `[SKIPPED-FWD-REF]` 残留注释 | DONE |
| P1-7 | 索引 | [064] | `provider_trace_id` 索引全量补齐: 75/75 张表已覆盖 (0 缺失) | DONE |
| P2-8 | 注释 | [062] DEFAULT 兜底 | 2 张 DEFAULT 分区表补齐 `COMMENT ON TABLE`,覆盖率 100% (112/112) | DONE |
| P2-9 | 元数据 | [065] | 新增 `pmis_meta_schema_version` 表 + `pmis_view_current_schema_version` 视图 | DONE |
| P2-10 | 视图安全 | [015] / [037] / [065] | 7 个 `pmis_view_*` 全部加 `WITH (security_invoker = true)` | DONE |
| P2-11 | 文档 | 本节 | 在 `deploy/sql/README.md` 引入 §9 CHANGELOG 章节,记录任务 ID 映射 | DONE |

### 9.3 任务 ID 反查

任何 PR 描述中只要写明 `Task: P1-7`,即代表:
- 改动了 `deploy/sql/V1.0.0.sql` 的 [064] 章节
- 涉及 63 张 `pmis_*` 表的 `provider_trace_id` 索引
- 通过 `check-provider-trace-index.ps1` 校验为 75/75 覆盖

CI 静态扫描会在 PR 标题包含 `Task: <ID>` 时,自动跑对应的检查脚本。

### 9.4 后续待办(P3 级)

| 任务 ID | 类别 | 章节 | 内容概述 | 优先级 |
|---|---|---|---|---|
| P3-13 | 性能 | TBD | 大表冷热数据分层(`pg_partman` / 冷分区归档到 OSS) | LOW |
| P3-14 | 安全 | TBD | 敏感字段加密落盘(手机号/身份证/银行卡 SM4 加密列) | LOW |
| P3-15 | 审计 | TBD | `pmis_data_export_audit` 接入 OPLOG,支持 UDF 检索 | LOW |

> **注**:P3 任务非阻塞,可由后续迭代按业务节奏逐步落地。
