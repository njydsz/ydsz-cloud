#!/usr/bin/env bash
# =============================================================================
#  PMIS 金丝雀发布 - 流量切分脚本 (批次 20 P3-4 → 批次 23 P2-1 升级)
#  --------------------------------------------------------------------------
#  适用场景:
#    未启用 Argo Rollouts 的 staging / 边缘环境兜底
#    生产环境推荐: deploy/argo-rollouts/ Argo Rollouts Controller
#                  详见 docs/canary-deployment.md §3.3
#
#  流程 (兼容旧逻辑, 但已弃用):
#    1. 部署 canary 版本 (使用独立 Deployment + 独立 Service)
#    2. 调整 Spring Cloud Gateway 路由权重 (通过 Nacos config 热更新)
#    3. 监控关键指标 5min, 通过后提升权重直至 100%
#    4. 下线旧版本, 切换 Service selector
#
#  用法 (生产请改用 Argo Rollouts):
#    ./canary-shift.sh <service> <weight>
#    例: ./canary-shift.sh project 10   # 10% 流量到 canary
#        ./canary-shift.sh project 50
#        ./canary-shift.sh project 100  # 全量切换
#        ./canary-shift.sh project 0    # 回滚
#
#  推荐 (安装 argo rollouts plugin 后):
#    kubectl argo rollouts set image pmis-<svc> <svc>=registry/...:v1.x.x -n pmis-prod
#    kubectl argo rollouts status pmis-<svc> -n pmis-prod -w
#    kubectl argo rollouts abort pmis-<svc> -n pmis-prod     # 紧急回滚
# =============================================================================
set -euo pipefail

# ---------- 入参解析 ----------
# SERVICE: 微服务名, 例 project / finance / agent
# WEIGHT:  canary 权重 0-100, 缺省 10 (灰度起步量)
SERVICE="${1:-}"
WEIGHT="${2:-10}"

# ---------- 批次 23 P2-1 升级: 优先提示使用 Argo Rollouts ----------
# 检测 argo rollouts plugin 是否安装, 若已安装则提示更优方案
if kubectl argo rollouts version >/dev/null 2>&1; then
  cat <<EOF
[!] 检测到 Argo Rollouts plugin, 推荐使用 deploy/argo-rollouts/ 中的 Rollout 资源:

    kubectl argo rollouts set image pmis-${SERVICE:-execution} \\
        ${SERVICE:-execution}=registry/ydsz/ydsz-pmis-${SERVICE:-execution}:v1.x.x -n pmis-prod
    kubectl argo rollouts status pmis-${SERVICE:-execution} -n pmis-prod -w
    kubectl argo rollouts abort pmis-${SERVICE:-execution} -n pmis-prod

本脚本仅作 staging / 无 argo 环境的兜底使用, 继续执行 Nacos Gateway 权重切换...
EOF
fi

# ---------- Nacos 配置 ----------
# Nacos 服务地址, 默认指向生产集群的 K8s Service DNS
NACOS_SERVER="${NACOS_SERVER:-nacos.pmis-prod.svc.cluster.local:8848}"
# 命名空间隔离, 与 K8s namespace 对齐
NACOS_NAMESPACE="${NACOS_NAMESPACE:-pmis-prod}"
# 配置分组, 业务路由统一使用 DEFAULT_GROUP
NACOS_GROUP="${NACOS_GROUP:-DEFAULT_GROUP}"
# Spring Cloud Gateway 路由配置的 dataId (按服务拆分, 便于独立变更)
GATEWAY_DATA_ID="gateway-route-${SERVICE}.json"

# ---------- 参数校验 ----------
# SERVICE 非空 + WEIGHT 是 0-100 整数
if [[ -z "$SERVICE" || ! "$WEIGHT" =~ ^[0-9]+$ ]] || (( WEIGHT < 0 || WEIGHT > 100 )); then
  echo "Usage: $0 <service> <weight 0-100>" >&2
  exit 1
fi

# stable 权重 = 100 - canary 权重, 两者互补
STABLE_WEIGHT=$((100 - WEIGHT))

echo "===> 切分流量: ${SERVICE} stable=${STABLE_WEIGHT}% canary=${WEIGHT}%"
echo "===> 目标 Nacos: ${NACOS_SERVER} ns=${NACOS_NAMESPACE} dataId=${GATEWAY_DATA_ID}"
echo "===> ⚠️  生产环境建议迁移到 Argo Rollouts (deploy/argo-rollouts/)"

# ---------- 构造 Gateway 路由权重 JSON ----------
# 通过 Nacos OpenAPI 更新 Spring Cloud Gateway 路由权重
# 真实环境应使用 Nacos SDK / curl 推送, 此处给出 curl 示例
# Weight 过滤器: _genkey_0=权重值, _genkey_1=对应目标服务实例名
PAYLOAD=$(cat <<EOF
{
  "id": "route-${SERVICE}",
  "uri": "lb://${SERVICE}",
  "predicates": [{"name": "Path","args": {}}],
  "filters": [
    {"name": "Weight", "args": {"_genkey_0": "${STABLE_WEIGHT}", "_genkey_1": "${SERVICE}-stable"}},
    {"name": "Weight", "args": {"_genkey_0": "${WEIGHT}", "_genkey_1": "${SERVICE}-canary"}}
  ]
}
EOF
)

# 调用 Nacos OpenAPI 推送配置, Gateway 通过长连接监听自动热更新
curl -sS -X PUT "http://${NACOS_SERVER}/nacos/v1/cs/configs" \
  -d "dataId=${GATEWAY_DATA_ID}" \
  -d "group=${NACOS_GROUP}" \
  -d "namespaceId=${NACOS_NAMESPACE}" \
  --data-urlencode "content=${PAYLOAD}" \
  -o /dev/null -w "HTTP %{http_code}\n"

echo "===> 配置已推送, Gateway 大约 5s 内热更新"
echo "===> 监控建议:"
echo "    kubectl logs -n pmis-prod -l app=gateway --tail=200 | grep ${SERVICE}"
echo "    kubectl get pods -n pmis-prod -l app=${SERVICE} -o wide"
echo "    错误率阈值: < 0.5% 持续 5min 后可继续加压"
echo "===> 推荐升级: kubectl argo rollouts promote pmis-${SERVICE} -n pmis-prod"
