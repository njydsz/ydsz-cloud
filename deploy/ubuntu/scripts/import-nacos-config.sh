#!/usr/bin/env bash
# =============================================================================
#  YDSZ PMIS · Nacos 共享配置导入脚本
# -----------------------------------------------------------------------------
#  用途:    将 deploy/common/nacos/ydsz-common.yaml 导入到 Nacos
#  依赖:    curl / nacos 已启动 (http://127.0.0.1:8848)
#  用法:    ./deploy/ubuntu/scripts/import-nacos-config.sh [namespace] [group]
#           默认: namespace=pmis, group=dev
# =============================================================================
set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../../.." && pwd)"

NAMESPACE=${1:-pmis}
GROUP=${2:-dev}
NACOS_ADDR=${NACOS_SERVER_ADDR:-127.0.0.1:8848}
USERNAME=${NACOS_USERNAME:-nacos}
PASSWORD=${NACOS_PASSWORD:-nacos}

DATA_ID="ydsz-common.yaml"
CONFIG_FILE="$ROOT_DIR/deploy/common/nacos/$DATA_ID"

if [[ ! -f "$CONFIG_FILE" ]]; then
  echo "[ERROR] 配置模板不存在: $CONFIG_FILE" >&2
  exit 1
fi

# 等待 Nacos 就绪
echo "[INFO] 等待 Nacos $NACOS_ADDR 就绪..."
for i in {1..30}; do
  if curl -sf "http://$NACOS_ADDR/nacos/actuator/health" >/dev/null 2>&1; then
    echo "[OK] Nacos 已就绪"
    break
  fi
  sleep 2
  if [[ $i -eq 30 ]]; then
    echo "[ERROR] Nacos 不可达: $NACOS_ADDR" >&2
    exit 1
  fi
done

# 导入配置（使用 Nacos OpenAPI /v1/cs/configs）
echo "[INFO] 导入配置: $DATA_ID (namespace=$NAMESPACE, group=$GROUP)"

# 注意: Nacos 2.3 鉴权方式: accessToken 由 /v1/auth/login 获取
ACCESS_TOKEN=$(curl -s -X POST "http://$NACOS_ADDR/nacos/v1/auth/login" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=$USERNAME&password=$PASSWORD" | grep -oP '"accessToken":"\K[^"]+' || echo "")

if [[ -n "$ACCESS_TOKEN" ]]; then
  AUTH_HEADER="accessToken=$ACCESS_TOKEN"
  echo "[OK] Nacos 鉴权成功"
else
  # standalone 模式默认关闭鉴权,直接走无 token 流程
  AUTH_HEADER=""
  echo "[WARN] Nacos 鉴权失败,尝试无 token 方式（standalone 模式）"
fi

# 发布配置
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
  "http://$NACOS_ADDR/v1/cs/configs" \
  ${AUTH_HEADER:+-d "$AUTH_HEADER"} \
  -d "dataId=$DATA_ID&group=$GROUP&namespaceId=$NAMESPACE&content=$(cat "$CONFIG_FILE" | python3 -c "import sys, urllib.parse; print(urllib.parse.quote(sys.stdin.read()))")&type=yaml&desc=PMIS+shared+config+(auto+imported)")

if [[ "$HTTP_CODE" == "200" ]]; then
  echo "[OK] 导入成功: $DATA_ID"
else
  echo "[ERROR] 导入失败,HTTP $HTTP_CODE" >&2
  echo "[HINT] 可手动导入: Nacos 控制台 → 配置管理 → 命名空间 '$NAMESPACE' → 新建配置" >&2
  echo "       DataId=$DATA_ID Group=$GROUP 类型=YAML" >&2
  exit 1
fi

echo
echo "============================================================"
echo "  共享配置导入完成"
echo
echo "  后续: 7 个后端服务启动时会自动从 Nacos 拉取此配置"
echo "  验证: 访问 http://$NACOS_ADDR/nacos (nacos/nacos)"
echo "        → 配置管理 → 命名空间 pmis → 应能看到 $DATA_ID"
echo "============================================================"
