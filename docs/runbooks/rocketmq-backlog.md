# RocketMQ 消息积压处理

## 症状
- `RocketMQBacklog` 告警（consumer lag > 1000）
- Grafana 消费者堆积量曲线持续上升
- 消息处理延迟升高

## 影响
- 异步任务延迟执行
- 消息通知发送延迟
- P2 级别故障（消息系统允许一定延迟）

## 排查步骤

### Step 1: 确认积压情况

```bash
# 通过 RocketMQ Dashboard 或 Admin 工具检查
sh mqadmin consumerProgress -n rocketmq-namesrv:9876 -g <consumer_group>
```

### Step 2: 分析积压原因

#### 可能原因 1: 消费者处理慢
- 消费者调用下游服务超时
- 消费者内部逻辑耗时增加

#### 可能原因 2: 消费者实例减少
- 消费者 Pod 重启或缩容
- 消费者线程池满

#### 可能原因 3: 消息量突增
- 上游业务流量暴增
- 批量消息集中发送

### Step 3: 止血操作

#### 预案 A: 扩容消费者实例

```bash
# HorizontalPodAutoscaler 自动扩容
kubectl scale deployment <consumer-deployment> --replicas=<new_replicas> -n ydsz-prod

# 或修改 HPA 配置
kubectl patch hpa <hpa-name> -n ydsz-prod --patch '{"spec":{"minReplicas":<new_min>}}'
```

#### 预案 B: 检查并重启异常消费者

```bash
# 查看消费者 Pod 日志
kubectl logs -f deployment/<consumer> -n ydsz-prod --tail=100

# 重启异常消费者
kubectl rollout restart deployment/<consumer> -n ydsz-prod
```

#### 预案 C: 紧急情况 - 跳过积压消息

> ⚠️ 仅在消息可丢弃、接受丢失的场景下使用

1. 计算需要跳过的 offset
2. 通过 RocketMQ Admin 重置消费位点
3. 确认消费者恢复消费最新数据

### Step 4: 恢复后验证

```bash
# 确认消费积压量回落
# 通过 Grafana Consumer Lag 面板确认曲线下降

# 确认消费者消费速率正常
curl -s http://localhost:<port>/actuator/metrics/rocketmq.consumer.lag | jq
```

## 预防措施
1. 消费者并发线程数调优（根据下游处理能力配置）
2. 消费者超时时间合理设置
3. HPA 配置 CPU 扩容阈值
4. 消息量突增时上游限流保护
