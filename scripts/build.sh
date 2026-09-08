#!/usr/bin/env bash
# =============================================================
# scripts/build.sh — ydsz-cloud 本地/CI 构建脚本
#
# 用法：
#   ./scripts/build.sh                        # 全量构建（默认）
#   ./scripts/build.sh --module ydsz-system   # 仅构建指定模块（及其上游依赖）
#   ./scripts/build.sh --skip-tests           # 跳过单元测试
#   ./scripts/build.sh --skip-checkstyle      # 跳过 Checkstyle 校验
#   ./scripts/build.sh --module ydsz-gateway --skip-tests
#
# 环境变量：
#   MAVEN_HOME            可选；若未设置则假定 mvn 在 PATH 中
#   MAVEN_OPTS            可选；传递给 JVM 的参数（如 -Xmx4g）
#
# 退出码：
#   0  成功
#   1  参数错误
#   2  Maven 构建失败
# =============================================================

set -Eeuo pipefail

# ---------- 常量 ----------
readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Maven 二进制（环境变量优先，否则回退到 PATH 的 mvn）
readonly MAVEN_BIN="${MAVEN_HOME:-}/mvn"
# 注释：若 MAVEN_HOME 为空，Bash 字符串拼接结果为 /mvn，需要兜底处理
# 下方通过 command -v 安全解析

# ---------- 彩色输出 ----------
if [[ -t 1 ]]; then
    readonly COLOR_INFO="\033[0;36m"     # cyan
    readonly COLOR_SUCCESS="\033[0;32m"  # green
    readonly COLOR_ERROR="\033[0;31m"    # red
    readonly COLOR_WARN="\033[0;33m"     # yellow
    readonly COLOR_RESET="\033[0m"
else
    readonly COLOR_INFO=""
    readonly COLOR_SUCCESS=""
    readonly COLOR_ERROR=""
    readonly COLOR_WARN=""
    readonly COLOR_RESET=""
fi

log_info()    { printf "${COLOR_INFO}[INFO]${COLOR_RESET} %s\n" "$*"; }
log_success() { printf "${COLOR_SUCCESS}[SUCCESS]${COLOR_RESET} %s\n" "$*"; }
log_error()   { printf "${COLOR_ERROR}[ERROR]${COLOR_RESET} %s\n" "$*" >&2; }
log_warn()    { printf "${COLOR_WARN}[WARN]${COLOR_RESET} %s\n" "$*" >&2; }

# ---------- 参数解析 ----------
MODULE=""
SKIP_TESTS=false
SKIP_CHECKSTYLE=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --module)
            if [[ -z "${2:-}" ]]; then
                log_error "参数 --module 需要一个值（模块名称，如 ydsz-system）"
                exit 1
            fi
            MODULE="$2"
            shift 2
            ;;
        --skip-tests)
            SKIP_TESTS=true
            shift
            ;;
        --skip-checkstyle)
            SKIP_CHECKSTYLE=true
            shift
            ;;
        -h|--help)
            sed -n '2,/^$/p' "$0" | sed 's/^# \?//'
            exit 0
            ;;
        *)
            log_error "未知参数：$1"
            log_info "使用 --help 查看帮助信息"
            exit 1
            ;;
    esac
done

# ---------- Maven 解析 ----------
if [[ -x "${MAVEN_HOME:-}/bin/mvn" ]]; then
    MAVEN_CMD="${MAVEN_HOME}/bin/mvn"
elif command -v mvn >/dev/null 2>&1; then
    MAVEN_CMD="$(command -v mvn)"
else
    log_error "找不到 mvn 命令。请安装 Maven 或设置 MAVEN_HOME 环境变量。"
    exit 1
fi

# ---------- 构建命令组装 ----------
MVN_ARGS=(-B)  # 非交互模式（batch）

if [[ -n "${MODULE}" ]]; then
    # -pl 指定模块，-am 同时构建其上游依赖
    MVN_ARGS+=("-pl" "${MODULE}" "-am")
    log_info "单模块模式：${MODULE}（含上游依赖）"
fi

if [[ "${SKIP_TESTS}" == "true" ]]; then
    MVN_ARGS+=("-DskipTests")
    log_warn "已跳过单元测试"
fi

if [[ "${SKIP_CHECKSTYLE}" == "true" ]]; then
    MVN_ARGS+=("-Dcheckstyle.skip=true")
    log_warn "已跳过 Checkstyle 校验"
fi

# ---------- 环境检查 ----------
cd "${PROJECT_ROOT}"

log_info "项目根目录：${PROJECT_ROOT}"
log_info "Maven 路径：${MAVEN_CMD}"
log_info "MAVEN_OPTS：${MAVEN_OPTS:-（未设置，使用 JVM 默认值）}"
log_info "执行命令：${MAVEN_CMD} verify ${MVN_ARGS[*]}"

# ---------- 执行构建 ----------
START_TIME=$(date +%s)

# shellcheck disable=SC2086  # MVN_ARGS 是有意通过数组展开，无注入风险（已在上方严格校验）
if MAVEN_OPTS="${MAVEN_OPTS:-}" "${MAVEN_CMD}" verify "${MVN_ARGS[@]}"; then
    END_TIME=$(date +%s)
    ELAPSED=$(( END_TIME - START_TIME ))
    log_success "构建成功（耗时 ${ELAPSED}s）"

    # JaCoCo 报告路径提示
    log_info "JaCoCo 覆盖率报告路径："
    if [[ -n "${MODULE}" ]]; then
        log_info "  -> ${MODULE}/target/site/jacoco/index.html"
    else
        log_info "  -> <module>/target/site/jacoco/index.html"
    fi
    exit 0
else
    EXIT_CODE=$?
    log_error "构建失败（退出码：${EXIT_CODE}）"
    log_info "提示：查看详细日志请运行 ${MAVEN_CMD} verify ${MVN_ARGS[*]} -X"
    exit 2
fi
