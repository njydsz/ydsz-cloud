# 故障处理 SOP 汇总

> 每个故障场景按「症状 → 诊断 → 处置 → 预防」四步组织。
> 值班人员按本手册操作，恢复后补充事故报告。

---

## 1. 网关 503 / 502（下游不可达）

**症状**：Gateway 日志大量 `ConnectException` / 504，Grafana 5xx 突增。

**诊断**：
```bash
# 1. 确认哪个下游异常
kubectl get pods -n ydsz-prod | grep -v Running
# 2. 查看网关日志中的 targetUri
kubectl logs -n ydsz-prod deploy/ydsz-gateway --tail=200 | grep "502\|503"
# 3. 检查下游健康
curl -s http://ydsz-project:9009/actuator/health
```

**处置**：
1. 下游 Pod 异常 → 查看其日志定位原因（OOM/慢SQL/代码 bug）
2. 单个 Pod 异常 → `kubectl delete pod` 触发重启
3. 全部异常 → 回滚到上一版本镜像

**预防**：灰度发布 + 主动健康检查（已集成 `HealthCheckServiceInstanceListSupplier` 建议，见优化提案 2.10）

---

## 2. Redis 不可用

**症状**：`RateLimitFallbackActive` 告警，限流降级为本地兜底。

**诊断**：
```bash
docker exec ydsz-dev-redis redis-cli ping
kubectl logs -n ydsz-prod -l app=redis | tail -50
```

**处置**：
1. 单节点故障 → 检查内存/持久化，重启
2. 集群故障 → 检查网络/主从切换
3. **期间系统行为**（已自动降级）：
   - 限流 → 本地兜底（按实例数分摊）
   - JWT 黑名单 → 降级放行（安全性轻微下降，恢复后自动恢复）
   - 本地缓存 → 继续服务

**预防**：Redis 主从 + 哨兵/集群，定期演练 Redis 故障降级

---

## 3. PostgreSQL 变慢

**症状**：接口 P99 上升，Druid 慢 SQL 日志增多，连接池使用率 > 80%。

**诊断**：
```sql
-- 1. 慢查询 TOP
SELECT query, calls, total_exec_time, mean_exec_time
FROM pg_stat_statements
ORDER BY mean_exec_time DESC LIMIT 20;

-- 2. 活跃连接
SELECT state, count(*) FROM pg_stat_activity GROUP BY state;

-- 3. 锁等待
SELECT * FROM pg_locks WHERE NOT granted LIMIT 20;
```

**处置**：
1. 慢 SQL → 检查执行计划 `EXPLAIN ANALYZE`，补充索引或改写
2. 锁等待 → 定位持锁事务，`pg_terminate_backend(pid)`
3. 连接耗尽 → 临时 `max_connections` 调大 + 排查连接泄漏（Druid `remove-abandoned` 已开启）

**预防**：慢 SQL 阈值 1s 监控（已配置）、索引 Review、大表分区（V1.0.1 脚本）

---

## 4. RocketMQ 消息积压

**症状**：`ConsumerLag` 指标上升，业务处理延迟。

**诊断**：
```bash
# RocketMQ 控制台查看消费进度
# 或
docker exec ydsz-dev-rocketmq sh mqadmin consumerProgress -n localhost:9876
```

**处置**：
1. 消费者速度不足 → 扩容消费者实例
2. 消费失败重试堆积 → 查看死信队列 `%DLQ%`，分析失败原因
3. 消费者挂掉 → 检查消费者日志，重启

**预防**：消费幂等（已实现 @Idempotent）、死信队列告警

---

## 5. Nacos 不可用

**症状**：服务注册失败，配置无法下发，新实例无法启动。

**诊断**：
```bash
curl -s http://localhost:8848/nacos/v1/console/health/readiness
```

**处置**：
1. 单机 Nacos 故障 → 重启（配置数据在 derby/mysql）
2. 集群 Nacos → 检查节点间通信
3. **期间系统行为**：已注册实例继续工作（心跳暂停但服务内存中保留路由），**新服务实例无法注册**

**预防**：Nacos 集群部署（≥3 节点）+ 数据库存储模式

---

## 6. JVM OOM

**症状**：`ExitOnOutOfMemoryError` 触发，Pod 重启，`/app/logs/heapdump` 生成堆转储。

**诊断**：
```bash
# 分析堆转储（Eclipse MAT / jhat）
jhat -port 7000 /app/logs/heapdump/*.hprof
```

**处置**：
1. 恢复：K8s 自动重启（Pod CrashLoop 需人工介入）
2. 分析：定位大对象/泄漏源（缓存未设上限？批量查询无分页？）
3. 修复：加缓存上限 / 限制查询 / 调大内存

**预防**：本地缓存设 `maximumSize`、批量接口强制分页、JVM 参数已含堆转储配置

---

## 7. 定时任务失败

**症状**：`cronjob` 任务 FAILED，SLA 告警。

**诊断**：
```bash
# 查看任务执行历史
curl -s http://localhost:9006/api/v1/job/history?jobId=xxx
# 查看执行日志
curl -s http://localhost:9006/api/v1/job/log?instanceId=xxx
```

**处置**：
1. 单次失败 → 触发重试（重试机制已内置）
2. 连续失败 → 查看脚本/处理器日志定位原因
3. Leader 选举异常 → 检查 Redis 锁租约

**预防**：失败告警（已集成）、自愈扫描（SelfHealingScanner 已实现）

---

## 事故报告模板

```markdown
# 事故报告

- 时间：YYYY-MM-DD HH:MM - HH:MM
- 影响：受影响服务 / 功能 / 时长
- 严重级别：P0 / P1 / P2

## 时间线
- HH:MM 收到告警
- HH:MM 定位根因
- HH:MM 恢复

## 根因分析
（5 Whys 分析）

## 改进项
1. [ ] 监控补盲
2. [ ] 代码修复
3. [ ] 流程优化
```
