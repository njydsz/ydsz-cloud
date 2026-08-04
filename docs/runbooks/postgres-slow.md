# 数据库性能下降处理

## 症状
- `ServiceHighLatency` 告警（接口 P99 > 3s）
- `DataSourcePoolExhausted` 告警（连接池接近耗尽）
- Druid 慢 SQL 日志增多

## 影响
- 接口响应超时
- 用户操作卡顿
- 严重时连接耗尽导致服务不可用
- P1 级别故障

## 排查步骤

### Step 1: 确认数据库状态

```bash
# 连接检查
psql -h <PG_HOST> -U ydsz -d ydsz_pmis -c "SELECT count(*) as active_connections FROM pg_stat_activity WHERE state='active';"

# 锁等待检查
psql -h <PG_HOST> -U ydsz -d ydsz_pmis -c "
SELECT blocked_locks.pid AS blocked_pid,
       blocked_activity.query AS blocked_query,
       blocking_locks.pid AS blocking_pid,
       blocking_activity.query AS blocking_query
FROM pg_catalog.pg_locks blocked_locks
JOIN pg_catalog.pg_stat_activity blocked_activity ON blocked_activity.pid = blocked_locks.pid
JOIN pg_catalog.pg_locks blocking_locks ON blocking_locks.locktype = blocked_locks.locktype
JOIN pg_catalog.pg_stat_activity blocking_activity ON blocking_activity.pid = blocking_locks.pid
WHERE NOT blocked_locks.granted;
"
```

### Step 2: 查找慢 SQL

```bash
# 查看当前运行超过 5 秒的查询
psql -h <PG_HOST> -U ydsz -d ydsz_pmis -c "
SELECT pid, now()-query_start AS duration, query, state
FROM pg_stat_activity
WHERE state='active' AND now()-query_start > interval '5 seconds'
ORDER BY duration DESC;
"

# 查看 pg_stat_statements 中耗时 TOP 10 的查询
psql -h <PG_HOST> -U ydsz -d ydsz_pmis -c "
SELECT mean_exec_time, calls, query 
FROM pg_stat_statements 
ORDER BY mean_exec_time DESC 
LIMIT 10;
"
```

### Step 3: 止血操作

#### 场景 A: 慢 SQL 导致连接堆积

1. **Kill 长时间运行的查询**
   ```bash
   psql -h <PG_HOST> -U ydsz -d ydsz_pmis -c "
   SELECT pg_terminate_backend(pid) 
   FROM pg_stat_activity 
   WHERE state='active' AND now()-query_start > interval '30 seconds';
   "
   ```

2. **分析慢 SQL 执行计划**
   ```sql
   EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT) <慢SQL>;
   ```

3. **补充索引或优化 SQL**

#### 场景 B: 连接池接近耗尽

1. **检查 Druid 连接池状态**
   ```bash
   # 通过 Actuator 端点查看
   curl -s http://localhost:<port>/actuator/metrics/druid.active.connections | jq
   curl -s http://localhost:<port>/actuator/metrics/druid.wait.millis.total | jq
   ```

2. **检查是否有连接泄漏**
   ```bash
   # 查看 Druid abandoned 连接数
   curl -s http://localhost:<port>/actuator/metrics/druid.abandoned.total | jq
   ```

3. **临时扩容连接池**（通过 Nacos 动态刷新）
   - 修改 `ydsz-common-datasource.yaml` 中的 `max-active` 从 50 调到 80
   - 观察是否缓解（注意：PG 总连接数 = 服务数 × max-active）

#### 场景 C: 数据库 CPU 高

1. **检查是否有全表扫描**
   ```sql
   SELECT schemaname, tablename, seq_scan, seq_tup_read, idx_scan
   FROM pg_stat_user_tables
   WHERE seq_scan > 0
   ORDER BY seq_tup_read DESC
   LIMIT 20;
   ```

2. **检查表膨胀**
   ```sql
   SELECT schemaname, tablename, n_dead_tup, last_vacuum, last_autovacuum
   FROM pg_stat_user_tables
   ORDER BY n_dead_tup DESC
   LIMIT 10;
   ```

3. **手动 VACUUM**
   ```sql
   VACUUM ANALYZE <table>;
   ```

### Step 4: 恢复后验证

```bash
# 确认连接数回落到正常水位
psql -h <PG_HOST> -U ydsz -d ydsz_pmis -c "SELECT count(*) FROM pg_stat_activity WHERE state='active';"

# 确认接口延迟恢复正常
curl -s http://localhost:<port>/actuator/metrics/http.server.requests | jq '.measurements[] | select(.statistic=="VALUE")'

# 确认无慢 SQL
# 检查 Grafana 慢 SQL 面板无新增慢查询
```

## 预防措施
1. Druid 慢 SQL 监控开启（`slow-sql-millis: 1000`）
2. PostgreSQL `pg_stat_statements` 扩展启用
3. 大表按月分区（`ydsz_flow_audit_log`、`ydsz_job_log`）
4. 定期执行 `VACUUM ANALYZE`
5. 索引使用监控 + 缺失索引告警
