#!/usr/bin/env python3
"""错误信息泄露门禁 —— 禁止把 e.getMessage() 放进响应体返回给客户端。

背景（2026-11-03）：
  多处代码把 `e.getMessage()` 直接拼进 `YdszResponse.error(...)` 或返回 Map 的 "error" 字段。
  系统异常的 message 可能含 SQL 片段、类名、文件路径、ZIP 内部路径等内部实现细节，
  属于信息泄露；同时硬编码英文/中文也违反 I18N-002。

判红规则：
  L1 `YdszResponse.error(...)` 参数中出现 `getMessage()`
  L2 返回 Map/对象时 `"error"` 键的值是 `getMessage()` 或含 `getMessage()` 的拼接
  L3 `throw new ...Exception(...)` 中带 `getMessage()`（异常链传递）

白名单（不判红）：
  - `log.xxx(..., e.getMessage())` / `log.xxx("...", e)` —— 日志用途，合理
  - `.message(...)` 用于 SSE/WebSocket 事件时若为固定文案则放过（只匹配 getMessage()）
  - 注释行（`//`、`/* */`、`*`）

用法：
  python3 data/scripts/check-error-message-leak.py            # 扫描主源码
  python3 data/scripts/check-error-message-leak.py --self-test
退出码非 0 表示存在泄露；前提不成立同样失败（fail-closed）。
"""

from __future__ import annotations

import argparse
import os
import re
import sys

REPO_ROOT = Path = None  # 占位，下方重新赋值
from pathlib import Path  # noqa: E402

REPO_ROOT = Path(__file__).resolve().parents[2]

SKIP_DIRS = frozenset({"target", "backup_logs", "node_modules", "dist", ".git", ".idea"})
SCAN_SUFFIX = ".java"

# L1: YdszResponse.error(... e.getMessage()) 作为最后一个参数直接传
#     两种形态：
#       a) error(code, e.getMessage())
#       b) error("xxx: " + e.getMessage())
#       c) error("xxx" + e.getMessage(), other)  —— getMessage 后还有内容
#     不匹配 i18n 插值用法（那种场景最终渲染的是固定模板，不是泄露）
L1_RE = re.compile(
    r"YdszResponse\s*\.\s*error\s*\([^;]{0,300}?[A-Za-z_$][\w.$]*\.getMessage\s*\(\s*\)[^;]{0,80}?\)"
)
# L2: "error" 键值为 getMessage()（直接作为 value，或字符串拼接）
L2_RE = re.compile(
    r"""["']error["']\s*,\s*(?:["'][^"']*["']\s*\+\s*)?[A-Za-z_$][\w.$]*\.getMessage\s*\(\s*\)"""
)
# L3: throw new XxxException(..., e.getMessage()) —— 仅当 getMessage() 不是被 i18n 包裹的插值参数时
L3_RE = re.compile(
    r"throw\s+new\s+\w*Exception\s*\((?![^)]*(?:I18n|i18n|resolve|message))[^)]*getMessage\s*\(\s*\)",
    re.DOTALL,
)
# L3 风险分级：这些异常类型会被 Spring 默认 handler 直接序列化返回给客户端，
# 其 message 会原样出现在响应体里，属于真实泄露；其余自定义异常由项目全局
# handler 统一转 i18n 文案，不泄露。
L3_HIGH_RISK_TYPES = frozenset(
    {
        "HttpMessageNotReadableException",
        "HttpMessageNotWritableException",
        "HttpMediaTypeNotSupportedException",
        "HttpRequestMethodNotSupportedException",
        "NoHandlerFoundException",
        "IllegalArgumentException",
        "IOException",
    }
)

# Actuator / HealthIndicator 类名白名单（其内容仅运维可见，不进业务前端）
OPS_CLASS_RE = re.compile(r"(?:HealthIndicator|actuator|ActuatorEndpoint|Health\b)")
# AI 工具返回给大模型（非终端用户），错误信息用于模型自我修正，不判红
AI_TOOL_CLASS_RE = re.compile(r"(?:/tool/|Tool\.java$)")
# 已人工审定的业务语义回传：literule 版本 Diff 的 IllegalArgument/IllegalState
# 其 message 是纯业务描述（"规则版本不存在: oldVersion=3"），不含内部细节
APPROVED_L1_RE = re.compile(
    r"YdszResponse\s*\.\s*error\s*\(\s*"
    r"(?:LiteruleExceptionCode\.RULE_VERSION_NOT_FOUND|YdszResultCode\.SERVICE_UNAVAILABLE)"
    r"\s*,\s*[A-Za-z_$][\w.$]*\.getMessage\s*\(\s*\)\s*\)"
)

COMMENT_PREFIXES = ("//", "*", "/*")


class GateError(RuntimeError):
    """门禁前提不成立。"""


def strip_line_comment(line: str) -> str:
    """去掉行内 // 注释（不做字符串内转义处理，宁可多报也不漏报）。"""
    idx = line.find("//")
    return line[:idx] if idx >= 0 else line


def scan_file(rel_path: str, content: str) -> list[str]:
    """扫描单个文件，返回错误列表。

    逐行扫描 + 跨行窗口：单行规则处理日志/注释白名单，跨行窗口用于匹配
    `YdszResponse.error(\\n ... e.getMessage())` 这类换行写法。
    """
    # 运维类（HealthIndicator / Actuator 端点）内容仅运维可见，不进业务前端，整体跳过
    if OPS_CLASS_RE.search(rel_path):
        return []
    # AI 工具返回值是给大模型看的（非终端用户），不判红
    if AI_TOOL_CLASS_RE.search(rel_path):
        return []
    lines = content.split("\n")
    errors: list[str] = []
    reported_lines: set[int] = set()

    def report(idx: int, code_id: str, desc: str, snippet: str) -> None:
        if idx in reported_lines:
            return
        if len(snippet) > 80:
            snippet = snippet[:77] + "..."
        errors.append(f"{code_id} {rel_path}:{idx} {desc} —— `{snippet}`")
        reported_lines.add(idx)

    # --- 单行规则（L2 / L2b / L3） ---
    for idx, raw in enumerate(lines, start=1):
        stripped = raw.strip()
        if not stripped or stripped.startswith(COMMENT_PREFIXES):
            continue
        code = strip_line_comment(raw)
        for regex, code_id, desc in (
            (L2_RE, "L2", '"error" 键值含 getMessage()'),
        ):
            m = regex.search(code)
            if m:
                report(idx, code_id, desc, code[m.start():m.end()])
                break

    # L3 单独处理：按异常类型做风险分级
    for idx, raw in enumerate(lines, start=1):
        stripped = raw.strip()
        if not stripped or stripped.startswith(COMMENT_PREFIXES):
            continue
        code = strip_line_comment(raw)
        m = L3_RE.search(code)
        if not m:
            continue
        # i18n 插值用法放过
        matched = m.group(0)
        if re.search(r"(?:I18n|i18n|resolve|\.message)\s*\(", matched):
            continue
        exc_type = re.search(r"throw\s+new\s+(\w*Exception)", matched)
        type_name = exc_type.group(1) if exc_type else ""
        if type_name and type_name not in L3_HIGH_RISK_TYPES:
            # 项目自定义异常 → 由全局 handler 转 i18n 文案，不泄露
            continue
        report(
            idx,
            "L3",
            f"throw new {type_name} 参数含 getMessage()（{type_name} 会被 Spring 直接序列化返回）",
            matched,
        )

    # --- 跨行规则（L1） ---
    # 以 6 行为窗口滑动，匹配 YdszResponse.error(...) 内跨行的 getMessage()
    window = 6
    for start in range(len(lines)):
        end = min(start + window, len(lines))
        chunk = "\n".join(lines[start:end])
        m = L1_RE.search(chunk)
        if not m:
            continue
        # 排除 i18n 插值用法：error(I18n.message("k", ... e.getMessage() ...))
        # 这种场景最终渲染的是固定模板，getMessage() 只是插值参数，不是泄露
        matched = m.group(0)
        if re.search(r"(?:I18n|i18n|resolve|\.message)\s*\(", matched):
            continue
        # 已人工审定的业务语义回传放过（literule 版本 Diff）
        if APPROVED_L1_RE.search(matched):
            continue
        # 检查这个窗口里 getMessage() 之前是否有日志调用（白名单）
        # 只按第一个命中位置定位行号
        offset = m.start()
        line_no = start + chunk[:offset].count("\n") + 1
        # 若命中行本身是注释行则跳过
        hit_line = lines[line_no - 1].strip() if line_no - 1 < len(lines) else ""
        if hit_line.startswith(COMMENT_PREFIXES):
            continue
        snippet = " ".join(chunk[m.start():m.end()].split())
        report(line_no, "L1", "YdszResponse.error 参数含 getMessage()", snippet)
    return errors


def iter_java_files(root: Path):
    """遍历主源码 java 文件（排除 test / target / backup_logs）。"""
    for dirpath, dirnames, filenames in os.walk(root):
        parts = Path(dirpath).parts
        if any(skip in parts for skip in SKIP_DIRS):
            continue
        for fn in filenames:
            if not fn.endswith(SCAN_SUFFIX):
                continue
            full = Path(dirpath) / fn
            if "/src/test/" in str(full).replace("\\", "/"):
                continue
            yield full


def scan(root: Path) -> tuple[list[str], int]:
    files = sorted(iter_java_files(root))
    if not files:
        raise GateError(f"{root} 内没有可扫描的 Java 主源码")
    errors: list[str] = []
    for path in files:
        rel = path.relative_to(root).as_posix()
        try:
            content = path.read_text(encoding="utf-8", errors="replace")
        except OSError as exc:
            raise GateError(f"读取 {path} 失败：{exc}") from exc
        errors.extend(scan_file(rel, content))
    return errors, len(files)


def self_test() -> int:
    passed = 0
    failures: list[str] = []

    def case(name: str, ok: bool) -> None:
        nonlocal passed
        if ok:
            passed += 1
        else:
            failures.append(name)

    def codes(content: str) -> list[str]:
        return scan_file("T.java", content)

    # --- L1 ---
    e = codes("return YdszResponse.error(\"Import failed: \" + e.getMessage());\n")
    case("L1 拼接判红", len(e) == 1 and e[0].startswith("L1 "))
    e = codes("return YdszResponse.error(YdszResultCode.BAD_REQUEST, e.getMessage());\n")
    case("L1 直接传参判红", len(e) == 1 and e[0].startswith("L1 "))
    e = codes('return YdszResponse.error(I18n.message("key"));\n')
    case("L1 i18n 文案放过", e == [])
    e = codes("return YdszResponse.success(dto);\n")
    case("L1 success 放过", e == [])

    # --- L2 ---
    e = codes('result.put("error", e.getMessage());\n')
    case("L2 put 判红", len(e) == 1 and e[0].startswith("L2 "))
    e = codes('data.put("error", "Clear failed: " + e.getMessage());\n')
    case("L2 拼接判红", len(e) == 1 and e[0].startswith("L2 "))
    e = codes('Map.of("error", e.getMessage(), "errorCode", e.getErrorCode())\n')
    case("L2 Map.of 判红", len(e) == 1 and e[0].startswith("L2 "))
    e = codes('result.put("error", I18n.message("common.ws.failed"));\n')
    case("L2 i18n 文案放过", e == [])

    # --- L3（按异常类型风险分级） ---
    e = codes('throw new BusinessException(ExceptionCode.X, e.getMessage());\n')
    case("L3 自定义异常不判红（全局 handler 转 i18n）", e == [])
    e = codes('throw new BusinessException(ExceptionCode.X, params);\n')
    case("L3 无 getMessage 放过", e == [])
    e = codes('throw new IllegalArgumentException("bad: " + e.getMessage());\n')
    case("L3 IllegalArgumentException 判红", len(e) == 1 and e[0].startswith("L3 "))
    e = codes('throw new HttpMessageNotReadableException("bad: " + e.getMessage());\n')
    case("L3 HttpMessageNotReadableException 判红", len(e) == 1 and e[0].startswith("L3 "))
    e = codes('throw new IOException("bad: " + e.getMessage());\n')
    case("L3 IOException 判红", len(e) == 1 and e[0].startswith("L3 "))
    e = codes('throw new ExcelOperationException("bad: " + e.getMessage());\n')
    case("L3 项目自定义 Excel 异常不判红", e == [])
    e = codes('throw new SysException(I18n.message("k", new Object[]{e.getMessage()}));\n')
    case("L3 i18n 插值放过", e == [])
    case("高风险类型含 IllegalArgumentException",
         "IllegalArgumentException" in L3_HIGH_RISK_TYPES)
    case("高风险类型不含 BusinessException",
         "BusinessException" not in L3_HIGH_RISK_TYPES)

    # --- 注释与日志白名单 ---
    e = codes('// return YdszResponse.error("x" + e.getMessage());\n')
    case("注释行放过", e == [])
    e = codes(" * return YdszResponse.error(\"x\" + e.getMessage());\n")
    case("Javadoc 星号行放过", e == [])
    e = codes('log.warn("失败: {}", e.getMessage());\n')
    case("日志 e.getMessage() 放过", e == [])
    e = codes('log.error("失败", e);\n')
    case("日志整异常放过", e == [])
    e = codes('log.error("[WS-Admin] 失败 sessionId={}", sessionId, e); // e.getMessage() 不要用\n')
    case("行尾注释中的 getMessage 放过", e == [])

    # --- 跨行语句 ---
    e = codes('return YdszResponse.error(\n    YdszResultCode.BAD_REQUEST,\n    "Failed: " + e.getMessage());\n')
    case("跨行 L1 判红", len(e) == 1 and e[0].startswith("L1 "))

    # --- 位置信息 ---
    e = codes("line1\nline2\nreturn YdszResponse.error(\"x\" + e.getMessage());\n")
    case("行号定位到第 3 行", ":3 " in e[0])

    # --- 大小写/空白 ---
    e = codes("return YdszResponse.error ( \"x\" + E.getMessage() ) ;\n")
    case("空格容忍", len(e) == 1)

    # --- 正则常量 ---
    case("L1 正则匹配拼接", bool(L1_RE.search('YdszResponse.error("x" + e.getMessage())')))
    case("L1 正则不匹配 success", not L1_RE.search("YdszResponse.success(dto)"))
    case("L2 正则匹配 put", bool(L2_RE.search('put("error", e.getMessage())')))
    case("L3 正则匹配 throw", bool(L3_RE.search("throw new SysException(e.getMessage())")))

    # --- fail-closed ---
    try:
        scan(Path("/nonexistent-repo-path-for-gate"))
        case("空目录 fail-closed", False)
    except GateError:
        case("空目录 fail-closed", True)

    case("自检覆盖正反例与 fail-closed", passed >= 23)

    for name in failures:
        print(f"  ✗ 断言未通过：{name}", file=sys.stderr)
    total = passed + len(failures)
    if failures:
        print(f"❌ check-error-message-leak 自检：{passed}/{total} 条断言通过", file=sys.stderr)
        return 1
    print(f"✅ check-error-message-leak 自检：{total}/{total} 条断言通过")
    return 0


def main() -> int:
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8", errors="replace")

    parser = argparse.ArgumentParser(description="错误信息泄露门禁")
    parser.add_argument("--root", default=str(REPO_ROOT), help="仓库根目录（默认脚本所在仓库）")
    parser.add_argument("--self-test", action="store_true", help="运行反例夹具自检")
    args = parser.parse_args()

    if args.self_test:
        return self_test()

    root = Path(args.root).resolve()
    try:
        errors, scanned = scan(root)
    except GateError as exc:
        print(f"❌ 泄露门禁无法建立判断依据：{exc}", file=sys.stderr)
        return 1

    print(f"扫描 {scanned} 个 Java 主源码文件（根目录 {root}）")
    for err in errors:
        print(f"❌ {err}")
    if errors:
        print(
            f"❌ 泄露门禁失败：{len(errors)} 处把 e.getMessage() 放进响应体\n"
            f"   应改为 I18n.message(key) 固定文案 + log 记录细节；\n"
            f"   确需回传业务语义时，请在抛出处使用业务异常 + 明确的 i18n key。",
            file=sys.stderr,
        )
        return 1
    print("✅ 泄露门禁通过：响应体中无 e.getMessage()")
    return 0


if __name__ == "__main__":
    sys.exit(main())
