# PMIS PostgreSQL 备份运维手册

## 备份策略

| 类型 | 周期 | 保留期 | 工具 | 触发方式 | 脚本 |
|------|------|--------|------|----------|------|
| 全量 | 每日 02:00 | 7 天 | `pg_dump --format=custom --compress=9` | cron | [pg_backup.sh](./pg_backup.sh) |
| 增量 | 实时 | 7 天 | WAL 归档（`archive_command`） | PostgreSQL 自动 | [pg_incremental.sh](./pg_incremental.sh) |
| 演练 | 每月 1 日 04:00 | — | `pg_restore` 恢复到临时实例 | cron | [restore-test.sh](./restore-test.sh) |

## 部署步骤

### 1. 创建备份专用账号

```sql
CREATE USER pmis_backup WITH REPLICATION PASSWORD 'xxx';
GRANT CONNECT ON DATABASE pmis TO pmis_backup;
GRANT USAGE ON SCHEMA public TO pmis_backup;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO pmis_backup;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO pmis_backup;
```

### 2. 配置 PostgreSQL WAL 归档

修改 `postgresql.conf`：

```ini
wal_level = replica
archive_mode = on
archive_command = '/data/backup/pg_incremental.sh %f %p'
max_wal_senders = 3
wal_keep_size = '1GB'
```

### 3. 配置环境变量

```bash
sudo mkdir -p /etc/pmis
sudo tee /etc/pmis/backup.env > /dev/null <<EOF
PMIS_PGPASSWORD=xxx
PMIS_BACKUP_OSS_BUCKET=pmis-prod-backup
PMIS_ALERT_MAIL=ops@ydsz-pmis.cn
EOF
sudo chmod 600 /etc/pmis/backup.env
```

### 4. 安装 crontab

```bash
sudo cp deploy/backup/cron.d/pmis-backup /etc/cron.d/pmis-backup
sudo chmod 644 /etc/cron.d/pmis-backup
sudo systemctl restart cron
```

### 5. 首次执行验证

```bash
sudo -u postgres bash -c "source /etc/pmis/backup.env && /data/backup/pg_backup.sh"
# 预期输出末尾：[PMIS Daily Backup] SUCCESS
```

## 监控与告警

### 关键指标

| 指标 | 阈值 | 监控方式 |
|------|------|----------|
| 备份成功率 | ≥99%（季度） | `.last_backup.json` 状态字段 |
| 备份耗时 | ≤30min | `.last_backup.json` duration_seconds |
| 备份文件大小 | 与昨日差 ≤50% | 环比监控 |
| 磁盘空间 | 余量 ≥50% | 每日巡检 |
| 恢复演练 RTO | ≤30min | 月度报告 |

### 告警触发

| 场景 | 邮件标题 | 接收人 |
|------|----------|--------|
| 备份失败 | `[ALERT] PMIS Daily Backup FAILED` | ops@ydsz-pmis.cn |
| 文件损坏 | `[ALERT] PMIS Daily Backup CORRUPTED` | ops@ydsz-pmis.cn |
| WAL 归档失败 | `[ALERT] PMIS WAL Archive FAILED` | ops@ydsz-pmis.cn |
| RTO 超时 | `[WARN] PMIS Restore Drill SLOW` | ops@ydsz-pmis.cn |

## 灾备恢复流程

### 场景 1：单库误操作（DROP TABLE 等）

```bash
# 1. 停止应用访问
sudo systemctl stop pmis-{auth,user,project,execution}

# 2. 找到误操作前的全量备份
ls -lt /data/backup/pmis/daily/ | head -5

# 3. 创建时间点恢复目标
# PITR 起点 = 全量备份时间
# PITR 终点 = 误操作前 1 分钟

# 4. 基础备份恢复
sudo -u postgres pg_restore -d pmis_restore /data/backup/pmis/daily/pmis_daily_xxx.sql.gz

# 5. 提取误操作表的 DDL + 数据
pg_dump -t <table_name> pmis_restore > table_recovery.sql

# 6. 导入到生产库
psql -d pmis -f table_recovery.sql

# 7. 重启应用
sudo systemctl start pmis-{auth,user,project,execution}
```

### 场景 2：整库损坏

```bash
# 1. 重建 PostgreSQL 实例
sudo -u postgres initdb -D /data/pg_new

# 2. 启用 WAL 归档恢复模式
cat >> /data/pg_new/postgresql.conf <<EOF
restore_command = 'cp /data/backup/pmis/wal/%f.gz %p && gunzip %p'
recovery_target_time = '2026-07-01 09:00:00'
EOF

# 3. 创建恢复信号文件
sudo -u postgres touch /data/pg_new/recovery.signal

# 4. 启动实例并验证
sudo -u postgres pg_ctl -D /data/pg_new start
```

## 容量规划

| 数据量 | 全量备份耗时 | 磁盘占用（压缩） | 推荐存储 |
|--------|--------------|------------------|----------|
| 10 GB | ~3 min | ~2 GB | 本地 7 天 + OSS 30 天 |
| 100 GB | ~25 min | ~25 GB | 本地 3 天 + OSS 30 天 + 异地 90 天 |
| 500 GB | ~2 h | ~120 GB | 本地 1 天 + OSS 7 天 + 异地 365 天 |
| 1 TB+ | ~4 h | ~250 GB | 不建议全量；改用 Barman / pgbackrest 增量永久保留 |

## 常见问题

### Q1: pg_dump 卡住 / 超时

检查长事务：`SELECT pid, query_start, state, query FROM pg_stat_activity WHERE state='active' AND now()-query_start > interval '5 min';`

### Q2: 备份文件很大

确认 `--format=custom --compress=9` 已使用；考虑分库备份（按 schema）。

### Q3: 恢复后查询慢

执行 `ANALYZE` 收集统计信息：`psql -c 'ANALYZE;' pmis`

### Q4: WAL 归档积压

检查 `pg_stat_archiver`：`SELECT * FROM pg_stat_archiver;`，重点看 `failed_count` 和 `last_archived_time`。
