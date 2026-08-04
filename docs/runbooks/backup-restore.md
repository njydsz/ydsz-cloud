# 备份恢复操作手册

## 备份策略

### 数据库备份（PostgreSQL）

| 备份类型 | 频率 | 保留期 | 存储位置 |
|---------|------|--------|---------|
| 全量备份 | 每日 02:00 | 30 天 | MinIO / S3 |
| WAL 归档 | 实时 | 7 天 | MinIO / S3 |
| 手动备份 | 重大变更前 | 永久 | MinIO + 本地 |

#### 全量备份脚本

```bash
#!/bin/bash
# backup_postgres.sh

set -e

BACKUP_DIR="/backup/postgres/$(date +%Y%m%d_%H%M%S)"
mkdir -p "$BACKUP_DIR"

PG_HOST="${PG_HOST:-pg-master.prod.svc}"
PG_USER="${PG_USER:-ydsz}"
PG_DB="${PG_DB:-ydsz_pmis}"

echo "[$(date)] Starting PostgreSQL backup..."

pg_dump -h "$PG_HOST" -U "$PG_USER" -d "$PG_DB" \
  --format=directory \
  --jobs=4 \
  --compress=6 \
  --no-owner \
  --no-privileges \
  --file="$BACKUP_DIR"

echo "[$(date)] Backup completed: $BACKUP_DIR"

# 上传到 MinIO
mc cp --recursive "$BACKUP_DIR" minio/backups/postgres/

# 清理本地旧备份（保留 7 天）
find /backup/postgres/ -mindepth 1 -maxdepth 1 -mtime +7 -exec rm -rf {} \;

echo "[$(date)] Cleanup completed"
```

### Redis 备份

| 备份类型 | 频率 | 保留期 |
|---------|------|--------|
| RDB 快照 | 每 6 小时 | 7 天 |
| AOF 持久化 | 实时 | - |

### 文件备份（MinIO）

MinIO 支持跨地域复制（Site Replication），无需额外备份。

## 恢复操作

### 数据库恢复

```bash
# 1. 停止相关服务
kubectl scale deployment --all --replicas=0 -n ydsz-prod

# 2. 下载最新备份
mc cp --recursive minio/backups/postgres/<BACKUP_DIR>/ /tmp/restore/

# 3. 执行恢复
pg_restore -h "$PG_HOST" -U "$PG_DB" -d "$PG_DB" \
  --jobs=4 \
  --no-owner \
  --no-privileges \
  --clean \
  --if-exists \
  /tmp/restore/

# 4. 验证数据完整性
psql -h "$PG_HOST" -U "$PG_USER" -d "$PG_DB" -c "
SELECT 'projects' as tbl, count(*) FROM ydsz_project
UNION ALL
SELECT 'users', count(*) FROM ydsz_user
UNION ALL
SELECT 'contracts', count(*) FROM ydsz_contract;
"

# 5. 恢复服务
kubectl scale deployment --all --replicas=<原副本数> -n ydsz-prod
```

### 恢复演练

**每月进行一次恢复演练**：
1. 从最近全量备份恢复到一个临时数据库
2. 验证关键表数据完整性
3. 执行核心业务 SQL 验证
4. 记录恢复耗时（RTO 指标）

## RPO/RTO 目标

| 指标 | 目标 | 说明 |
|------|------|------|
| RPO（恢复点目标） | < 1 小时 | 基于 WAL 归档 |
| RTO（恢复时间目标） | < 30 分钟 | 基于 pg_restore |

## 备份监控

| 监控项 | 告警条件 |
|--------|---------|
| 备份任务执行 | 失败告警 |
| 备份文件大小 | 小于预期值 50% 告警 |
| 备份文件年龄 | > 25 小时告警 |
| 恢复演练 | 超过 35 天未演练告警 |
