# YDSZ 后端 · K8s 部署与灰度发布说明

> 适用目录：`ydsz-backend/deploy/k8s/`
> 创建日期：2026-08-05

## 一、灰度发布通道：Argo Rollouts 为首选

本项目统一以 **Argo Rollouts** 作为灰度发布入口（基础设施层流量拆分），GrayLoadBalancer 降为可选的辅助组件（服务间调用透传灰度标记）。

| 机制 | 层级 | 用途 |
|------|------|------|
| Argo Rollouts | Ingress / Service Mesh | 入口流量按比例切分（5% → 25% → 50% → 100%） |
| GrayLoadBalancer | Spring Cloud Gateway LB | 服务间调用透传 `X-Gray-Tag` header |

## 二、Rollout 模板使用

```bash
# 应用 rollout（替换同名 Deployment）
kubectl apply -f deploy/k8s/rollout-template.yaml

# 查看发布状态
kubectl argo rollouts get rollout ${SERVICE_NAME} --watch

# 手动推进到下一阶段
kubectl argo rollouts promote ${SERVICE_NAME}

# 回滚
kubectl argo rollouts abort ${SERVICE_NAME}
```

## 三、Gateway 灰度头（X-Gray-Tag）说明

- 网关 `GrayLoadBalancerRequestFilter` 从请求头 `X-Gray-Tag` 或查询参数 `gray=true` 中提取灰度标识
- 下游服务间调用通过 GrayLoadBalancer 按 `Nacos metadata.version` 路由到对应实例
- CORS allowedHeaders 已包含 `X-Gray-Tag`，网关已放行该头

## 四、关闭 GrayLoadBalancer

当不需要基于 header 的 service-level 灰度路由时，可在网关配置中关闭：

```yaml
ydsz:
  gray-loadbalancer:
    enabled: false
```

关闭后入口流量完全由 Argo Rollouts 控制。

## 五、文件清单

| 文件 | 用途 |
|------|------|
| `rollout-template.yaml` | Argo Rollout 金丝雀发布模板（5%→25%→50%→100%） |
| `deployment-template.yaml` | 普通 Deployment 模板（无灰度场景使用） |
| `service-template.yaml` | Service 模板 |
| `hpa-template.yaml` | HPA 自动扩缩容模板 |
| `kustomization.yaml` | Kustomize 入口 |
| `servicemonitor-template.yaml` | Prometheus Operator ServiceMonitor |
