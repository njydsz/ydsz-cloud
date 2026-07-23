# YDSZ 灾备方案与恢复 Runbook

> **版本**: v1.0 · 2026-07-11
> **适用环境**: 生产环境（prod）
> **文档性质**: P0 级运维文档，变更需 DBA + 架构师审批

---

## 1. 备份策略概览

| 备份类型 | 频率 | 保留期 | 存储位置 | RPO |
|---------|------|--------|---------|-----|
| 全量逻辑备份 (pg_dump) | 每日 02:00 | 7 天 | 本地 + OSS | ≤ 24h |
| WAL 归档 | 每 15 分钟 | 30 天 | 本地 + OSS | ≤ 15min |
| 全量物理备份 (pg_basebackup) | 每周日 03:00 | 4 周 | 本地 + OSS | ≤ 7d |
| Redis RDB | 每日 01:00 | 7 天 | 本地 + OSS | ≤ 24h |
| Redis AOF | 实时 (everysec) | 7 天 | 本地 | ≤ 1s |
| Nacos 配置快照 | 每日 00:30 | 30 天 | 本地 + OSS | ≤ 24h |

### RTO / RPO 目标

| 场景 | RTO (恢复时间) | RPO (数据丢失) | 策略 |
|------|---------------|---------------|------|
| 单表误删 | ≤ 15 min | 0 | pg_dump 逻辑恢复单表 |
| 库级故障 | ≤ 2 h | ≤ 15 min | WAL 归档 + PITR |
| 整机房故障 | ≤ 8 h | ≤ 24 h | OSS 跨域备份 + 新机房重建 |
| 数据损坏 | ≤ 4 h | ≤ 15 min | pg_basebackup + WAL PITR |

---

## 2. 备份架构

```
                    ┌─────────────────┐
                    │  YDSZ PG Primary │
                    └────────┬────────┘
                             │
                    ┌────────▼────────┐
                    │  pg_dump (daily) │
                    │  WAL Archive     │
                    │  pg_basebackup   │
                    └────────┬────────┘
                             │
                    ┌────────▼────────┐
                    │  Local Storage   │
                    │  /data/backups/  │
                    └────────┬────────┘
                             │
                    ┌────────▼────────┐
                    │  OSS (异地容灾)  │
                    │  oss://ydsz-bk/  │
                    └─────────────────┘
```

---

## 3. 自动备份配置

### 3.1 PostgreSQL WAL 归档配置

在 `postgresql.conf` 中启用：

```conf
# WAL 归档
wal_level = replica
archive_mode = on
archive_command = 'test ! -f /data/backups/wal-archive/%f && cp %p /data/backups/wal-archive/%f'
archive_timeout = 900  # 15 分钟强制切换 WAL

# 检查点
checkpoint_timeout = 15min
max_wal_size = 2GB
min_wal_size = 256MB
```

### 3.2 Crontab 调度

```bash
# YDSZ 数据库备份 crontab
# 每日凌晨 2:00 全量逻辑备份
0 2 * * * /opt/ydsz/scripts/pg-backup.sh >> /var/log/ydsz/backup.log 2>&1

# 每 15 分钟 WAL 归档检查
*/15 * * * * /opt/ydsz/scripts/pg-backup.sh --wal-archive >> /var/log/ydsz/wal-archive.log 2>&1

# 每周日凌晨 3:00 物理全量备份
0 3 * * 0 /opt/ydsz/scripts/pg-basebackup.sh >> /var/log/ydsz/basebackup.log 2>&1

# 每日凌晨 2:30 上传备份到 OSS
30 2 * * * /opt/ydsz/scripts/upload-backup-to-oss.sh >> /var/log/ydsz/oss-upload.log 2>&1

# Redis RDB 每日 1:00
0 1 * * * redis-cli -a $REDIS_PASS BGSAVE && sleep 10 && cp /var/lib/redis/dump.rdb /data/backups/redis/dump_$(date +\%Y\%m\%d).rdb

# Nacos 配置导出每日 0:30
30 0 * * * curl -s "http://nacos:8848/nacos/v1/cs/configs?export=true" -o /data/backups/nacos/configs_$(date +\%Y\%m\%d).zip
```

---

## 4. 恢复 Runbook

### 4.1 场景 A：单表误删/误改恢复

**触发条件**: 业务方报告某张表数据被误删或误改

**步骤**:

```bash
# 1. 确认误操作时间和表名
PSQL_CMD="psql -h 127.0.0.1 -U postgres -d ydsz"
$PSQL_CMD -c "SELECT now();"  # 记录当前时间

# 2. 找到最近的备份文件
ls -lt /data/backups/postgres/ydsz_full_*.dump | head -3

# 3. 预检查备份文件
/opt/ydsz/scripts/pg-backup.sh --verify /data/backups/postgres/ydsz_full_20260710_020000.dump

# 4. 创建临时恢复数据库
createdb -h 127.0.0.1 -U postgres ydsz-restore

# 5. 恢复备份到临时库
pg_restore -h 127.0.0.1 -U postgres -d ydsz-restore -j 4 \
  /data/backups/postgres/ydsz_full_20260710_020000.dump

# 6. 从临时库导出误操作的表
pg_dump -h 127.0.0.1 -U postgres -d ydsz-restore \
  --table=ydsz_project \
  --data-only \
  --format=plain \
  -f /tmp/ydsz_project_restore.sql

# 7. 在生产库中恢复该表（先备份当前数据）
$PSQL_CMD -c "CREATE TABLE ydsz_project_bak_$(date +%s) AS SELECT * FROM ydsz_project;"
$PSQL_CMD -c "TRUNCATE TABLE ydsz_project;"
$PSQL_CMD -f /tmp/ydsz_project_restore.sql

# 8. 验证数据
$PSQL_CMD -c "SELECT count(*) FROM ydsz_project;"

# 9. 清理临时资源
dropdb -h 127.0.0.1 -U postgres ydsz-restore
rm /tmp/ydsz_project_restore.sql
```

### 4.2 场景 B：库级故障恢复（PITR）

**触发条件**: 数据库损坏、存储故障、大范围数据损坏

**步骤**:

```bash
# 1. 停止所有 YDSZ 应用服务
kubectl scale deploy -n ydsz-prod --replicas=0 deployment/ydsz-gateway deployment/ydsz-userinfo deployment/ydsz-system

# 2. 停止 PostgreSQL
systemctl stop postgresql

# 3. 备份当前（损坏的）数据目录
mv /var/lib/postgresql/data /var/lib/postgresql/data.corrupt.$(date +%s)

# 4. 找到最近的全量物理备份
LATEST_BASEBACKUP=$(ls -d /data/backups/postgres/basebackup_* | tail -1)
echo "使用物理备份: $LATEST_BASEBACKUP"

# 5. 恢复物理备份
mkdir -p /var/lib/postgresql/data
cp -r $LATEST_BASEBACKUP/* /var/lib/postgresql/data/
chown -R postgres:postgres /var/lib/postgresql/data

# 6. 创建 recovery 配置
cat > /var/lib/postgresql/data/recovery.signal <<EOF
EOF

cat >> /var/lib/postgresql/data/postgresql.auto.conf <<EOF
restore_command = 'cp /data/backups/wal-archive/%f %p'
recovery_target_time = '$(date -d "1 hour ago" "+%Y-%m-%d %H:%M:%S")'
recovery_target_action = 'promote'
EOF

# 7. 启动 PostgreSQL（自动进入恢复模式）
systemctl start postgresql

# 8. 监控恢复进度
tail -f /var/log/postgresql/postgresql-*.log | grep -E "recovery|restore|consistent"

# 9. 等待恢复完成（日志出现 "database system is ready to accept connections"）
# 10. 验证数据
psql -U postgres -d ydsz -c "SELECT count(*) FROM ydsz_user;"
psql -U postgres -d ydsz -c "SELECT count(*) FROM ydsz_project;"

# 11. 恢复应用服务
kubectl scale deploy -n ydsz-prod --replicas=3 deployment/ydsz-gateway deployment/ydsz-userinfo deployment/ydsz-system
```

### 4.3 场景 C：整机房故障 — 异地重建

**触发条件**: 主机房整体不可用（网络中断/断电/硬件损毁）

**步骤**:

```bash
# 1. 在灾备机房准备新 PostgreSQL 实例
# (假设新 PG 已安装并初始化)

# 2. 从 OSS 下载最近的备份
ossutil cp oss://ydsz-backup/postgres/ydsz_full_latest.dump /tmp/
ossutil cp -r oss://ydsz-backup/wal-archive/ /data/backups/wal-archive/

# 3. 恢复全量逻辑备份
createdb -U postgres ydsz
pg_restore -U postgres -d ydsz -j 4 /tmp/ydsz_full_latest.dump

# 4. 应用 WAL 归档（PITR 到最新状态）
# (参考场景 B 的步骤 6-8)

# 5. 更新 DNS / 负载均衡指向新 PG 实例
# 6. 恢复 Redis 备份
redis-cli -h new-redis shutdown
cp /data/backups/redis/dump_latest.rdb /var/lib/redis/dump.rdb
chown redis:redis /var/lib/redis/dump.rdb
systemctl start redis

# 7. 恢复 Nacos 配置
unzip /data/backups/nacos/configs_latest.zip -d /tmp/nacos-restore
# 通过 Nacos API 导入配置

# 8. 更新所有微服务配置指向新基础设施
kubectl apply -f deploy/k8s/overlays/dr-recovery/

# 9. 启动应用服务
kubectl apply -f deploy/k8s/overlays/prod/
```

---

## 5. 备份验证计划

### 5.1 定期恢复演练

| 频率 | 演练内容 | 参与人员 | 通过标准 |
|------|---------|---------|---------|
| 每月 | 全量备份恢复到测试环境 | DBA + 运维 | 数据一致 + RTO ≤ 2h |
| 每季度 | PITR 时间点恢复 | DBA + 架构师 | 数据一致 + RTO ≤ 4h |
| 每半年 | 异地灾备全流程演练 | 全体 SRE | RTO ≤ 8h |

### 5.2 自动化校验

```bash
# 每日备份后自动验证（crontab）
30 2 * * * /opt/ydsz/scripts/pg-backup.sh --verify $(ls -t /data/backups/postgres/*.dump | head -1) >> /var/log/ydsz/backup-verify.log 2>&1
```

---

## 6. 监控与告警

### 6.1 备份监控指标

| 指标 | 阈值 | 告警级别 |
|------|------|---------|
| 备份任务执行状态 | 失败 | P0 - 立即告警 |
| 备份文件大小 | 较昨日减少 > 20% | P1 - 30 分钟内确认 |
| WAL 归档延迟 | > 15 分钟 | P0 - 立即告警 |
| 备份磁盘使用率 | > 80% | P1 - 1 小时内处理 |
| OSS 上传状态 | 失败 | P1 - 1 小时内重试 |

### 6.2 Prometheus 告警规则

```yaml
# prometheus/rules/pg-backup-alerts.yml
groups:
  - name: pg-backup
    rules:
      - alert: PgBackupFailed
        expr: ydsz_pg_backup_status{job="pg-backup-exporter"} == 0
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "PostgreSQL 备份失败 ({{ $labels.instance }})"
          description: "PG 备份任务连续失败超过 5 分钟，请检查 /var/log/ydsz/backup.log"

      - alert: PgWalArchiveLag
        expr: time() - ydsz_pg_wal_archive_last_success_timestamp > 900
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "WAL 归档延迟超过 15 分钟 ({{ $labels.instance }})"
          description: "WAL 归档异常可能导致 RPO 超标，请立即检查"

      - alert: PgBackupSizeAnomaly
        expr: ydsz_pg_backup_size_bytes / ydsz_pg_backup_size_bytes offset 1d < 0.8
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "备份文件大小异常缩减 ({{ $labels.instance }})"
          description: "今日备份较昨日缩小超过 20%，可能存在数据丢失"
```

---

## 7. 联系人矩阵

| 角色 | 姓名 | 电话 | 职责 |
|------|------|------|------|
| DBA | ___ | ___ | 数据库恢复执行 |
| SRE Lead | ___ | ___ | 灾备协调与决策 |
| 架构师 | ___ | ___ | 恢复方案审批 |
| 业务方 | ___ | ___ | 数据一致性确认 |

---

## 8. 变更记录

| 日期 | 版本 | 变更内容 | 作者 |
|------|------|---------|------|
| 2026-07-11 | v1.0 | 初始版本 | YDSZ Team |
