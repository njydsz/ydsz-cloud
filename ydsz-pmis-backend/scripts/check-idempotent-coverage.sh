#!/usr/bin/env bash
# =============================================================================
# check-idempotent-coverage.sh
# -----------------------------------------------------------------------------
# 扫描所有 Controller.java,检查写接口(@PostMapping / @PutMapping /
# @DeleteMapping / @PatchMapping)是否标注了 @Idempotent 注解。
#
# 方法或类上标注 @IdempotentExempt 的写接口视为明确豁免,不计入未覆盖。
# 若存在未覆盖写接口,脚本以退出码 1 阻断 CI。
#
# 用法:
#   ./check-idempotent-coverage.sh [后端根目录路径]
#
# 参数:
#   后端根目录路径  默认: ydsz-pmis-backend
#
# 兼容: Linux (gawk) / macOS (BSD awk) / Git Bash (Windows)
# 依赖: grep / find / awk
# =============================================================================
set -euo pipefail

# 后端根目录,默认 ydsz-pmis-backend
BACKEND_DIR="${1:-ydsz-pmis-backend}"

# 检查目录是否存在
if [ ! -d "$BACKEND_DIR" ]; then
  echo "[check-idempotent] 错误: 后端根目录不存在: $BACKEND_DIR" >&2
  exit 1
fi

# 统计 Controller 文件数量
TOTAL_CONTROLLERS=$(find "$BACKEND_DIR" -type f -name "*Controller.java" | wc -l | tr -d ' ')

if [ "$TOTAL_CONTROLLERS" -eq 0 ]; then
  echo "[check-idempotent] 未找到 Controller.java 文件"
  exit 0
fi

# -----------------------------------------------------------------------------
# 用 awk 解析所有 Controller.java,输出 TSV:
#   模块名 \t 类名 \t 方法名 \t HTTP方法 \t 路径 \t COVERED|UNCOVERED|EXEMPT
#
# 解析逻辑(状态机):
#   1. 类声明前:累积类级注解,提取 @RequestMapping 路径、类级 @Idempotent 与 @IdempotentExempt
#   2. 类声明后:累积方法级注解块,遇到方法签名时检查是否为写接口 + 是否有 @Idempotent / @IdempotentExempt
#   3. 多行注解:通过"续行检测"将多行注解合并到当前注解块
# -----------------------------------------------------------------------------
RESULTS=$(find "$BACKEND_DIR" -type f -name "*Controller.java" -print0 \
  | xargs -0 awk '
    # ----- 去除 Windows 换行符 \r -----
    { sub(/\r$/, "") }

    # ----- 文件开始时重置状态 -----
    FNR == 1 {
      annotation_block = ""
      class_name = ""
      class_path = ""
      class_has_idempotent = 0
      class_has_exempt = 0
      seen_class = 0
      # 从 FILENAME 提取模块名(ydsz-pmis-xxx)
      # FILENAME 形如: ydsz-pmis-backend/ydsz-pmis-xxx/src/main/java/...
      module_name = ""
      n = split(FILENAME, parts, "/")
      for (i = 1; i <= n; i++) {
        if (parts[i] == "src" && i > 1) {
          module_name = parts[i-1]
          break
        }
      }
    }

    # ----- 类声明:提取类名 + 类级注解 -----
    seen_class == 0 && /^[[:space:]]*public[[:space:]]+(abstract[[:space:]]+)?class[[:space:]]+/ {
      # 提取类名
      if (match($0, /class[[:space:]]+[A-Za-z0-9_]+/)) {
        s = substr($0, RSTART, RLENGTH)
        sub(/class[[:space:]]+/, "", s)
        class_name = s
      }
      # 检查类级注解中的 @Idempotent / @IdempotentExempt
      if (annotation_block ~ /@Idempotent([^E]|$)/) {
        class_has_idempotent = 1
      }
      if (annotation_block ~ /@IdempotentExempt/) {
        class_has_exempt = 1
      }
      # 提取类级 @RequestMapping 路径(取第一个引号字符串)
      if (match(annotation_block, /@RequestMapping\([^)]*\)/)) {
        s = substr(annotation_block, RSTART, RLENGTH)
        if (match(s, /"[^"]*"/)) {
          class_path = substr(s, RSTART+1, RLENGTH-2)
        }
      }
      annotation_block = ""
      seen_class = 1
      next
    }

    # ----- 类声明前:累积类级注解 -----
    seen_class == 0 {
      if ($0 ~ /^[[:space:]]*@/) {
        annotation_block = annotation_block $0 "\n"
      } else if (annotation_block != "" && $0 ~ /[),]/ && $0 !~ /^[[:space:]]*(public|private|protected|import|package|@)/) {
        # 多行注解的续行
        annotation_block = annotation_block $0 "\n"
      } else {
        annotation_block = ""
      }
      next
    }

    # ----- 类声明后:方法级注解累积 -----
    # 注解行(以 @ 开头)
    /^[[:space:]]*@/ {
      annotation_block = annotation_block $0 "\n"
      next
    }

    # 多行注解的续行(不含 @,但属于上一个注解的参数延续)
    # 判定:前有注解块 + 当前行含 )/=/,且不像方法签名/语句关键字
    annotation_block != "" \
      && $0 !~ /^[[:space:]]*$/ \
      && $0 !~ /^[[:space:]]*\/[/*]/ \
      && $0 !~ /^[[:space:]]*\*/ \
      && $0 ~ /[),=]/ \
      && $0 !~ /^[[:space:]]*(public|private|protected|return|if|for|while|try|catch|throw|new|final|case|switch|break|continue)[[:space:]({]/ {
      annotation_block = annotation_block $0 "\n"
      next
    }

    # 方法签名行:public/protected/private 返回类型 方法名(参数)
    /^[[:space:]]*(public|protected|private)[[:space:]]+/ && /\(/ {
      if (annotation_block != "") {
        process_method($0)
      }
      annotation_block = ""
      next
    }

    # 其他代码行:遇到 { 或 ; 时重置注解块(进入方法体或字段声明)
    {
      if ($0 ~ /[;{]/) {
        annotation_block = ""
      }
    }

    # ----- 处理方法:检查是否为写接口 + 是否有 @Idempotent / @IdempotentExempt -----
    function process_method(sig,    is_write, http_method, method_name, path, full_path, has_idempotent, has_exempt, s, rest, mapping) {
      is_write = 0
      http_method = ""
      if (annotation_block ~ /@PostMapping/) { is_write = 1; http_method = "POST" }
      else if (annotation_block ~ /@PutMapping/) { is_write = 1; http_method = "PUT" }
      else if (annotation_block ~ /@DeleteMapping/) { is_write = 1; http_method = "DELETE" }
      else if (annotation_block ~ /@PatchMapping/) { is_write = 1; http_method = "PATCH" }

      # 非写接口,跳过
      if (!is_write) return

      # 提取方法名:public ReturnType methodName(
      method_name = ""
      if (match(sig, /(public|protected|private)[[:space:]]+[A-Za-z0-9_<>,[:space:]\[\]\?]+[[:space:]]+/)) {
        rest = substr(sig, RSTART + RLENGTH)
        if (match(rest, /^[A-Za-z_][A-Za-z0-9_]*/)) {
          method_name = substr(rest, RSTART, RLENGTH)
        }
      }
      # 方法名为空时跳过(可能是字段声明或构造方法)
      if (method_name == "") return

      # 提取写接口路径:从 @XxxMapping(...) 中取第一个引号字符串
      path = ""
      if (match(annotation_block, /@(PostMapping|PutMapping|DeleteMapping|PatchMapping)\([^)]*\)/)) {
        mapping = substr(annotation_block, RSTART, RLENGTH)
        if (match(mapping, /"[^"]*"/)) {
          path = substr(mapping, RSTART+1, RLENGTH-2)
        }
      }

      # 拼接类级路径 + 方法级路径
      full_path = class_path path

      # 检查 @Idempotent(方法级 或 类级)
      has_idempotent = (annotation_block ~ /@Idempotent([^E]|$)/) || class_has_idempotent
      # 检查 @IdempotentExempt(方法级 或 类级)
      has_exempt = (annotation_block ~ /@IdempotentExempt/) || class_has_exempt

      # 豁免方法不计入覆盖统计
      if (has_exempt) {
        print module_name "\t" class_name "\t" method_name "\t" http_method "\t" full_path "\tEXEMPT"
        return
      }

      # 输出 TSV: 模块 \t 类名 \t 方法名 \t HTTP方法 \t 路径 \t 状态
      print module_name "\t" class_name "\t" method_name "\t" http_method "\t" full_path "\t" (has_idempotent ? "COVERED" : "UNCOVERED")
    }
  ')


# -----------------------------------------------------------------------------
# 汇总统计(不包含 EXEMPT)
# -----------------------------------------------------------------------------
TOTAL_WRITE=$(printf '%s\n' "$RESULTS" | grep -c $'\tCOVERED$\|\tUNCOVERED$' || true)
COVERED=$(printf '%s\n' "$RESULTS" | grep -c $'\tCOVERED$' || true)
UNCOVERED=$(printf '%s\n' "$RESULTS" | grep -c $'\tUNCOVERED$' || true)
EXEMPT=$(printf '%s\n' "$RESULTS" | grep -c $'\tEXEMPT$' || true)

# 处理空值(grep -c 在无匹配时输出 0 但退出码为 1)
COVERED=${COVERED:-0}
UNCOVERED=${UNCOVERED:-0}
EXEMPT=${EXEMPT:-0}
TOTAL_WRITE=${TOTAL_WRITE:-0}

# 计算覆盖率
if [ "$TOTAL_WRITE" -eq 0 ]; then
  COVERAGE="0.0"
else
  COVERAGE=$(awk "BEGIN { printf \"%.1f\", ($COVERED / $TOTAL_WRITE) * 100 }")
fi

# 打印汇总
echo "[check-idempotent] 扫描 Controller: $TOTAL_CONTROLLERS 个"
echo "[check-idempotent] 写接口方法: $TOTAL_WRITE 个"
echo "[check-idempotent] 已标注 @Idempotent: $COVERED 个"
echo "[check-idempotent] 未标注: $UNCOVERED 个"
echo "[check-idempotent] 豁免: $EXEMPT 个"
echo "[check-idempotent] 覆盖率: $COVERAGE%"

# -----------------------------------------------------------------------------
# 输出未覆盖清单
# -----------------------------------------------------------------------------
if [ "$UNCOVERED" -gt 0 ]; then
  echo ""
  echo "[check-idempotent] ERROR: 以下写接口未标注 @Idempotent,将阻断 CI:"
  printf '%s\n' "$RESULTS" | grep $'\tUNCOVERED$' | sort | while IFS=$'\t' read -r module class method http path; do
    # 路径为空时显示 /
    display_path="$path"
    if [ -z "$display_path" ]; then
      display_path="/"
    fi
    printf "  - %s#%s (%s %s)  [%s]\n" "$class" "$method" "$http" "$display_path" "$module"
  done

  echo ""
  echo "[check-idempotent] 各模块未覆盖写接口数量:"
  printf '%s\n' "$RESULTS" | grep $'\tUNCOVERED$' | awk -F'\t' '{counts[$1]++} END {for (m in counts) printf "  - %-25s %d 个\n", m, counts[m]}' | sort

  # 存在未覆盖写接口,以错误退出码阻断 CI
  exit 1
fi

exit 0
