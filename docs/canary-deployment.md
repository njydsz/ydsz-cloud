# PMIS 金丝雀发布 (Canary Deployment)

> 批次 20 P3-4 | 适用: PMIS 全量 14 个 Spring Cloud 微服务 + 1 个前端

## 1. 适用场景
- 涉及 **线上 24h 服务** 的功能发布 (常规功能 / 优化 / 重构)
- 需要 **细粒度流量控制** 的场景 (5% → 25% → 50% → 100%)
- 业务对 **错误率敏感** (PMIS 经营驾驶舱 / 财务开票等核心域)

不适用:
- 数据库 schema 变更 (必须先扩列 + 双写 + 切读 + 切写)
- 一次性迁移任务 (执行型 Job, 应放 ops-ticket 离线执行)
- 安全 patch (建议直接全量, 通过 SAST / SCA 阻断在 CI)

## 2. 基础设施选型

| 方案 | 优点 | 缺点 | 适用 |
|------|------|------|------|
| **Istio VirtualService** | header / cookie 路由、按权重切分、流量镜像 | sidecar 资源开销、运维复杂 | 生产 |
| **Spring Cloud Gateway WeightFilter** | 零依赖, 走 Nacos config 热更新 | 仅权重路由、无流量镜像 | 准生产 / 中小规模 |
| **K8s Deployment + Service 双版本** | 纯 K8s 原生 | 切换成本高 (改 selector) | 已废弃, 不推荐 |

> PMIS 推荐: **生产** 用 Argo Rollouts (批次 23 P2-1), **staging** 用 SCG WeightFilter (`canary-shift.sh`)。

## 3. Istio 金丝雀发布流程 (推荐)

### 3.1 前置条件
1. 微服务 namespace 已开启 sidecar 注入: `kubectl label namespace pmis-prod istio-injection=enabled`
2. `gateway` 服务本身不参与金丝雀 (它路由到后端), 仅对 backend (project/iam/...) 切分
3. 当前版本 (stable) 的 Deployment labels 必须包含 `version: <tag>`

### 3.2 执行步骤

```bash
# 1. 部署 canary 版本 (与 stable 共存, 但暂时无流量)
kubectl set image deployment/pmis-project project=registry/.../ydsz-pmis-project:v1.1.0-rc1 -n pmis-prod
kubectl rollout status deployment/pmis-project -n pmis-prod --timeout=300s

# 2. 启用 VirtualService (5% 流量到 canary)
helm upgrade pmis helm/pmis --reuse-values \
  --set canary.enabled=true \
  --set canary.serviceName=project \
  --set canary.stableTag=v1.0.0 \
  --set canary.canaryTag=v1.1.0-rc1 \
  --set canary.weight=5 \
  -n pmis-prod

# 3. 内部员工验证 (header)
curl -H "x-pmis-canary: enabled" https://pmis.ydsz-pmis.cn/api/v1/execution/...

# 4. 观察 1h, 检查以下指标 (任一不达标立即回滚)
#    - 错误率 < 0.5%
#    - p99 延迟 < 800ms
#    - CPU/Memory < 80%
#    - Sentry 新错误数 = 0

# 5. 提升权重
helm upgrade pmis helm/pmis --reuse-values --set canary.weight=25
# 1h 观察后再提升到 50%, 100%

# 6. 全量切换后, 更新 stable tag, 下线 canary 标签
helm upgrade pmis helm/pmis --reuse-values \
  --set canary.canaryTag=v1.1.0 --set canary.weight=100
# 监控 24h 稳定后, 删除 canary VirtualService
kubectl delete virtualservice execution-canary -n pmis-prod
```

### 3.3 紧急回滚 (< 30s)

```bash
# 方案 A: 100% 切回 stable
helm upgrade pmis helm/pmis --reuse-values --set canary.weight=0

# 方案 B: 直接 rollback 镜像
kubectl rollout undo deployment/pmis-execution -n pmis-prod
```

## 4. Spring Cloud Gateway + Nacos 方案 (轻量)

适用于 staging 或未启用 Istio 的环境, 见 `deploy/canary/canary-shift.sh`。

```bash
# 初始 (5%)
./canary-shift.sh execution 5
# 1h 后
./canary-shift.sh execution 25
./canary-shift.sh execution 50
./canary-shift.sh execution 100
# 紧急回滚
./canary-rollback.sh execution
```

Gateway 大约 5s 内热更新, 通过 actuator/gateway/routes 验证:
```bash
curl http://gateway:9000/actuator/gateway/routes
```

## 4.5 Argo Rollouts 方案 (生产推荐, 批次 23 P2-1)

替代 canary-shift.sh 手动脚本, 提供 **自动化分析 + 一键回滚**。

### 4.5.1 安装 (一次性)

```bash
# 1. 部署 controller
kubectl create namespace argo-rollouts
kubectl apply -n argo-rollouts -f https://github.com/argoproj/argo-rollouts/releases/latest/download/install.yaml

# 2. 安装 kubectl plugin
brew install argoproj/tap/kubectl-argo-rollouts   # macOS
# Linux: 见 https://argo-rollouts.readthedocs.io/en/latest/installation/

# 3. 部署 PMIS Rollout 资源
kubectl apply -k deploy/argo-rollouts/overlays/prod
```

### 4.5.2 日常发布 (替代 canary-shift.sh)

```bash
# 1. 启动金丝雀: 推送新镜像
kubectl argo rollouts set image pmis-project \
  project=registry.ydsz-pmis.cn/ydsz/ydsz-pmis-project:v1.2.0-rc1 \
  -n pmis-prod

# 2. 实时观察 (Argo 自动执行 pause + analysis 5%→25%→50%→100%)
kubectl argo rollouts status pmis-project -n pmis-prod -w

# 3. 紧急回滚 (< 5s)
kubectl argo rollouts abort pmis-project -n pmis-prod
# 或直接回退到上一个版本
kubectl argo rollouts undo pmis-project -n pmis-prod

# 4. Web Dashboard
kubectl argo rollouts dashboard   # 默认 http://localhost:3100
```

### 4.5.3 自动分析模板

`deploy/argo-rollouts/base/analysis-templates.yaml` 提供 3 个模板:
- `error-rate-check`: 5xx 错误率 < 0.5% (默认)
- `latency-p99-check`: p99 延迟 < 800ms
- `composite-check`: 错误率 + 延迟同时检查

阈值可在 Rollout `args` 中按服务覆盖, 如财务服务更严: `error-rate-threshold=0.003`。

详细运维命令见 `deploy/argo-rollouts/ops-commands.md`。

## 5. 决策矩阵

| 阶段 | 持续时间 | 通过条件 | 失败行动 |
|------|----------|----------|----------|
| Canary 5% | 1h | 错误率 < 0.5% | 立即回滚 |
| Canary 25% | 1h | 错误率 < 0.5% | 回滚到 5% 或 0 |
| Canary 50% | 1h | 错误率 < 0.5% | 回滚到 25% |
| Canary 100% | 24h | Sentry 0 错误 + 监控基线正常 | 触发灾备 SOP |

## 6. 监控信号

| 指标 | 来源 | 阈值 |
|------|------|------|
| 错误率 | Prometheus `http_requests_total{status=~"5.."}` | < 0.5% |
| p99 延迟 | Prometheus `http_request_duration_seconds` | < 800ms |
| Sentry 新 issue | Sentry API | 0 (1h 内) |
| CPU 使用率 | K8s metrics-server | < 80% |
| DB 连接数 | HikariCP metrics | < 80% max pool |
| 慢 SQL | pg_stat_statements | < 3s |

任一阈值越界, 立即回滚, 并在 `docs/operations/post-mortem-<date>.md` 记录。

## 7. 与 FeatureFlag 联动

金丝雀发布仅控制 **版本粒度** (整个 jar), 业务特性粒度由 FeatureFlag 控制:
- 灰度新功能: `POST /api/v1/feature-flags/AGENT_ORCHESTRATION/rollout?percentage=10`
- 紧急关闭: `PUT /api/v1/feature-flags/AGENT_ORCHESTRATION/enabled?enabled=false`
- 不需重启服务, 通过 Nacos config 热更新

## 8. 与混沌工程联动

金丝雀验证通过后, 仍需在稳定版本上进行 **混沌工程实验** (见 `docs/chaos-engineering.md`):
- 对金丝雀目标服务注册 LATENCY (500ms) 实验
- 验证熔断器 (Sentinel) 是否能 100% 兜底
- 验证下游服务的 fallback 是否生效

## 9. 责任分工

| 角色 | 责任 |
|------|------|
| 开发 | 提交 MR + 标注"需金丝雀" + 写发版说明 |
| CI | 部署 canary 到 staging, 跑 E2E |
| SRE | 生产金丝雀流量切分 + 监控 + 回滚 |
| QA | Canary 阶段功能验证 |
| PM | 100% 后业务验收 |

## 10. 常见问题

**Q: Canary 阶段出现 P0 故障, 但 SLO 未越界, 怎么办?**
A: 以业务影响为先, 立即回滚, 不必等监控阈值。

**Q: 数据库 schema 变更能否走金丝雀?**
A: 不行。Schema 变更必须按 expand → migrate → dual-write → cutover 流程, 详见 `docs/standards/database-spec.md`。

**Q: 前端 (Vue) 如何金丝雀?**
A: 走 Nginx Ingress canary annotation 或 Argo Rollouts, 不影响后端金丝雀。
