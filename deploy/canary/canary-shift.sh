# =============================================================================
# PMIS 金丝雀发布流量切分脚本 (批次 20 P3-4)
#
# 适用场景: 未启用 Istio, 仅使用 K8s Service + Spring Cloud Gateway 权重路由
# 流程:
#   1. 部署 canary 版本 (使用独立 Deployment + 独立 Service)
#   2. 调整 Spring Cloud Gateway 路由权重 (通过 Nacos config 热更新)
#   3. 监控关键指标 5min, 通过后提升权重直至 100%
#   4. 下线旧版本, 切换 Service selector
#
# 用法:
#   ./canary-shift.sh <service> <weight>
#   例: ./canary-shift.sh execution 10   # 10% 流量到 canary
#       ./canary-shift.sh execution 50
#       ./canary-shift.sh execution 100  # 全量切换
#       ./canary-shift.sh execution 0    # 回滚
# =============================================================================
#!/usr/bin/env bash
set -euo pipefail

SERVICE="${1:-}"
WEIGHT="${2:-10}"
NACOS_SERVER="${NACOS_SERVER:-nacos.pmis-prod.svc.cluster.local:8848}"
NACOS_NAMESPACE="${NACOS_NAMESPACE:-pmis-prod}"
NACOS_GROUP="${NACOS_GROUP:-DEFAULT_GROUP}"
GATEWAY_DATA_ID="gateway-route-${SERVICE}.json"

if [[ -z "$SERVICE" || ! "$WEIGHT" =~ ^[0-9]+$ ]] || (( WEIGHT < 0 || WEIGHT > 100 )); then
  echo "Usage: $0 <service> <weight 0-100>" >&2
  exit 1
fi

STABLE_WEIGHT=$((100 - WEIGHT))

echo "===> 切分流量: ${SERVICE} stable=${STABLE_WEIGHT}% canary=${WEIGHT}%"
echo "===> 目标 Nacos: ${NACOS_SERVER} ns=${NACOS_NAMESPACE} dataId=${GATEWAY_DATA_ID}"

# 通过 Nacos OpenAPI 更新 Spring Cloud Gateway 路由权重
# 真实环境应使用 Nacos SDK / curl 推送, 此处给出 curl 示例
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
