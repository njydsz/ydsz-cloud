#!/usr/bin/env bash
# =============================================================================
#  YDSZ · 环境检查脚本 (Linux / macOS)
# -----------------------------------------------------------------------------
#  检查项：
#    1. 操作系统与 shell
#    2. JDK 21+
#    3. Maven 3.9+
#    4. Node.js 20+ 与 pnpm 9+
#    5. Docker 24+ 与 docker compose v2
#    6. 端口占用检查（9000-9007 / 9010-9011 / 8800 / 5432 / 6379 / 8848 / 9100-9101）
#    7. 内存检查（建议 ≥ 8GB 可用）
#    8. 项目文件结构
# =============================================================================
set -e
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
log()  { echo -e "${BLUE}[CHECK]${NC} $*"; }
ok()   { echo -e "  ${GREEN}✓${NC} $*"; }
warn() { echo -e "  ${YELLOW}⚠${NC} $*"; }
fail() { echo -e "  ${RED}✗${NC} $*"; }

PASS=0; FAIL=0
check_ok()  { ok "$1"; PASS=$((PASS+1)); }
check_fail(){ fail "$1"; FAIL=$((FAIL+1)); }

echo "============================================================"
echo "  YDSZ · 环境检查"
echo "============================================================"

# 1. 操作系统
log "[1/8] 操作系统"
OS=$(uname -s)
case "$OS" in
  Linux)  check_ok "Linux";;
  Darwin) check_ok "macOS";;
  *)      check_fail "不支持的操作系统: $OS";;
esac

# 2. JDK
log "[2/8] JDK"
if command -v java >/dev/null 2>&1; then
  JAVA_VER=$(java -version 2>&1 | head -n1 | awk -F '"' '{print $2}')
  JAVA_MAJOR=$(echo "$JAVA_VER" | awk -F. '{ if ($1==1) print $2; else print $1 }')
  if [[ "$JAVA_MAJOR" -ge 21 ]]; then
    check_ok "JDK $JAVA_VER"
  else
    check_fail "需要 JDK 21+，当前: $JAVA_VER"
  fi
else
  check_fail "未安装 Java"
fi

# 3. Maven
log "[3/8] Maven"
if command -v mvn >/dev/null 2>&1; then
  MVN_VER=$(mvn --version 2>&1 | head -n1 | awk '{print $3}')
  MVN_MAJOR=$(echo "$MVN_VER" | awk -F. '{print $1}')
  if [[ "$MVN_MAJOR" -ge 3 ]]; then
    check_ok "Maven $MVN_VER"
  else
    check_fail "需要 Maven 3.9+，当前: $MVN_VER"
  fi
else
  check_fail "未安装 Maven"
fi

# 4. Node + pnpm
log "[4/8] Node.js & pnpm"
if command -v node >/dev/null 2>&1; then
  NODE_VER=$(node --version | tr -d 'v')
  NODE_MAJOR=$(echo "$NODE_VER" | awk -F. '{print $1}')
  if [[ "$NODE_MAJOR" -ge 20 ]]; then
    check_ok "Node.js $NODE_VER"
  else
    check_fail "需要 Node.js 20+，当前: $NODE_VER"
  fi
else
  check_fail "未安装 Node.js"
fi

if command -v pnpm >/dev/null 2>&1; then
  PNPM_VER=$(pnpm --version)
  check_ok "pnpm $PNPM_VER"
else
  warn "未安装 pnpm，将无法启动前端（运行 npm i -g pnpm 安装）"
fi

# 5. Docker
log "[5/8] Docker"
if command -v docker >/dev/null 2>&1; then
  DOCKER_VER=$(docker --version | awk '{print $3}' | tr -d ',')
  check_ok "Docker $DOCKER_VER"
  if docker compose version >/dev/null 2>&1; then
    check_ok "docker compose v2"
  else
    check_fail "需要 docker compose v2（升级 Docker）"
  fi
else
  check_fail "未安装 Docker"
fi

# 6. 端口
log "[6/8] 端口检查"
REQUIRED_PORTS=(5432 6379 8848 9848 9100 9101 9000 9001 9002 9003 9004 9005 9006 9007 9010 9011 8800 5173)
for port in "${REQUIRED_PORTS[@]}"; do
  if (echo > /dev/tcp/127.0.0.1/$port) 2>/dev/null; then
    warn "端口 $port 已被占用（运行中的服务？可忽略若端口被其他项目使用）"
  fi
done
ok "端口检查完成（如有警告请确认）"

# 7. 内存
log "[7/8] 内存"
if [[ "$OS" == "Linux" ]]; then
  TOTAL_MEM=$(grep MemTotal /proc/meminfo | awk '{print $2}')
  TOTAL_GB=$((TOTAL_MEM / 1024 / 1024))
  if [[ $TOTAL_GB -ge 8 ]]; then
    check_ok "${TOTAL_GB}GB 可用"
  else
    warn "可用内存 ${TOTAL_GB}GB，建议 ≥ 8GB"
  fi
elif [[ "$OS" == "Darwin" ]]; then
  TOTAL_BYTES=$(sysctl -n hw.memsize)
  TOTAL_GB=$((TOTAL_BYTES / 1024 / 1024 / 1024))
  if [[ $TOTAL_GB -ge 8 ]]; then
    check_ok "${TOTAL_GB}GB 可用"
  else
    warn "可用内存 ${TOTAL_GB}GB，建议 ≥ 8GB"
  fi
fi

# 8. 项目结构
log "[8/8] 项目结构"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../../.." && pwd)"
for f in \
  "$ROOT_DIR/ydsz-backend/pom.xml" \
  "$ROOT_DIR/ydsz-frontend/package.json" \
  "$ROOT_DIR/deploy/sql/V1.0.0.sql" \
  "$ROOT_DIR/deploy/docker/docker-compose.dev.yml"; do
  if [[ -f "$f" ]]; then
    check_ok "$(basename $f)"
  else
    check_fail "缺失: ${f#$ROOT_DIR/}"
  fi
done

echo
echo "============================================================"
echo "  结果：${GREEN}通过 $PASS${NC}  ${RED}失败 $FAIL${NC}"
echo "============================================================"
exit $FAIL
