# Nacos 服务发现不可用处理

## 症状
- 服务实例列表为空或不变
- 配置刷新失败
- 新服务注册失败

## 影响
- 服务间调用路由失败
- 动态配置不可用
- P0 级别故障（如 Nacos 长时间不可用）

## 排查步骤

### Step 1: 确认 Nacos 服务状态

```bash
# Nacos Pod 状态
kubectl get pods -n ydsz-infra -l app=nacos

# Nacos 健康检查
curl -sf http://nacos:8848/nacos/v1/console/health
```

### Step 2: 查看 Nacos 日志

```bash
kubectl logs -f deployment/nacos -n ydsz-infra --tail=100
```

### Step 3: 止血操作

#### 临时恢复（单节点 Nacos）
1. 重启 Nacos Pod
   ```bash
   kubectl delete pod <nacos-pod> -n ydsz-infra
   ```

2. 确认 Nacos 注册中心恢复
   ```bash
   # 查询已注册服务列表
   curl -s http://nacos:8848/nacos/v2/ns/operator/metrics | jq .
   ```

#### 降级策略
Nacos 短时间不可用时，各服务使用本地缓存的服务列表继续运行（已在代码中实现）：
- 服务发现：使用本地缓存的服务实例列表
- 配置中心：使用本地已加载的配置值
- 注册中心：服务心跳失败不影响已注册状态（TTL 内仍有效）

## 验证恢复

```bash
# 查看服务注册状态（每个微服务）
curl http://localhost:<port>/actuator/nacos-discovery | jq '.services'
```
