# PMIS · 数据库 Schema 管理

> 本目录是 PMIS 主库 schema 的**唯一事实源(Single Source of Truth)**。
> 本文回答三个问题:用什么管 schema、怎么改 schema、怎么在新环境初始化。
> 文档版本:v2.0 · 2026-07-06(显式落地「单文件 V1.0.0.sql,不引入任何增量脚本」)

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
├── V1.0.0.sql          # 唯一汇总 SQL 文件(完整 DDL + DML,新环境初始化用)
├── README.md            # 本文件
└── modules/             # 按后端子模块拆分的独立 SQL(便于单独初始化/审查)
    ├── V1.0.0_all.sql       # 模块引用脚本(\i 依次引用各子模块,等价于 V1.0.0.sql)
    ├── V1.0.0_common.sql    # 公共基础 (字典/扩展/事务/触发器/字段统一)
    ├── V1.0.0_system.sql    # 系统管理 (配置/文件/审计/导出/索引调优)
    ├── V1.0.0_userinfo.sql  # 用户信息 (认证/用户/组织/权限/资源/考勤)
    ├── V1.0.0_project.sql   # 项目管理 (商机/立项/合同/执行/财务/结项/售后/报表)
    ├── V1.0.0_cronjob.sql   # 定时任务 (作业/DAG/调度/告警/日志/配额)
    ├── V1.0.0_message.sql   # 消息中心 (通知/模板/回执/批量/灰度/偏好)
    ├── V1.0.0_workflow.sql  # 工作流引擎 (定义/实例/委派/通知/DMN/集成/AI辅助)
    ├── V1.0.0_agent.sql     # AI Agent (Agent/编排/知识库/工具/人机协同)
    └── V1.0.0_literule.sql  # 规则引擎 (规则/决策表/评分卡/AB测试/变量)
```

### 3.1 汇总文件 vs 模块文件

| 场景 | 使用文件 | 说明 |
|---|---|---|
| **新环境完整初始化** | `V1.0.0.sql` | 一把梭,所有 DDL + DML 都在里面 |
| **新环境模块化初始化** | `modules/V1.0.0_all.sql` | 通过 `\i` 依次引用各子模块,等价于 V1.0.0.sql |
| **单独初始化某模块** | `modules/V1.0.0_{module}.sql` | 仅初始化对应模块的表(需先跑 common 模块) |
| **Schema 变更** | 直接编辑 `V1.0.0.sql` | **唯一事实源**,模块文件由拆分生成 |

### 3.2 模块与表的数量分布

| 模块 | 表数量 | 主要表前缀 |
|---|---|---|
| common | 4 | `pmis_dict_*`, `undo_log` |
| system | 8 | `pmis_config`, `pmis_operation_log`, `pmis_file` |
| userinfo | 19 | `pmis_role`, `pmis_permission`, `pmis_employee`, `pmis_user_*` |
| project | 37 | `pmis_project_*`, `pmis_execution_*`, `pmis_cost_*`, `pmis_finance_*` |
| cronjob | 22 | `pmis_job_*`, `pmis_tenant_quota` |
| message | 24 | `pmis_msg_*` (含 7 张月度分区表) |
| workflow | 34 | `pmis_flow_*` |
| agent | 12 | `pmis_agent_*` |
| literule | 17 | `pmis_rule_*` |
| **合计** | **177** | |

### 3.3 模块拆分规则

- 拆分依据:数据库表名前缀与后端子模块的对应关系
- `V1.0.0.sql` 为**唯一事实源**,`modules/` 下的文件由其拆分生成
- 任何 schema 变更**直接编辑 `V1.0.0.sql`**,然后重新运行拆分脚本同步模块文件
- 模块文件中不包含 `BEGIN`/`COMMIT`(事务包装在汇总文件或 `V1.0.0_all.sql` 中)

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

### 9.2 任务清单(2026-07-06 批次,本 README 更新版)

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
