# YDSZ 运维 Runbook 总览

> 对标 Google SRE 最佳实践：每个服务必须有故障处理 SOP（Runbook），
> 目标是让**任何值班工程师**在 15 分钟内按文档完成处置。

## 目录

| 文档 | 场景 | 优先级 |
|------|------|--------|
| [deployment.md](deployment.md) | 首次部署 / 滚动更新 / 回滚 | 常规 |
| [failure-handling.md](failure-handling.md) | 各类故障处置 SOP（汇总） | **应急** |
| [capacity-planning.md](capacity-planning.md) | 容量评估与扩容 | 常规 |
| [backup-restore.md](backup-restore.md) | 数据备份与恢复 | **应急** |
| [security-incident.md](security-incident.md) | 安全事件响应 | **应急** |

## 快速响应流程（黄金 15 分钟）

```
发现告警（钉钉/飞书/Grafana）
  → 1. 确认影响范围（哪个服务/租户/接口）
  → 2. 判断严重级别
       P0 全部不可用 / 数据损坏 / 安全事件 → 立即拉群 + 上报
       P1 部分功能不可用 → 值班处理 + 通知负责人
       P2 轻微影响 → 记录跟踪
  → 3. 按对应 Runbook 处置
  → 4. 恢复后 24h 内输出事故报告（时间线/根因/改进项）
```

## 值班检查表（每日）

- [ ] Grafana 各服务健康状态（up == 1）
- [ ] Prometheus 告警列表无未处理项
- [ ] 数据库连接池使用率 < 80%
- [ ] 消息队列积压 < 10000
- [ ] 磁盘使用率 < 80%（PG/MinIO/日志）
- [ ] 查看昨日错误日志峰值
