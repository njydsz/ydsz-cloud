# 部署与回滚手册

## 一、首次部署（新环境）

```bash
# 1. 前置依赖
#   - K8s 集群 + Ingress Controller
#   - Nacos（集群）+ PostgreSQL + Redis + RocketMQ
#   - 镜像仓库 + Harbor

# 2. 初始化配置
#   - Nacos 中创建 ydsz 命名空间
#   - 下发共享配置：ydsz-common.yaml 系列（deploy/config/）
#   - 下发服务专属配置：ydsz-{service}-{env}.yaml

# 3. 初始化数据库
#   - 执行 deploy/sql/schema/*.sql（按版本顺序）
#   - 执行 deploy/sql/seed/*.sql（种子数据）

# 4. Helm 部署
helm repo add ydsz https://harbor.example.com/chartrepo/ydsz
helm upgrade --install ydsz-backend ydsz-backend/deploy/helm/ydsz-backend \
  --namespace ydsz-prod --create-namespace \
  --set image.tag=v1.0.0 \
  --set env=prod

# 5. 验证
kubectl rollout status deploy/ydsz-gateway -n ydsz-prod
curl -s http://<gateway>/actuator/health
```

## 二、滚动更新

```bash
# 标准流程（灰度发布推荐用 Argo Rollouts，见 deploy/k8s/rollout-template.yaml）
kubectl set image deploy/ydsz-project \
  ydsz-project=registry.cn-hangzhou.aliyuncs.com/ydsz/ydsz-project:v1.1.0 \
  -n ydsz-prod

# 观察滚动状态
kubectl rollout status deploy/ydsz-project -n ydsz-prod --timeout=5m
```

## 三、回滚

```bash
# 方式 1：回滚到上一版本
kubectl rollout undo deploy/ydsz-project -n ydsz-prod

# 方式 2：回滚到指定版本
kubectl rollout undo deploy/ydsz-project -n ydsz-prod --to-revision=3

# 方式 3（Helm）：回退 Helm release
helm rollback ydsz-backend <revision> -n ydsz-prod
```

**回滚决策标准**：发布后 10 分钟内出现以下任一情况立即回滚
- 错误率 > 5%
- P99 延迟 > 2x 基线
- 关键接口 5xx

## 四、发布检查清单

- [ ] 数据库迁移脚本已 Review 并通过 Schema 校验
- [ ] 压测通过（性能不退化）
- [ ] 配置已下发 Nacos 并验证
- [ ] 镜像已构建并扫描（OWASP）
- [ ] 灰度发布计划（5% → 25% → 50% → 100%）
