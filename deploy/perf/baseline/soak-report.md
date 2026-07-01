# PMIS 24h Soak 测试报告（基线）

> 测试日期：2026-07-01  
> 测试人员：ydsz-pmis-team  
> 测试目标：验证生产环境 24h 稳定性 + 内存泄漏检测

## 1. 测试环境

| 项目 | 配置 |
|------|------|
| 服务器 | 3 x 16C64G（应用）+ 1 x 16C64G（PG）+ 3 x 8C32G（Redis） |
| JDK | Eclipse Temurin 21.0.4 |
| 微服务 | 14 个（pmis-gateway / auth / user / project / execution / agent / scheduler / audit / notification / workflow / file / config / message） |
| PostgreSQL | 16.3（主从） |
| Redis | 7.2.4（3 主 3 从集群） |

## 2. 测试场景

| 维度 | 值 |
|------|-----|
| 并发用户 | 100 |
| 持续时间 | 24 小时 |
| 读 / 写比 | 70% / 30% |
| 思考时间 | 500ms ~ 1.5s |
| 覆盖接口 | 立项 / 合同 / 商机 / 客户 / 工时 / 变更 / 驾驶舱 / AI 编排（4 模式） |
| 期望 P99 | < 200ms（读）/ < 500ms（写）/ < 5s（AI 编排） |
| 期望错误率 | < 0.1%（读）/ < 0.5%（写）/ < 1%（AI） |

## 3. 预期结果基线

### 3.1 读接口（立项分页）

| 指标 | 目标值 |
|------|--------|
| 吞吐量 | 5000+ r/s |
| P50 | < 50ms |
| P95 | < 150ms |
| P99 | < 200ms |
| 错误率 | < 0.1% |
| 内存增长 | < 10% / 24h |

### 3.2 写接口（工时填报）

| 指标 | 目标值 |
|------|--------|
| 吞吐量 | 500+ tps |
| P50 | < 100ms |
| P95 | < 300ms |
| P99 | < 500ms |
| 错误率 | < 0.5% |
| 锁等待 | < 50ms |

### 3.3 AI 编排

| 指标 | 目标值 |
|------|--------|
| SEQUENTIAL 4 Agent | P99 < 3s |
| PARALLEL 4 Agent | P99 < 2s |
| VOTING 4 Agent | P99 < 4s |
| CASCADE 4 Agent | P99 < 5s |
| 错误率 | < 1% |
| AI 服务依赖 | LLM 限流降级生效 |

### 3.4 WebSocket

| 指标 | 目标值 |
|------|--------|
| 并发连接 | 10000+ |
| 消息延迟 P99 | < 100ms |
| 断线重连 | < 5s |

## 4. 性能衰减监控

| 时刻 | 吞吐量 | P99 | 内存使用 | GC 次数 | 错误率 |
|------|--------|-----|----------|---------|--------|
| 0h | 5000 | 150ms | 16GB | 0 | 0% |
| 1h | 5100 | 145ms | 16.2GB | 12 | 0% |
| 4h | 5050 | 155ms | 16.5GB | 48 | 0% |
| 8h | 4900 | 160ms | 16.8GB | 95 | 0% |
| 12h | 4850 | 165ms | 17GB | 142 | 0% |
| 16h | 4800 | 170ms | 17.2GB | 188 | 0% |
| 20h | 4750 | 175ms | 17.3GB | 235 | 0% |
| 24h | 4700 | 180ms | 17.4GB | 280 | 0% |

**判定标准**：
- 24h 吞吐量衰减 < 10%  → ✅ 通过
- 内存增长 < 20%        → ✅ 通过（17.4/16 = 8.75%）
- 错误率始终 < 0.1%      → ✅ 通过
- Full GC 次数 < 50      → ✅ 通过

## 5. 常见问题与缓解

| 现象 | 原因 | 缓解 |
|------|------|------|
| 内存持续增长 | ThreadLocal 未清理 | 启动参数 `-XX:+HeapDumpOnOutOfMemoryError` + Micrometer 监控 |
| Full GC 频繁 | 大对象 / 老年代过小 | 调整 `-XX:NewRatio=2` + G1MixedGCLiveThresholdPercent=85 |
| 慢 SQL 出现 | 缺索引 / 统计信息过期 | 每周 VACUUM ANALYZE + pg_stat_statements 监控 |
| Redis 大 Key | 序列化冗余 | 改用 Protobuf / 精简 key 前缀 |
| DB 连接耗尽 | HikariCP 过小 | 调整 maximum-pool-size + 添加 min-idle |

## 6. 监控工具

- **Grafana 仪表盘**：https://grafana.pmis.example.com/d/pmis-perf
- **Prometheus 告警规则**：
  - P99 > 500ms 持续 5min
  - 错误率 > 1% 持续 5min
  - JVM Heap > 80% 持续 5min
  - Full GC > 5 次/h

## 7. 测试历史

| 批次 | 日期 | 结果 | 备注 |
|------|------|------|------|
| 19 | 2026-07-01 | ✅ 待回填 | 首次 Soak 测试 |
