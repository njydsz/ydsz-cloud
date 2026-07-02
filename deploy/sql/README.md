# PostgreSQL 部署与调优目录（批次 19 补全）

PMIS 生产环境 PostgreSQL 18 的调优参数与索引脚本。

## 目录结构

```
deploy/sql/
├── postgresql.conf        # 主从节点生产配置（64G 内存 / 16 核）
├── pg_hba.conf            # 客户端认证（应用/复制/监控三类分网段）
├── index-tuning.sql       # 200+ 表的索引补全（批次 1-19 全模块）
└── README.md              # 本文件
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
