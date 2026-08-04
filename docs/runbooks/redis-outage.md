# Redis 故障处理

## 症状
- `RedisDown` 告警触发
- `RateLimitFallbackActive` 告警（限流降级到本地兜底模式）
- 接口响应变慢（缓存穿透到数据库）
- JWT 校验延迟升高

## 影响
- 限流精度下降（多实例各自独立计数）
- 缓存穿透，数据库压力升高
- JWT 校验从缓存命中变为实时解析
- P0 级别故障

## 排查步骤

### Step 1: 确认 Redis 状态

```bash
# Redis Pod 状态
kubectl get pods -n ydsz-infra -l app=redis

# Redis CLI 检查
kubectl exec -it redis-0 -n ydsz-infra -- redis-cli -a ydsz123 ping
kubectl exec -it redis-0 -n ydsz-infra -- redis-cli -a ydsz123 info server | grep redis_version
```

### Step 2: 查看 Redis 日志

```bash
kubectl logs -f deployment/redis -n ydsz-infra --tail=200
```

### Step 3: 止血操作

#### 场景 A: Redis 宕机（单节点）

1. **确认限流降级状态**
   ```bash
   # 检查网关指标
   curl -s http://localhost:9000/actuator/metrics/gateway.ratelimit.fallback.active | jq
   ```
   预期值为 `1`，表示限流已降级到本地兜底模式（按实例数分摊配额）

2. **重启 Redis**
   ```bash
   kubectl rollout restart deployment/redis -n ydsz-infra
   kubectl rollout status deployment/redis -n ydsz-infra
   ```

3. **确认限流恢复正常**
   ```bash
   # 网关限流降级指标应恢复为 0
   curl -s http://localhost:9000/actuator/metrics/gateway.ratelimit.fallback.active | jq
   ```

#### 场景 B: Redis 内存不足

1. **查看内存使用**
   ```bash
   kubectl exec -it redis-0 -n ydsz-infra -- redis-cli -a ydsz123 info memory
   ```

2. **触发缓存淘汰（如配置的是 noeviction 策略）**
   ```bash
   # 修改淘汰策略为 allkeys-lru（临时）
   kubectl exec -it redis-0 -n ydsz-infra -- redis-cli -a ydsz123 CONFIG SET maxmemory-policy allkeys-lru
   # 持久化修改（更新 Helm values 或 ConfigMap）
   ```

3. **确认大 Key**
   ```bash
   kubectl exec -it redis-0 -n ydsz-infra -- redis-cli -a ydsz123 --bigkeys
   ```

### Step 4: 恢复后验证

```bash
# Redis 健康检查
kubectl exec -it redis-0 -n ydsz-infra -- redis-cli -a ydsz123 ping

# 检查连接数是否正常
kubectl exec -it redis-0 -n ydsz-infra -- redis-cli -a ydsz123 info clients

# 检查网关限流状态
curl -s http://localhost:9000/actuator/metrics/gateway.ratelimit.fallback.active | jq '.measurements[0].value'
# 应为 0
```

## 预防措施
1. Redis 部署 Sentinel 或 Cluster 模式实现高可用
2. 内存告警阈值设置 80%
3. 定期执行 `redis-cli --bigkeys` 检查大 Key
4. 设置合理的 `maxmemory-policy`（推荐 `allkeys-lru`）
