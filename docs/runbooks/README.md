# YDSZ 运维 Runbook 总览

> 对标 Google SRE Runbook 规范，为每个常见故障场景提供标准化处理 SOP

## 目录

| 文档 | 场景 | 严重级别 |
|------|------|---------|
| [deployment.md](./deployment.md) | 部署操作（首次/滚动更新/回滚） | - |
| [failure-handling.md](./failure-handling.md) | 故障处理总览 | - |
| [gateway-503.md](./gateway-503.md) | 网关 503 故障 | P1 |
| [nacos-unavailable.md](./nacos-unavailable.md) | Nacos 服务发现不可用 | P0 |
| [redis-outage.md](./redis-outage.md) | Redis 故障 | P0 |
| [postgres-slow.md](./postgres-slow.md) | 数据库性能下降 | P1 |
| [rocketmq-backlog.md](./rocketmq-backlog.md) | 消息积压 | P2 |
| [capacity-planning.md](./capacity-planning.md) | 容量规划指南 | - |
| [backup-restore.md](./backup-restore.md) | 备份恢复 | - |

## 使用原则

1. **告警触发 → 先查 Runbook**：收到 Prometheus 告警后，优先查阅对应 Runbook
2. **黄金 5 分钟**：P0 故障 5 分钟内必须开始执行 SOP
3. **故障复盘**：每次故障处理后更新 Runbook，补充遗漏步骤

## 告警联系方式

- P0 故障：立即电话通知 on-call 值班人员
- P1 故障：飞书群 @channel
- P2 故障：飞书群消息通知
