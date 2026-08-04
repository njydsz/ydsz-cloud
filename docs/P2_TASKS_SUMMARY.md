# P2-1: 灰度入口统一

> 创建日期：2026-08-05
> 状态：方案确认

## 一、职责边界

| 机制 | 层级 | 职责 |
|------|------|------|
| Argo Rollouts | 基础设施 / Ingress | 入口流量拆分：5% → 25% → 50% → 100% 阶梯式金丝雀/蓝绿发布 |
| GrayLoadBalancer | 应用层 / Spring Cloud LB | 服务间调用透传灰度标记（X-Gray-Tag），按 Nacos metadata.version 路由 |

## 二、并存冲突与维护负担

1. **规则冲突**：Argo 按副本比例切流，GrayLB 按 header 过滤。同一个请求可能被两层机制独立决定其走向，导致实际灰度比例偏离预期。
2. **双套实例标签**：Argo 用 canaryService/stableService 标记；GrayLB 用 Nacos metadata.version=gray。两套标签需独立维护，漂移后出现路由黑洞。
3. **运维成本**：问题排查需在 Ingress 层和应用层两头定位，缺乏统一的灰度控制面。
4. **行为不确定性**：若关闭 Argo 仅保留 GrayLB，灰度比例靠人工调整 metadata 权重；若关闭 GrayLB 仅靠 Argo，则服务内部调用无法透传灰度标记。

## 三、统一方案

**以 Argo Rollouts 为主（入口流量拆分），GrayLoadBalancer 降为可选辅助（用于服务内部调用透传灰度标记）。**

- 入口流量统一由 Argo Rollouts 控制阶梯权重；
- GrayLoadBalancer 默认仍开启（向后兼容），可通过 `ydsz.gray-loadbalancer.enabled=false` 关闭；
- 内部服务间调用如需 header-based 路由（如压测、定向灰度验证），由 GrayLoadBalancer 辅助透传。

## 四、落地步骤（已实施）

| 步骤 | 变更项 | 文件 |
|------|--------|------|
| 3.1 | 添加 `@ConditionalOnProperty` 开关 | `GrayLoadBalancerConfig.java` + `additional-spring-configuration-metadata.json` |
| 3.2 | 确认 Rollout 阶梯完整（已有 5→25→50→100） | `rollout-template.yaml` |
| 3.3 | 新建部署文档说明双机制协作 | `k8s/README.md` |
| 3.4 | Helm chart 注释说明灰度路由由 Argo 控制 | `helm/ydsz-backend/templates/deployment.yaml` |
