#!/usr/bin/env bash
# =============================================================================
# check-brand-consistency.sh
# -----------------------------------------------------------------------------
# ydsz 全仓库品牌一致性门禁
#
# 检测目标：阻止「pmis 品牌标识」在代码 / 资源 / 配置 / SQL 中再次回潮。
#
# 品牌定位（2026-07-16 决策）：
#   项目品牌标识是 ydsz（不是 pmis）。pmis 是遗留产品代号，须从全仓库移除。
#   2026-07-15 的「去 Ydsz 化」方向已反转，json/cache 模块回退为 Ydsz 前缀。
#
# 检测范围（命中即 fail PR）：
#   1. Java 源文件 com.njydsz.pmis 包路径残留
#   2. pom.xml 中 ydsz-pmis-* artifactId 残留
#   3. SQL 文件中 pmis_ 表前缀残留
#   4. Java 类名 Pmis* 残留（文件名或类声明）
#   5. 配置文件中 pmis. 配置键残留（yml/yaml/properties）
#   6. 分布式锁 key / 权限码 pmis:* 残留
#   7. 目录名/文件名含 pmis 残留
#
# 适用场景：
#   - 本地开发：mvn verify 前手动跑
#   - CI 流水线：deploy/scripts/check-quality-gate.sh 中调用
#   - pre-commit hook：scripts/hooks/pre-commit
#
# 退出码：0=通过 / 1=发现 pmis 残留 / 2=环境异常
# =============================================================================

set -euo pipefail

# ---------- 路径与配置 ----------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# 检测范围：后端 + 前端 + 部署 + 脚本（排除构建产物和第三方依赖）
SCAN_DIRS=(
  "${REPO_ROOT}/ydsz-backend"
  "${REPO_ROOT}/ydsz-frontend/src"
  "${REPO_ROOT}/deploy"
  "${REPO_ROOT}/scripts"
)

# ---------- 颜色输出 ----------
RED='\033[0;31m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
NC='\033[0m'

violations=0
check_count=0

# ---------- 检查函数 ----------
check() {
  local title="$1"
  local pattern="$2"
  local file_glob="$3"
  shift 3
  local scan_dirs=("$@")

  check_count=$((check_count + 1))
  echo -n "[Check ${check_count}] ${title} ... "

  local matches=""
  for dir in "${scan_dirs[@]}"; do
    if [[ ! -d "${dir}" ]]; then
      continue
    fi
    local part_matches
    part_matches=$(grep -rn --include="${file_glob}" -E "${pattern}" "${dir}" 2>/dev/null \
      | grep -v "target/" \
      | grep -v "node_modules/" \
      | grep -v "\.git/" \
      | grep -v "debrand-pmis" \
      || true)
    if [[ -n "${part_matches}" ]]; then
      matches="${matches}${part_matches}"$'\n'
    fi
  done

  if [[ -n "${matches}" ]]; then
    echo -e "${RED}FAIL${NC}"
    echo "  命中："
    echo "${matches}" | sed 's/^/    /' | head -20
    if [[ $(echo "${matches}" | wc -l) -gt 20 ]]; then
      echo "    ...（更多命中已省略）"
    fi
    violations=$((violations + 1))
  else
    echo -e "${GREEN}PASS${NC}"
  fi
}

# ---------- 检查文件名残留 ----------
check_filenames() {
  local title="$1"
  local name_pattern="$2"
  shift 2
  local scan_dirs=("$@")

  check_count=$((check_count + 1))
  echo -n "[Check ${check_count}] ${title} ... "

  local matches=""
  for dir in "${scan_dirs[@]}"; do
    if [[ ! -d "${dir}" ]]; then
      continue
    fi
    local part_matches
    part_matches=$(find "${dir}" -name "${name_pattern}" -not -path "*/target/*" -not -path "*/node_modules/*" -not -path "*/.git/*" -not -name "debrand-pmis*" 2>/dev/null || true)
    if [[ -n "${part_matches}" ]]; then
      matches="${matches}${part_matches}"$'\n'
    fi
  done

  if [[ -n "${matches}" ]]; then
    echo -e "${RED}FAIL${NC}"
    echo "  命中："
    echo "${matches}" | sed 's/^/    /' | head -20
    violations=$((violations + 1))
  else
    echo -e "${GREEN}PASS${NC}"
  fi
}

# ---------- 颜色输出 ----------
echo "=========================================="
echo " ydsz 全仓库品牌一致性门禁"
echo " 检测目标：pmis 残留"
echo "=========================================="

# ---------- 逐项检查 ----------

# 1. Java 源文件 com.njydsz.pmis 包路径残留
check "1. Java com.njydsz.pmis 包路径残留" \
      'com\.njydsz\.pmis' \
      "*.java" \
      "${SCAN_DIRS[@]}"

# 2. pom.xml 中 ydsz-pmis-* artifactId 残留
check "2. pom.xml ydsz-pmis-* artifactId 残留" \
      'ydsz-pmis' \
      "pom.xml" \
      "${SCAN_DIRS[@]}"

# 3. SQL 文件中 pmis_ 表前缀残留
check "3. SQL pmis_ 表前缀残留" \
      '\bpmis_' \
      "*.sql" \
      "${REPO_ROOT}/deploy/sql"

# 4. Java 类名 Pmis* 残留（类声明）
check "4. Java Pmis* 类名声明残留" \
      '\b(class|interface|enum|@interface|record)\s+Pmis[A-Z]' \
      "*.java" \
      "${SCAN_DIRS[@]}"

# 5. 配置文件中 pmis. 配置键残留
check "5. 配置文件 pmis. 配置键残留" \
      '\bpmis\.' \
      "*.yml" \
      "${SCAN_DIRS[@]}"
check "5b. 配置文件 pmis. 配置键残留 (yaml)" \
      '\bpmis\.' \
      "*.yaml" \
      "${SCAN_DIRS[@]}"
check "5c. 配置文件 pmis. 配置键残留 (properties)" \
      '\bpmis\.' \
      "*.properties" \
      "${SCAN_DIRS[@]}"

# 6. 分布式锁 key / 权限码 pmis:* 残留
check "6. 分布式锁 key / 权限码 pmis:* 残留" \
      'pmis:' \
      "*.java" \
      "${SCAN_DIRS[@]}"
check "6b. 分布式锁 key / 权限码 pmis:* 残留 (yml)" \
      'pmis:' \
      "*.yml" \
      "${SCAN_DIRS[@]}"

# 7. 文件名含 pmis 残留
check_filenames "7. 文件名含 pmis 残留" \
      "*pmis*" \
      "${SCAN_DIRS[@]}"

# 8. 目录名含 pmis 残留
check_count=$((check_count + 1))
echo -n "[Check ${check_count}] 目录名含 pmis 残留 ... "
dir_matches=""
for dir in "${SCAN_DIRS[@]}"; do
  if [[ ! -d "${dir}" ]]; then
    continue
  fi
  part_dir_matches=$(find "${dir}" -type d -name "*pmis*" -not -path "*/target/*" -not -path "*/node_modules/*" -not -path "*/.git/*" 2>/dev/null || true)
  if [[ -n "${part_dir_matches}" ]]; then
    dir_matches="${dir_matches}${part_dir_matches}"$'\n'
  fi
done
if [[ -n "${dir_matches}" ]]; then
  echo -e "${RED}FAIL${NC}"
  echo "  命中："
  echo "${dir_matches}" | sed 's/^/    /' | head -20
  violations=$((violations + 1))
else
  echo -e "${GREEN}PASS${NC}"
fi

# ---------- 汇总 ----------
echo ""
echo "=========================================="
if [[ ${violations} -gt 0 ]]; then
  echo -e "${RED}共 ${violations} 项 pmis 品牌残留违规（检查项 ${check_count} 中）${NC}"
  echo ""
  echo "修复指引："
  echo "  1. 包路径:  com.njydsz.pmis.* → com.njydsz.*（删去 .pmis 段）"
  echo "  2. artifactId: ydsz-pmis-* → ydsz-*"
  echo "  3. SQL 表前缀: pmis_* → ydsz_*"
  echo "  4. Java 类名: PmisXxx → YdszXxx"
  echo "  5. 配置键: pmis.* → ydsz.*"
  echo "  6. 锁 key: pmis:* → ydsz:*"
  echo "  7. 文件/目录名: 含 pmis → 改为 ydsz 或删除 pmis 段"
  echo ""
  echo "或运行批量替换脚本：python scripts/debrand-pmis-fullrepo.py"
  exit 1
else
  echo -e "${GREEN}全部 ${check_count} 项检查通过 ✓${NC}"
  exit 0
fi
