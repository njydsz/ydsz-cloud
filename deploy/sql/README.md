# PMIS 数据库 Schema 与部署目录
# --------------------------------------------------------------------------
# 用途：PMIS 生产环境 PostgreSQL 18 的所有 DDL、索引调优、配置与脚本。
# 命名规范：Flyway 版本号 V<主>.<次>.<补丁>_<序号>__<说明>.sql
# 执行方式：Flyway 自动按版本号顺序执行；本地用 psql -f 手动执行。
# 备份：deploy/backup/pg_backup.sh 提供每日全量 + 增量备份。
# --------------------------------------------------------------------------

## 目录结构

```
deploy/sql/
├── postgresql.conf                          # 主从节点生产配置（64G 内存 / 16 核）
├── pg_hba.conf                              # 客户端认证（应用/复制/监控三类分网段）
├── index-tuning.sql                         # 200+ 表的索引补全（批次 1-19 全模块）
│
├── V1.0.0_001__init_pmis_schema.sql         # 基础字典/权限/租户 schema
├── V1.0.0_004__init_pmis_workflow_schema.sql
├── V1.0.0_005__init_pmis_file_schema.sql
├── V1.0.0_006__init_pmis_job_schema.sql
├── V1.0.0_007__init_pmis_message_schema.sql
├── V1.0.0_008__init_pmis_audit_schema.sql
├── V1.0.0_009__init_pmis_project_schema.sql
├── V1.0.0_010__init_pmis_execution_schema.sql
├── V1.0.0_011__init_pmis_batch8_schema.sql  # 批次 8：合同/变更/交付/结项/AI 智能体
├── V1.0.0_012__init_pmis_finance_schema.sql
├── V1.0.0_013__init_pmis_evm_schema.sql
├── V1.0.0_014__init_pmis_admin_full_perm.sql
├── V1.0.0_014_1__init_pmis_resource_bench_schema.sql
├── V1.0.0_015__init_pmis_cockpit_views.sql
├── V1.0.0_016__init_pmis_security.sql
├── V1.0.0_017__init_pmis_after_sales_schema.sql
├── V1.0.0_018__init_pmis_smart_p4_2_schema.sql
├── V1.0.0_019__init_pmis_alert_thresholds.sql
├── V1.0.0_020__init_pmis_billable_utilization_snapshot.sql
├── V1.0.0_021__register_pmis_smart_jobs.sql
├── V1.0.0_022__init_pmis_alert_templates.sql
├── V1.0.0_023__init_pmis_flow_engine.sql
├── V1.0.0_024__add_version_to_core_tables.sql
├── V1.0.0_025__add_pmis_flow_audit_log.sql
├── V1.0.0_026__add_pmis_flow_cc.sql
├── V1.0.0_027__init_undo_log.sql            # Seata AT 模式 undo_log（每个业务库）
├── V1.0.0_028__add_flow_gap_columns.sql
├── V1.0.0_029__add_pmis_flow_timer.sql
├── V1.0.0_030__add_pmis_flow_delegate_auth.sql
├── V1.0.0_031__init_report_subscription.sql
├── V1.0.0_032__register_report_jobs.sql
├── V1.0.0_033__add_pmis_flow_weight.sql
├── V1.0.0_034__add_pmis_flow_sla_reminder.sql
├── V1.0.0_035__register_consistency_job.sql
├── V1.0.0_036__init_export_record.sql
├── V1.0.0_037__init_pmis_flow_archive.sql
├── V1.0.0_038__add_pmis_flow_canary.sql
├── V1.0.0_039__init_pmis_attendance_schema.sql
├── V1.0.0_040__add_audit_diff_fields.sql
├── V1.0.0_041__init_pmis_literule_schema.sql
├── V1.0.0_042__init_pmis_rule_test_case.sql
├── V1.0.0_043__add_rule_lifecycle_and_trace.sql
├── V1.0.0_044__add_decision_table.sql
├── V1.0.0_045__add_decision_table_hit_policy.sql
├── V1.0.0_046__add_pmis_flow_event_subscription.sql
├── V1.0.0_047__add_rule_canary.sql
├── V1.0.0_048__add_pmis_flow_task_priority.sql
├── V1.0.0_048__init_rule_scorecard_tree_script.sql
├── V1.0.0_049__add_rule_status_check.sql
└── README.md                                # 本文件
```

## 调优要点

### 1. 内存（64G 物理机）

| 参数 | 值 | 说明 |
|------|----|------|
| `shared_buffers` | 16GB | 物理内存 25% |
| `effective_cache_size` | 48GB | OS 缓存估算 |
| `work_mem` | 32MB | 单次排序/哈希 |
| `maintenance_work_mem` | 2GB | VACUUM/INDEX |

### 2. 写入

| 参数 | 值 | 说明 |
|------|----|------|
| `wal_buffers` | 64MB | WAL 缓冲 |
| `wal_compression` | on | WAL 压缩 |
| `max_wal_size` | 16GB | WAL 总量上限 |
| `statement_timeout` | 60s | 防慢查询 |

### 3. 复制（主从）

| 参数 | 值 | 说明 |
|------|----|------|
| `wal_level` | replica | 物理复制 |
| `max_wal_senders` | 10 | 并发备库数 |
| `hot_standby_feedback` | on | 备库反馈避免冲突 |

### 4. 慢 SQL

- `log_min_duration_statement = 500` 记录 > 500ms
- `log_destination = csvlog` 输出 csv 格式
- 配合 `pgBadger` 每日分析

## 索引调优覆盖

| 模块 | 关键索引 |
|------|----------|
| 项目立项/变更/结项 | 复合索引 + 部分索引（major_flag=1） |
| EVM | `(initiation_id, wbs_task_id, period) UNIQUE` 幂等 |
| 利用率 | `(user_id, period DESC)` 排行榜 |
| 预警/对账 | `next_retry_at` 部分索引（重试队列） |
| AI Agent | `(biz_type, biz_id, created_at)` 业务追溯 |
| 财务 | `status, issued_at DESC` 发票流 |
| 审计/日志 | `BRIN(created_at)` 100w+ 行压缩 |

## 表命名规范

- 前缀：`pmis_<业务域>_<实体>`，如 `pmis_project_initiation`、`pmis_finance_invoice`
- 业务域缩写：dict / iam / project / execution / finance / evm / agent / audit / file / job / message / workflow / alert / rule / attendance
- 主键：统一 `BIGSERIAL`，命名 `id`
- 通用字段：`tenant_id`、`created_by/at`、`updated_by/at`、`deleted`（逻辑删除，0=未删 1=已删）
- 时间字段：默认 `CURRENT_TIMESTAMP`，类型 `TIMESTAMP`（不带时区，应用层统一 UTC+8）
- 金额字段：`NUMERIC(18,4)`，避免浮点精度问题
- 状态字段：`VARCHAR(32)` 枚举字符串 + `CHECK` 约束

## 部署步骤

```bash
# 1. 安装 PostgreSQL 18
apt install postgresql-18

# 2. 替换配置文件
cp deploy/sql/postgresql.conf /etc/postgresql/18/main/postgresql.conf
cp deploy/sql/pg_hba.conf /etc/postgresql/18/main/pg_hba.conf
chown postgres:postgres /etc/postgresql/18/main/*.conf
chmod 640 /etc/postgresql/18/main/*.conf

# 3. 重启并初始化索引
systemctl restart postgresql
psql -U pmis_app -d pmis -f deploy/sql/index-tuning.sql

# 4. 安装 pg_stat_statements 扩展
psql -U pmis_app -d pmis -c "CREATE EXTENSION IF NOT EXISTS pg_stat_statements;"
psql -U pmis_app -d pmis -c "CREATE EXTENSION IF NOT EXISTS pg_hint_plan;"

# 5. 启用 pgBadger 慢 SQL 分析
apt install pgbadger
# crontab: 0 1 * * * pgbadger /var/log/postgresql/*.csv -o /var/www/pgbadger/index.html
```

## 监控 SQL（Prometheus postgres_exporter）

参见 `deploy/monitoring/postgres-exporter-queries.yml`（批次 19 后续补充）。

## 灾备

参见 `deploy/backup/pg_backup.sh`（每日全量）+ `pg_incremental.sh`（每小时增量）。

## 多租户与逻辑删除

- 每一张业务表都带 `tenant_id`，所有 SQL 强制 `WHERE tenant_id = ?`
- 物理删除一律禁止，统一通过 `deleted = 1` 标记，配合查询自动过滤
- 关键业务表（如合同/项目）删除前需走"归档"流程（`pmis_flow_archive`）

## COMMENT ON 规范

PMIS 强制所有表与列必须添加 COMMENT ON，便于 DBA 与开发协作：

- `COMMENT ON TABLE` 写业务含义（一行话能说清）
- `COMMENT ON COLUMN` 写字段语义 + 枚举值（`status: ENABLED 启用 / DISABLED 停用`）
- 字典字段额外说明字典类型 code，如 `contract_type: FIXED_PRICE/T_M/...`

## Flyway 兼容性

- 文件名遵循 `V<版本>__<说明>.sql` 格式，版本号严格单调递增
- 不可修改已执行过的 migration，需要变更时新建 V<N+1> 文件
- `R__` 开头为可重复执行的视图/函数/存储过程
- `U<n>__` 开头为应急回滚脚本，仅 DBA 手动执行
