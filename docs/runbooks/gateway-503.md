# 网关 503 故障处理

## 症状
- 所有 API 请求返回 502/503
- Prometheus 告警 `ServiceDown` 触发
- Grafana 网关流量降为 0

## 影响
- 全量用户无法访问系统
- P0 级别故障

## 排查步骤

### Step 1（2 分钟内）: 确认网关 Pod 状态

```bash
# 查看网关 Pod 状态
kubectl get pods -n ydsz-prod -l app=ydsz-gateway

# 查看最近事件
kubectl get events -n ydsz-prod --field-selector involvedObject.name=ydsz-gateway-xxx --sort-by='.lastTimestamp'
```

**可能输出**：
- `CrashLoopBackOff`：Pod 启动后反复崩溃
- `OOMKilled`：内存不足被 Kill
- `Pending`：调度失败（资源不足）
- `ImagePullBackOff`：镜像拉取失败

### Step 2（5 分钟内）: 查看网关日志

```bash
# 实时日志
kubectl logs -f deployment/ydsz-gateway -n ydsz-prod --tail=200

# 崩溃前的日志
kubectl logs deployment/ydsz-gateway -n ydsz-prod --previous --tail=500
```

**常见错误**：

| 错误 | 原因 | 处理 |
|------|------|------|
| `Connection refused: nacos:8848` | Nacos 不可用 | 检查 Nacos 状态，切换本地配置降级 |
| `OutOfMemoryError` | 内存不足 | 临时扩容 JVM 堆，排查内存泄漏 |
| `BeanCreationException` | Spring 容器启动失败 | 检查 Nacos 配置变更、依赖配置 |

### Step 3: 止血操作

#### 场景 A: Nacos 不可用
1. 网关 fallback 到本地配置启动
2. 检查 Nacos 服务状态
3. 如 Nacos 单节点，考虑重启 Nacos Pod

#### 场景 B: OOM 导致重启
1. 临时扩容 JVM 堆内存：
   ```bash
   kubectl set env deployment/ydsz-gateway -n ydsz-prod JAVA_OPTS="-Xmx2g -Xms2g"
   ```
2. 分析内存泄漏根因

#### 场景 C: 启动失败
1. 查看最近配置变更
   ```bash
   # 回滚最近一次配置修改
   kubectl rollout undo deployment/ydsz-gateway -n ydsz-prod
   ```

### Step 4: 恢复后验证

```bash
# 确认 Pod Running
kubectl get pods -n ydsz-prod -l app=ydsz-gateway

# 确认健康检查通过
kubectl port-forward svc/ydsz-gateway 9000:9000 -n ydsz-prod &
curl -sf http://localhost:9000/actuator/health | jq .

# 验证关键接口
curl -sf http://localhost:9000/api/v1/system/config/health
```

## 预防措施
1. 网关配置多副本（最少 2 个），滚动更新
2. JVM 参数配置 `-XX:+HeapDumpOnOutOfMemoryError`
3. Nacos 高可用部署（集群模式）
