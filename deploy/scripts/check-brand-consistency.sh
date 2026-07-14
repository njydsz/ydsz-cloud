#!/usr/bin/env bash
# =============================================================================
# check-brand-consistency.sh
# -----------------------------------------------------------------------------
# ydsz-pmis-common-json 模块品牌一致性门禁
#
# 检测目标：阻止「Ydsz 品牌标识」在公开 API / 资源 / 文档中再次回潮。
#
# 检测范围（命中即 fail PR）：
#   1. Java 源文件 public class/interface/enum/@interface/record 声明含 Ydsz 前缀
#   2. Java 源文件名 Ydsz*.java 残留
#   3. META-INF/spring/.../AutoConfiguration.imports 引用已废弃的 Ydsz*AutoConfiguration
#   4. META-INF/native-image/.../native-image.json 反射类名包含 Ydsz 前缀
#   5. README.md / pom.xml description 出现 YdszJson 字样
#   6. Javadoc @author ydsz-pmis-team 残留
#
# 适用场景：
#   - 本地开发：mvn verify 前手动跑
#   - CI 流水线：deploy/scripts/check-quality-gate.sh 中调用
#   - pre-commit hook：scripts/hooks/pre-commit
#
# 退出码：0=通过 / 1=发现残留 / 2=环境异常
# =============================================================================

set -euo pipefail

# ---------- 路径与配置 ----------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)/ydsz-pmis-backend"
MODULE_DIR="${BACKEND_DIR}/ydsz-pmis-common/ydsz-pmis-common-json"

if [[ ! -d "${MODULE_DIR}" ]]; then
  echo "[FAIL] 模块目录不存在：${MODULE_DIR}" >&2
  exit 2
fi

cd "${MODULE_DIR}"

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
  check_count=$((check_count + 1))

  echo -n "[Check ${check_count}] ${title} ... "

  local matches
  matches=$(grep -rn --include="${file_glob}" -E "${pattern}" "${MODULE_DIR}" 2>/dev/null \
    | grep -v "target/" \
    | grep -v "\.git/" \
    || true)

  if [[ -n "${matches}" ]]; then
    echo -e "${RED}FAIL${NC}"
    echo "  命中："
    echo "${matches}" | sed 's/^/    /'
    violations=$((violations + 1))
  else
    echo -e "${GREEN}PASS${NC}"
  fi
}

# ---------- 6 项检查 ----------
echo "=========================================="
echo " ydsz-pmis-common-json 品牌一致性门禁"
echo " 模块：${MODULE_DIR}"
echo "=========================================="

check "1. Java 公共类声明含 Ydsz 前缀" \
      '\b(public|protected)\s+(class|interface|enum|@interface|record|final\s+class|abstract\s+class)\s+Ydsz[A-Z]\w*' \
      "*.java"

check "2. Ydsz*.java 文件名残留" \
      '^Ydsz[A-Z]\w*\.java$' \
      "*.java"

check "3. AutoConfiguration.imports 引用废弃类名" \
      'Ydsz[A-Z]\w*AutoConfiguration' \
      "*.imports"

check "4. native-image.json 反射类名含 Ydsz" \
      'com\.njydsz\.pmis\.common\.json\.[^"]*Ydsz' \
      "*.json"

check "5. README.md / pom.xml 出现 YdszJson" \
      'YdszJson|YdszSerializer|YdszDeserializer' \
      "*.md"

check "6. Javadoc @author ydsz-pmis-team 残留" \
      '@author\s+ydsz-pmis-team' \
      "*.java"

# ---------- 汇总 ----------
echo "=========================================="
if [[ ${violations} -gt 0 ]]; then
  echo -e "${RED}共 ${violations} 项品牌残留违规（检查项 ${check_count} 中）${NC}"
  echo ""
  echo "修复指引："
  echo "  1. 公开类名:  YdszXxx → Xxx（同步修改文件名）"
  echo "  2. AutoConfiguration.imports: 引用最新类名"
  echo "  3. native-image.json: 反射类名同步更新"
  echo "  4. README / pom: 文档描述更新为去 Ydsz 化后类名"
  echo "  5. Javadoc @author: 删除 ydsz-pmis-team 标签"
  echo ""
  echo "或运行一键修复脚本：python scripts/rename-ydsz-engines.py"
  exit 1
else
  echo -e "${GREEN}全部 ${check_count} 项检查通过 ✓${NC}"
  exit 0
fi
