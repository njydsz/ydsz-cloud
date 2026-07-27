#!/usr/bin/env bash
# =============================================================================
# check-quality-gate.sh — ydsz 项目统一质量门禁（CI 阻断性）
#
# 本脚本是 ydsz 项目 CI 流水线的「最终门禁」，聚合所有强制规则的检测：
#   1. 行内 FQN + @SuppressWarnings 违规（严格模式）
#   2. pmis 品牌残留（全仓库）
#   3. 项目版本号统一为 1.0.0
#   4. CI 命令禁用 -DskipJacocoCheck=false（覆盖率门禁已全局关闭）
#   5. ydsz-workflow 模块禁止移动端适配代码（Mobile/H5 Controller / VO / 路径）
#   6. 禁止引入 Flyway / Liquibase 自动 schema-migration 框架
#   7. 源代码文件 BOM 编码污染检测
#   8. ydsz-workflow 模块禁止电子签章集成
#
# 规则文件来源（.trae/rules/*.md + project_memory.md）：
#   - no-inline-fqn.md         （FQN + @SuppressWarnings）
#   - version-policy.md        （版本号 1.0.0）
#   - ignore-unit-test-coverage.md（JaCoCo check 永不阻断）
#   - workflow-pc-only.md      （工作流 PC-only + 禁电子签章）
#   - project_memory.md → Hard Constraints（Flyway/Liquibase 禁用）
#
# 用法：
#   ./deploy/scripts/check-quality-gate.sh                # 默认模式（仅检测）
#   ./deploy/scripts/check-quality-gate.sh --strict       # 严格模式（有违规即 exit 1）
#   ./deploy/scripts/check-quality-gate.sh --skip <name>  # 跳过指定检测项
#       可用的 <name>：fqn, brand, version, jacoco, workflow-mobile, flyway, bom, esign
#
# 退出码：
#   0 — 所有检测通过（或非严格模式）
#   1 — 至少一项检测失败（严格模式）
#   2 — 环境异常（脚本执行错误）
#
# @author ydsz-team
# @since 1.0.0
# =============================================================================
set -uo pipefail

# ---------- 路径与配置 ----------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
BACKEND_ROOT="${REPO_ROOT}/ydsz-backend"
FRONTEND_ROOT="${REPO_ROOT}/ydsz-frontend"
SQL_ROOT="${REPO_ROOT}/deploy/sql"

# ---------- 颜色输出 ----------
RED='\033[0;31m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

# ---------- 参数解析 ----------
STRICT=false
SKIP_LIST=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --strict)
      STRICT=true
      shift
      ;;
    --skip)
      if [[ -z "${2:-}" ]]; then
        echo -e "${RED}--skip 参数后必须跟检测项名称${NC}" >&2
        exit 2
      fi
      SKIP_LIST+=("$2")
      shift 2
      ;;
    -h|--help)
      sed -n '1,40p' "$0"
      exit 0
      ;;
    *)
      echo -e "${RED}未知参数: $1${NC}" >&2
      exit 2
      ;;
  esac
done

# 工具函数：判断是否跳过某项
is_skipped() {
  local name="$1"
  for s in "${SKIP_LIST[@]:-}"; do
    [[ "$s" == "$name" ]] && return 0
  done
  return 1
}

# 全局违规计数
TOTAL_VIOLATIONS=0
TOTAL_CHECKS=0
PASSED_CHECKS=0

# 单项检测包装函数
# 用法：run_check <check_name> <check_function>
run_check() {
  local name="$1"
  local func="$2"
  TOTAL_CHECKS=$((TOTAL_CHECKS + 1))
  if is_skipped "$name"; then
    echo -e "${YELLOW}[SKIP]${NC} ${name}（用户指定跳过）"
    return 0
  fi
  if $func; then
    PASSED_CHECKS=$((PASSED_CHECKS + 1))
    return 0
  else
    TOTAL_VIOLATIONS=$((TOTAL_VIOLATIONS + 1))
    return 1
  fi
}

# ---------- Check 1: 行内 FQN + @SuppressWarnings ----------
check_fqn() {
  echo -e "${BLUE}[Check]${NC} 行内 FQN + @SuppressWarnings 违规检测..."
  if [[ ! -x "${SCRIPT_DIR}/check-inline-fqn.sh" ]]; then
    chmod +x "${SCRIPT_DIR}/check-inline-fqn.sh" 2>/dev/null || true
  fi
  # 严格模式下，子脚本 exit 1 即视为本项失败
  # 非严格模式下，子脚本 exit 0 即通过，exit 1 仅警告
  local sub_output sub_exit
  if [[ "$STRICT" == "true" ]]; then
    sub_output=$(bash "${SCRIPT_DIR}/check-inline-fqn.sh" "${BACKEND_ROOT}" --strict 2>&1)
    sub_exit=$?
    if [[ $sub_exit -eq 0 ]]; then
      echo -e "${GREEN}  ✓ PASS${NC}"
      return 0
    else
      echo "$sub_output" | sed 's/^/    /' | tail -30
      echo -e "${RED}  ✗ FAIL${NC}（子脚本退出码 $sub_exit）"
      return 1
    fi
  else
    sub_output=$(bash "${SCRIPT_DIR}/check-inline-fqn.sh" "${BACKEND_ROOT}" 2>&1)
    sub_exit=$?
    if [[ $sub_exit -eq 0 ]]; then
      echo -e "${GREEN}  ✓ PASS${NC}"
      return 0
    else
      echo -e "${YELLOW}  ⚠ WARN${NC}（非严格模式，不阻断，退出码 $sub_exit）"
      return 0
    fi
  fi
}

# ---------- Check 2: pmis 品牌残留 ----------
check_brand() {
  echo -e "${BLUE}[Check]${NC} pmis 品牌残留检测..."
  if [[ ! -x "${SCRIPT_DIR}/check-brand-consistency.sh" ]]; then
    chmod +x "${SCRIPT_DIR}/check-brand-consistency.sh" 2>/dev/null || true
  fi
  if bash "${SCRIPT_DIR}/check-brand-consistency.sh" > /tmp/ydsz-brand-$$.log 2>&1; then
    echo -e "${GREEN}  ✓ PASS${NC}"
    return 0
  else
    tail -20 /tmp/ydsz-brand-$$.log | sed 's/^/    /'
    rm -f /tmp/ydsz-brand-$$.log
    echo -e "${RED}  ✗ FAIL${NC}"
    return 1
  fi
}

# ---------- Check 3: 项目版本号统一为 1.0.0 ----------
check_version() {
  echo -e "${BLUE}[Check]${NC} 项目版本号统一为 1.0.0..."
  local violations=0

  # 3a. Maven 后端 pom.xml <revision> 必须为 1.0.0-SNAPSHOT
  local revision
  revision=$(grep '<revision>' "${BACKEND_ROOT}/pom.xml" 2>/dev/null \
             | head -1 \
             | sed -E 's/.*<revision>([^<]+)<\/revision>.*/\1/' \
             | tr -d '[:space:]' || echo "")
  if [[ "$revision" != "1.0.0-SNAPSHOT" ]]; then
    echo "    ❌ ydsz-backend/pom.xml <revision> = '$revision'（应为 1.0.0-SNAPSHOT）"
    violations=$((violations + 1))
  fi

  # 3b. 前端 package.json version 必须为 1.0.0
  if [[ -f "${FRONTEND_ROOT}/package.json" ]]; then
    local fe_version
    fe_version=$(grep '"version"' "${FRONTEND_ROOT}/package.json" 2>/dev/null \
                 | head -1 \
                 | sed -E 's/.*"version"\s*:\s*"([^"]+)".*/\1/' \
                 | tr -d '[:space:]' || echo "")
    if [[ "$fe_version" != "1.0.0" ]]; then
      echo "    ❌ ydsz-frontend/package.json version = '$fe_version'（应为 1.0.0）"
      violations=$((violations + 1))
    fi
  fi

  # 3c. Helm Chart.yaml version / appVersion 必须为 1.0.0
  local helm_chart="${REPO_ROOT}/deploy/helm/ydsz/Chart.yaml"
  if [[ -f "$helm_chart" ]]; then
    local chart_ver app_ver
    chart_ver=$(grep -E '^version:' "$helm_chart" | awk '{print $2}' | tr -d '"' | tr -d '[:space:]' || echo "")
    app_ver=$(grep -E '^appVersion:' "$helm_chart" | awk '{print $2}' | tr -d '"' | tr -d '[:space:]' || echo "")
    if [[ "$chart_ver" != "1.0.0" ]]; then
      echo "    ❌ deploy/helm/ydsz/Chart.yaml version = '$chart_ver'（应为 1.0.0）"
      violations=$((violations + 1))
    fi
    if [[ "$app_ver" != "1.0.0" ]]; then
      echo "    ❌ deploy/helm/ydsz/Chart.yaml appVersion = '$app_ver'（应为 1.0.0）"
      violations=$((violations + 1))
    fi
  fi

  # 3d. Java 源文件 @since 不能用 1.3.0 / 2.0.0 / 3.5.0 等非 1.0.0 版本
  # 例外：第三方依赖版本、协议规范版本不在检测范围
  local bad_since
  bad_since=$(grep -rnE '@since\s+(1\.[1-9]|2\.|3\.|4\.|5\.)' "${BACKEND_ROOT}" \
              --include="*.java" 2>/dev/null \
              | grep -v '/target/' \
              | grep -v 'test/' \
              | head -10 || true)
  if [[ -n "$bad_since" ]]; then
    echo "    ❌ Java @since 出现非 1.0.0 项目版本号（仅显示前 10 条）："
    echo "$bad_since" | sed 's/^/      /'
    violations=$((violations + 1))
  fi

  # 3e. @Deprecated(since = "x.y.z") 同理
  local bad_deprecated
  bad_deprecated=$(grep -rnE '@Deprecated\(since\s*=\s*"(1\.[1-9]|2\.|3\.|4\.|5\.)' "${BACKEND_ROOT}" \
                   --include="*.java" 2>/dev/null \
                   | grep -v '/target/' \
                   | head -10 || true)
  if [[ -n "$bad_deprecated" ]]; then
    echo "    ❌ @Deprecated(since=...) 出现非 1.0.0 项目版本号："
    echo "$bad_deprecated" | sed 's/^/      /'
    violations=$((violations + 1))
  fi

  if [[ $violations -eq 0 ]]; then
    echo -e "${GREEN}  ✓ PASS${NC}"
    return 0
  else
    echo -e "${RED}  ✗ FAIL${NC}（$violations 处版本号违规）"
    return 1
  fi
}

# ---------- Check 4: CI 命令禁用 -DskipJacocoCheck=false ----------
check_jacoco() {
  echo -e "${BLUE}[Check]${NC} CI 命令禁用 -DskipJacocoCheck=false..."
  local matches
  matches=$(grep -rnE 'skipJacocoCheck=false' "${REPO_ROOT}" \
            --include="*.sh" --include="*.yaml" --include="*.yml" --include="*.Jenkinsfile" --include="*.groovy" --include="Makefile" \
            2>/dev/null \
            | grep -v '/target/' \
            | grep -v 'node_modules/' \
            | grep -v 'check-quality-gate.sh' \
            | head -10 || true)
  if [[ -z "$matches" ]]; then
    echo -e "${GREEN}  ✓ PASS${NC}"
    return 0
  else
    echo "    ❌ 发现 -DskipJacocoCheck=false 用法（覆盖率门禁已全局关闭，禁止重新启用）："
    echo "$matches" | sed 's/^/      /'
    echo -e "${RED}  ✗ FAIL${NC}"
    return 1
  fi
}

# ---------- Check 5: ydsz-workflow 禁止移动端适配代码 ----------
check_workflow_mobile() {
  echo -e "${BLUE}[Check]${NC} ydsz-workflow 禁止移动端适配代码..."
  local wf_dir="${BACKEND_ROOT}/ydsz-workflow"
  if [[ ! -d "$wf_dir" ]]; then
    echo -e "${YELLOW}  ⚠ SKIP${NC}（ydsz-workflow 目录不存在）"
    return 0
  fi

  local violations=0

  # 5a. 移动端专属 Controller
  local mobile_ctrl
  mobile_ctrl=$(grep -rlE '(Mobile|H5|Applet|MApp|MiniApp).*Controller|Controller.*(Mobile|H5|Applet)' \
                 "$wf_dir" --include="*.java" 2>/dev/null | grep -v '/target/' | head -5 || true)
  if [[ -n "$mobile_ctrl" ]]; then
    echo "    ❌ 发现移动端 Controller："
    echo "$mobile_ctrl" | sed 's/^/      /'
    violations=$((violations + 1))
  fi

  # 5b. 移动端专属路径 /mobile/ /h5/ /app/
  local mobile_path
  mobile_path=$(grep -rnE '"/(mobile|h5|app|applet)/' "$wf_dir" --include="*.java" 2>/dev/null \
                 | grep -v '/target/' | head -5 || true)
  if [[ -n "$mobile_path" ]]; then
    echo "    ❌ 发现移动端路径映射："
    echo "$mobile_path" | sed 's/^/      /'
    violations=$((violations + 1))
  fi

  # 5c. 移动端专属 VO/DTO
  local mobile_vo
  mobile_vo=$(grep -rlE '\b(Mobile|H5|Applet)[A-Z][a-zA-Z]*VO\b|\b(Mobile|H5|Applet)[A-Z][a-zA-Z]*DTO\b' \
              "$wf_dir" --include="*.java" 2>/dev/null | grep -v '/target/' | head -5 || true)
  if [[ -n "$mobile_vo" ]]; then
    echo "    ❌ 发现移动端 VO/DTO："
    echo "$mobile_vo" | sed 's/^/      /'
    violations=$((violations + 1))
  fi

  # 5d. 移动端 SDK 依赖
  local mobile_dep
  mobile_dep=$(grep -rnE 'weixin-java-miniapp|dingtalk-app-sdk' "$wf_dir" --include="pom.xml" 2>/dev/null \
               | head -5 || true)
  if [[ -n "$mobile_dep" ]]; then
    echo "    ❌ 发现移动端 SDK 依赖："
    echo "$mobile_dep" | sed 's/^/      /'
    violations=$((violations + 1))
  fi

  if [[ $violations -eq 0 ]]; then
    echo -e "${GREEN}  ✓ PASS${NC}"
    return 0
  else
    echo -e "${RED}  ✗ FAIL${NC}（$violations 处移动端适配违规）"
    return 1
  fi
}

# ---------- Check 6: 禁止引入 Flyway / Liquibase ----------
check_flyway() {
  echo -e "${BLUE}[Check]${NC} 禁止引入 Flyway / Liquibase 自动 schema-migration 框架..."
  local violations=0

  # 6a. pom.xml 中不能引入 flyway-core / liquibase-core
  local bad_dep
  bad_dep=$(grep -rnE '<artifactId>(flyway-core|liquibase-core)</artifactId>' "${BACKEND_ROOT}" \
            --include="pom.xml" 2>/dev/null | grep -v '/target/' | head -5 || true)
  if [[ -n "$bad_dep" ]]; then
    echo "    ❌ pom.xml 引入了 Flyway / Liquibase 依赖："
    echo "$bad_dep" | sed 's/^/      /'
    violations=$((violations + 1))
  fi

  # 6b. application.yml / properties 中不能有 spring.flyway.* / spring.liquibase.*
  local bad_cfg
  bad_cfg=$(grep -rnE '^\s*(spring\.)?(flyway|liquibase)\.' "${REPO_ROOT}" \
            --include="*.yml" --include="*.yaml" --include="*.properties" 2>/dev/null \
            | grep -v '/target/' | grep -v 'node_modules/' | head -10 || true)
  if [[ -n "$bad_cfg" ]]; then
    echo "    ❌ 配置文件出现 spring.flyway.* / spring.liquibase.* 配置："
    echo "$bad_cfg" | sed 's/^/      /'
    violations=$((violations + 1))
  fi

  # 6c. 不能有 db/migration 资源目录
  local bad_dir
  bad_dir=$(find "${BACKEND_ROOT}" -type d -name "migration" -path "*/db/*" 2>/dev/null | head -5 || true)
  if [[ -n "$bad_dir" ]]; then
    echo "    ❌ 发现 db/migration 目录（Flyway 默认迁移路径）："
    echo "$bad_dir" | sed 's/^/      /'
    violations=$((violations + 1))
  fi

  # 6d. 不能新增增量 SQL 脚本（V1.0.1 / V1.1.0 / patch_*.sql / migration_*.sql）
  local bad_sql
  bad_sql=$(find "${SQL_ROOT}" -type f \( -name "V1.[1-9]*.sql" -o -name "V[2-9].*.sql" -o -name "patch_*.sql" -o -name "migration_*.sql" \) 2>/dev/null | head -5 || true)
  if [[ -n "$bad_sql" ]]; then
    echo "    ❌ 发现增量 SQL 脚本（项目当前阶段所有变更须合并到 V1.0.0.sql）："
    echo "$bad_sql" | sed 's/^/      /'
    violations=$((violations + 1))
  fi

  if [[ $violations -eq 0 ]]; then
    echo -e "${GREEN}  ✓ PASS${NC}"
    return 0
  else
    echo -e "${RED}  ✗ FAIL${NC}（$violations 处 schema-migration 违规）"
    return 1
  fi
}

# ---------- Check 7: BOM 编码污染检测 ----------
check_bom() {
  echo -e "${BLUE}[Check]${NC} 源代码 BOM 编码污染检测..."
  local violations=0
  local bom_files=""

  # 检测 .java / .ts / .vue / .yml / .yaml / .xml / .sql 文件开头的 BOM 字节
  while IFS= read -r -d '' file; do
    # 读取前 3 字节，检测是否为 EF BB BF（UTF-8 BOM）
    local hex
    hex=$(head -c 3 "$file" | od -An -tx1 | tr -d ' \n')
    if [[ "$hex" == "efbbbf" ]]; then
      bom_files="${bom_files}${file}"$'\n'
      violations=$((violations + 1))
    fi
  done < <(find "${BACKEND_ROOT}" "${FRONTEND_ROOT}/src" "${REPO_ROOT}/deploy" \
           -type f \( -name "*.java" -o -name "*.ts" -o -name "*.vue" -o -name "*.yml" -o -name "*.yaml" -o -name "*.xml" -o -name "*.sql" \) \
           -not -path "*/target/*" -not -path "*/node_modules/*" -not -path "*/.git/*" \
           -print0 2>/dev/null)

  if [[ $violations -eq 0 ]]; then
    echo -e "${GREEN}  ✓ PASS${NC}"
    return 0
  else
    echo "    ❌ 发现 $violations 个文件含 BOM 字节前缀（可能导致编译/解析错误）："
    echo "$bom_files" | head -20 | sed 's/^/      /'
    [[ $violations -gt 20 ]] && echo "      ...（更多已省略）"
    echo -e "${RED}  ✗ FAIL${NC}"
    return 1
  fi
}

# ---------- Check 8: ydsz-workflow 禁止电子签章集成 ----------
check_esign() {
  echo -e "${BLUE}[Check]${NC} ydsz-workflow 禁止电子签章集成..."
  local wf_dir="${BACKEND_ROOT}/ydsz-workflow"
  if [[ ! -d "$wf_dir" ]]; then
    echo -e "${YELLOW}  ⚠ SKIP${NC}（ydsz-workflow 目录不存在）"
    return 0
  fi

  local violations=0

  # 8a. 禁止出现 ElectronicSign* / Esign* / SignatureCert* / PdfSeal* 类
  local esign_class
  esign_class=$(grep -rlE '\b(ElectronicSign|Esign|SignatureCert|PdfSeal)[A-Z][a-zA-Z]*' \
                "$wf_dir" --include="*.java" 2>/dev/null | grep -v '/target/' | head -5 || true)
  if [[ -n "$esign_class" ]]; then
    echo "    ❌ 发现电子签章相关类："
    echo "$esign_class" | sed 's/^/      /'
    violations=$((violations + 1))
  fi

  # 8b. 禁止引入电子签章第三方依赖
  local esign_dep
  esign_dep=$(grep -rnE 'esign|docu-sign|adobe-sign|契约锁|法大大|上上签|e签宝' "$wf_dir" --include="pom.xml" 2>/dev/null \
              | head -5 || true)
  if [[ -n "$esign_dep" ]]; then
    echo "    ❌ 发现电子签章第三方依赖："
    echo "$esign_dep" | sed 's/^/      /'
    violations=$((violations + 1))
  fi

  # 8c. 禁止 esign:* / sign:* 权限码
  local esign_perm
  esign_perm=$(grep -rnE '"(esign|sign):' "$wf_dir" --include="*.java" --include="*.yml" --include="*.yaml" 2>/dev/null \
               | grep -v '/target/' | head -5 || true)
  if [[ -n "$esign_perm" ]]; then
    echo "    ❌ 发现 esign:* / sign:* 权限码："
    echo "$esign_perm" | sed 's/^/      /'
    violations=$((violations + 1))
  fi

  # 8d. SQL 不能新增 ydsz_sign_* / ydsz_cert_* 表
  local esign_table
  esign_table=$(grep -rnE 'CREATE\s+TABLE\s+[a-zA-Z_]*\.?(ydsz_sign_|ydsz_cert_)' "${SQL_ROOT}" \
                --include="*.sql" 2>/dev/null | head -5 || true)
  if [[ -n "$esign_table" ]]; then
    echo "    ❌ 发现电子签章相关表 DDL："
    echo "$esign_table" | sed 's/^/      /'
    violations=$((violations + 1))
  fi

  if [[ $violations -eq 0 ]]; then
    echo -e "${GREEN}  ✓ PASS${NC}"
    return 0
  else
    echo -e "${RED}  ✗ FAIL${NC}（$violations 处电子签章违规）"
    return 1
  fi
}

# =============================================================================
# 主流程
# =============================================================================
echo "=========================================="
echo " ydsz 项目统一质量门禁"
echo " 模式: $([[ "$STRICT" == "true" ]] && echo '严格（CI 阻断）' || echo '默认（仅检测）')"
[[ ${#SKIP_LIST[@]} -gt 0 ]] && echo " 跳过: ${SKIP_LIST[*]}"
echo "=========================================="
echo ""

run_check "fqn"               check_fqn
run_check "brand"             check_brand
run_check "version"           check_version
run_check "jacoco"            check_jacoco
run_check "workflow-mobile"   check_workflow_mobile
run_check "flyway"            check_flyway
run_check "bom"               check_bom
run_check "esign"             check_esign

echo ""
echo "=========================================="
echo " 检测汇总：${PASSED_CHECKS}/${TOTAL_CHECKS} 项通过，${TOTAL_VIOLATIONS} 项失败"
if [[ ${TOTAL_VIOLATIONS} -eq 0 ]]; then
  echo -e "${GREEN}✅ 质量门禁通过${NC}"
  exit 0
elif [[ "$STRICT" == "true" ]]; then
  echo -e "${RED}❌ 质量门禁失败${NC}（严格模式，CI 阻断）"
  echo ""
  echo "修复指引："
  echo "  1. FQN / @SuppressWarnings 违规：参见 .trae/rules/no-inline-fqn.md"
  echo "  2. pmis 品牌残留：参见 .trae/rules/version-policy.md，或运行 python scripts/debrand-pmis-fullrepo.py"
  echo "  3. 版本号违规：参见 .trae/rules/version-policy.md，所有版本号必须为 1.0.0"
  echo "  4. JaCoCo 命令违规：参见 .trae/rules/ignore-unit-test-coverage.md，禁止 -DskipJacocoCheck=false"
  echo "  5. 移动端适配违规：参见 .trae/rules/workflow-pc-only.md"
  echo "  6. Flyway/Liquibase 违规：参见 project_memory.md → Hard Constraints"
  echo "  7. BOM 编码污染：使用 Python 脚本 strip-bom.py 批量清理"
  echo "  8. 电子签章违规：参见 project_memory.md → Hard Constraints"
  exit 1
else
  echo -e "${YELLOW}⚠️  质量门禁检测出 ${TOTAL_VIOLATIONS} 项违规${NC}（非严格模式，不阻断）"
  exit 0
fi
