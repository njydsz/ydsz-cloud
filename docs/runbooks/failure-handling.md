# 故障处理总览

## 故障分级

| 级别 | 响应时间 | 处理原则 |
|------|---------|---------|
| **P0-紧急** | 立即（5分钟内） | 服务不可用，全量用户受影响 |
| **P1-严重** | 15分钟内 | 核心功能降级，部分用户受影响 |
| **P2-一般** | 1小时内 | 非核心功能异常，可控范围内 |
| **P3-低优** | 工作时间内 | 性能优化、体验改善类问题 |

## 常见故障场景

### P0-紧急

| 故障 | 现象 | 处理手册 |
|------|------|---------|
| 全服务宕机 | 所有接口返回 502/503 | [gateway-503.md](./gateway-503.md) |
| Nacos 不可用 | 服务发现失败、配置无法刷新 | [nacos-unavailable.md](./nacos-unavailable.md) |
| Redis 宕机 | 限流降级、缓存穿透 | [redis-outage.md](./redis-outage.md) |

### P1-严重

| 故障 | 现象 | 处理手册 |
|------|------|---------|
| 数据库变慢 | 查询耗时 > 3s，接口超时 | [postgres-slow.md](./postgres-slow.md) |
| 网关 P99 突增 | 大量请求超时 | [gateway-503.md](./gateway-503.md) |
| OOM 告警 | Pod 被 Kill，频繁重启 | [failure-handling.md](./failure-handling.md#oom-处理) |

### P2-一般

| 故障 | 现象 | 处理手册 |
|------|------|---------|
| 消息积压 | MQ consumer lag 增长 | [rocketmq-backlog.md](./rocketmq-backlog.md) |
| 慢 SQL | 数据库 CPU 升高 | [postgres-slow.md](./postgres-slow.md) |

---

## 故障发现与通知

### 告警来源
- Prometheus AlertManager → 飞书/钉钉 Webhook
- Jaeger 错误链路追踪
- Grafana Dashboard 异常

### 故障处理流程

```
1. 告警触发
   ↓
2. On-call 确认告警（5 分钟确认）
   ↓
3. 查阅对应 Runbook
   ↓
4. 执行止血操作（优先恢复服务）
   ↓
5. 根因分析（恢复后）
   ↓
6. 更新 Runbook（记录遗漏步骤）
```

---

## 紧急联系

| 角色 | 联系方式 | 备注 |
|------|---------|------|
| Backend On-call | 飞书 on-call 群 | 7x24 值班 |
| DBA | 飞书工单 | 工作时间 |
| SRE | Slack #incident | 严重故障 |

---

## OOM 处理

**症状**：Pod 频繁重启，JVM 堆内存告警

**止血步骤**：
1. 查看 OOM 日志
   ```bash
   kubectl logs <pod> -n ydsz-prod --previous | grep -A 20 "OutOfMemoryError"
   ```

2. 获取 Heap Dump
   ```bash
   kubectl exec <pod> -n ydsz-prod -- jmap -dump:format=b,file=/tmp/heap.hprof 1
   kubectl cp <pod>:/tmp/heap.hprof ./heap.hprof -n ydsz-prod
   ```

3. 临时扩容 Pod 重启间隔，避免频繁重启影响服务
4. 分析 Heap Dump（Eclipse MAT 或 VisualVM）

---

## 数据库连接耗尽

**症状**：`DataSourcePoolExhausted` 告警，接口响应超时

**止血步骤**：
1. 检查活跃连接数和慢 SQL
   ```bash
   # 查看当前连接数
   psql -h <PG_HOST> -U ydsz -d ydsz_pmis -c "SELECT count(*) FROM pg_stat_activity WHERE datname='ydsz_pmis';"
   
   # 查看慢查询
   psql -h <PG_HOST> -U ydsz -d ydsz_pmis -c "SELECT pid, now()-query_start AS duration, query FROM pg_stat_activity WHERE state='active' AND now()-query_start > interval '5 seconds' ORDER BY duration DESC;"
   ```

2. 如有长时间运行的查询，评估是否需要 kill
   ```sql
   SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE state='active' AND now()-query_start > interval '1 minute';
   ```

3. 临时扩容连接池（修改 Nacos 配置后刷新）
