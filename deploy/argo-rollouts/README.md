# PMIS Argo Rollouts 金丝雀发布 (批次 23 P2-1)
#
# Argo Rollouts 是 K8s 增强部署控制器, 提供比原生 Deployment 更细粒度的发布策略
# (渐进流量切分 / 自动分析 / 一键回滚), 是 canary-shift.sh 手动脚本的替代方案.
#
# 前置条件:
#   1) 已安装 argo rollouts controller
#      kubectl create namespace argo-rollouts
#      kubectl apply -n argo-rollouts -f https://github.com/argoproj/argo-rollouts/releases/latest/download/install.yaml
#      安装 kubectl plugin: https://argo-rollouts.readthedocs.io/en/latest/installation/#kubectl-plugin-installation
#
#   2) Prometheus 已部署 (本目录 AnalysisTemplate 引用)
#
#   3) 原 Deployment 已迁出, 改用 Rollout 接管 (本目录 base/execution-rollout.yaml 范例)
#
# 用法 (替换原有 canary-shift.sh):
#   kubectl argo rollouts set image pmis-execution execution=registry/ydsz/ydsz-pmis-execution:v1.2.0-rc1 -n pmis-prod
#   kubectl argo rollouts promote pmis-execution -n pmis-prod       # 推进到下一步
#   kubectl argo rollouts abort pmis-execution -n pmis-prod          # 紧急中止 + 回滚
#   kubectl argo rollouts status pmis-execution -n pmis-prod -w      # 实时观察
#   kubectl argo rollouts dashboard                                  # 本地 web 面板
#
# 目录结构:
#   base/                  - 公共资源 (Service / AnalysisTemplate / IngressRoute)
#   overlays/prod/         - 生产环境 kustomize overlay
#   examples/              - 各服务 Rollout 范例 (execution/project/finance/...)
#   README.md              - 本文件
#   ops-commands.md        - 运维命令速查
