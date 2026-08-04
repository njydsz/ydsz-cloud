# 容量规划指南

## 一、容量评估模型

| 服务 | 单实例容量（经验值） | 扩缩依据 |
|------|---------------------|---------|
| gateway | 2000 QPS / 1C2G | CPU > 70%（HPA 已配置） |
| userinfo | 1000 QPS / 1C2G | CPU + 连接数 |
| project | 800 QPS / 2C4G | CPU + 慢 SQL 数量 |
| workflow | 500 QPS / 2C4G | CPU + 流程实例数 |
| agent | 20 并发对话 / 2C4G | 并发数 + Token 消耗（LLM 是瓶颈） |
| cronjob | 100 任务/分 / 1C2G | 任务数 + 执行时长 |

## 二、数据库容量

| 指标 | 阈值 | 动作 |
|------|------|------|
| PG 磁盘 | > 70% | 清理归档 / 扩容 |
| PG 连接数 | > 80% | 检查连接泄漏 / 扩容 |
| Redis 内存 | > 70% | 清理过期 key / 加内存 |
| MinIO 磁盘 | > 70% | 配置生命周期策略清理 |
| 消息积压 | > 10000 | 扩消费者 / 排查消费失败 |

## 三、扩容操作

```bash
# 手动扩容（临时）
kubectl scale deploy/ydsz-project --replicas=5 -n ydsz-prod

# HPA 自动扩容（已配置）
# 触发条件：CPU > 70%，min=3 max=10
kubectl get hpa -n ydsz-prod
```

## 四、容量测试数据维护

每次压测后更新 `load-test/reports/` 中的基线数据：
- 压测环境规格（CPU/内存/PG 配置）
- 各服务 QPS 上限
- 数据库连接池最大使用量
- 瓶颈点记录
