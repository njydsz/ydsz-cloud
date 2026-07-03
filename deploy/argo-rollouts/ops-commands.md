# Argo Rollouts 运维命令速查 (批次 23 P2-1)
#
# 适用: PMIS 7 个后端微服务 + 1 个前端 (gateway 是路由层, 不参与金丝雀)
# 部署前提: argo rollouts controller 已安装, kubectl-argo-rollouts plugin 已配置

## 1. 安装 / 升级 argo rollouts controller (一次性)
kubectl create namespace argo-rollouts
kubectl apply -n argo-rollouts -f https://github.com/argoproj/argo-rollouts/releases/latest/download/install.yaml
# 装 kubectl plugin (macOS):
brew install argoproj/tap/kubectl-argo-rollouts
# Linux: 见 https://argo-rollouts.readthedocs.io/en/latest/installation/#kubectl-plugin-installation

## 2. 部署 PMIS Rollout 资源
kubectl apply -k deploy/argo-rollouts/overlays/prod

## 3. 日常金丝雀发布 (替换 canary-shift.sh)
# 3.1 启动金丝雀: 部署新版本镜像
kubectl argo rollouts set image pmis-project \
  project=registry.ydsz-pmis.cn/ydsz/ydsz-pmis-project:v1.2.0-rc1 \
  -n pmis-prod

# 3.2 实时观察进度
kubectl argo rollouts status pmis-project -n pmis-prod -w

# 3.3 手动跳过 pause (一般不需要, 自动化分析通过即推)
kubectl argo rollouts promote pmis-project -n pmis-prod

# 3.4 紧急回滚 (< 5s, 比 canary-rollback.sh 快)
kubectl argo rollouts abort pmis-project -n pmis-prod
# 或一键回退到上一个版本
kubectl argo rollouts undo pmis-project -n pmis-prod

## 4. 验证当前 Rollout 状态
kubectl argo rollouts get rollout pmis-project -n pmis-prod
kubectl argo rollouts list rollouts -n pmis-prod

## 5. 查看分析历史 (错误率趋势)
kubectl get analysisrun -n pmis-prod
kubectl describe analysisrun <name> -n pmis-prod

## 6. Web Dashboard (本地)
kubectl argo rollouts dashboard
# 浏览器打开 http://localhost:3100

## 7. 紧急情况快速处置
# 7.1 错误率越界时, Argo 会自动 abort, 但仍要确认
kubectl argo rollouts get rollout pmis-project -n pmis-prod -o yaml | grep -A 3 status
# 7.2 强制跳过分析 (应急, 不推荐)
kubectl argo rollouts set image pmis-project project=... --skip-analytics=true -n pmis-prod
# 7.3 完全跳过金丝雀, 直接全量 (DANGEROUS)
kubectl argo rollouts set image pmis-project project=... --full-promotion=true -n pmis-prod

## 8. 与 Istio 集成 (生产推荐, 但需先部署 Istio mesh)
# 在 Rollout strategy 中添加:
#   trafficRouting:
#     istio:
#       virtualService:
#         name: pmis-project-vs
#         routes:
#           - primary
# 详见: https://argo-rollouts.readthedocs.io/en/latest/traffic-management/istio/

## 9. 故障排查
# 9.1 Rollout 卡住不前进
kubectl argo rollouts get rollout pmis-project -n pmis-prod -o yaml
# 9.2 AnalysisRun 一直 Inconclusive
kubectl describe analysisrun -n pmis-prod | grep -A 5 Metrics
# 9.3 Prometheus 不可达
kubectl logs -n argo-rollouts -l app=argo-rollouts --tail=100 | grep -i prom

## 10. 批量操作 (CI 集成)
# 发布所有服务:
for svc in execution project finance agent; do
  kubectl argo rollouts set image pmis-$svc $svc=registry/...:v1.2.0 -n pmis-prod
done
# 等全部完成:
for svc in execution project finance agent; do
  kubectl argo rollouts status pmis-$svc -n pmis-prod --timeout 30m
done
