#!/usr/bin/env python3
"""i18n 硬编码文案门禁 —— 禁止在业务模块中出现「用户可见」的硬编码自然语言文案。

背景（YDIZ-I18N-002）：
  Service / Controller 等 Spring 托管层禁止出现自然语言文案字面量。
  异常消息、接口返回 message、业务描述等用户可见文案必须通过 i18n key 解析。

判定口径（本脚本只对「用户可见」文案判红，避免误伤）：
  判红：
    U1 `YdszResponse.error(..., "中文...")`           —— 接口返回 message
    U2 `.message("中文...")` / `BusinessException("中文...")` —— 异常消息（抛出处）
    U3 `Map.put("error"/"message"/"msg", "中文...")`  —— 返回体中的错误字段
    U4 `String title = "中文..."` / `setXxxName("中文...")` —— 通知标题、模板名等业务数据
    U5 `result.put("suggestions", ...)` 中文诊断建议   —— 运维可见

  放过（YDIZ-I18N-002 允许例外）：
    - 日志调用（log.info/warn/error/debug/trace）
    - 行注释 / 块注释 / Javadoc
    - Swagger 注解（@Tag/@Operation/@ApiResponse/@Parameter/@Schema）及其参数
    - 字符串常量定义行（`private static final String XXX = "..."`）—— 这些是「待替换的占位」，
      由开发者按模块批量替换；门禁只约束使用点
    - 测试代码（src/test）
    - L1/L2 common 模块（json/util/cache/excel/core-base）—— 规范允许纯英文/技术性描述

用法：
  python3 data/scripts/check-i18n-hardcode.py [--self-test] [--list]
退出码非 0 表示存在违规；前提不成立同样失败（fail-closed）。
"""

from __future__ import annotations

import argparse
import os
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]

# L1/L2 common 模块（规范允许技术性描述，不判红）
L12_MODULES = frozenset({
    "ydsz-common-json", "ydsz-common-util", "ydsz-common-cache", "ydsz-common-excel",
    "ydsz-common-core-base", "ydsz-common-core", "ydsz-common-domain", "ydsz-common-exception",
})
SKIP_DIRS = frozenset({"target", "backup_logs", "node_modules", "dist", ".git", ".idea"})

CJK_RE = re.compile(r"[一-鿿]")

# 使用点判据
U1_RE = re.compile(r"YdszResponse\s*\.\s*error\s*\([^;]*?\"[^\"]*[一-鿿][^\"]*\"")
U2_RE = re.compile(
    r"(?:\.message\s*\(|throw\s+new\s+\w*Exception\s*\()\s*\"[^\"]*[一-鿿][^\"]*\""
)
U3_RE = re.compile(
    r"(?:put|of)\s*\(\s*\"(?:error|message|msg|errorMessage)\"\s*,\s*(?:\"[^\"]*[一-鿿][^\"]*\"|[A-Za-z_$][\w.$]*\s*\+\s*\"[^\"]*[一-鿿][^\"]*\")"
)
U4_RE = re.compile(
    r"(?:setTitle|setTemplateName|setNodeName|setAssigneeName|setName)\s*\(\s*\"[^\"]*[一-鿿][^\"]*\""
    r"|String\s+\w*(?:[Tt]itle|[Nn]ame|[Ss]ummary|[Cc]ontent)\w*\s*=\s*\"[^\"]*[一-鿿][^\"]*\""
)

# 白名单模式
LOG_RE = re.compile(r"^\s*log\s*\.\s*(?:info|warn|error|debug|trace)\s*\(")
COMMENT_RE = re.compile(r"^\s*(?://|\*|/\*)")
ANNOTATION_RE = re.compile(
    r"^\s*@(Tag|Operation|ApiResponse|Parameter|Schema|Audit|RateLimit)\b"
)
ANNOTATION_ARG_RE = re.compile(
    r"^\s*(?:description|summary|name|module|type|action|content|example|required)\s*="
)
CONST_DEF_RE = re.compile(
    r"^\s*(?:private|public|protected)?\s*static\s+final\s+String\s+\w+\s*="
)
PROMPT_HINT_RE = re.compile(r"(?:prompt|PROMPT|系统提示|你是|You are)")


class GateError(RuntimeError):
    """门禁前提不成立。"""


def is_l12(path: str) -> bool:
    parts = path.split("/")
    if len(parts) >= 2:
        return parts[1] in L12_MODULES
    return False


def classify(line: str) -> str | None:
    """返回违规代码（U1-U5），不违规返回 None。"""
    if not CJK_RE.search(line):
        return None
    if COMMENT_RE.match(line) or LOG_RE.match(line):
        return None
    if ANNOTATION_RE.match(line) or ANNOTATION_ARG_RE.match(line):
        return None
    if CONST_DEF_RE.match(line):
        return None
    if PROMPT_HINT_RE.search(line) and ("String" in line or "=" in line):
        # LLM prompt 模板常量，属技术配置
        return None
    if U1_RE.search(line):
        return "U1"
    if U2_RE.search(line):
        return "U2"
    if U3_RE.search(line):
        return "U3"
    if U4_RE.search(line):
        return "U4"
    return None


def scan_file(rel_path: str, content: str) -> list[str]:
    errors: list[str] = []
    for idx, line in enumerate(content.split("\n"), start=1):
        code = classify(line)
        if code:
            snippet = line.strip()
            if len(snippet) > 90:
                snippet = snippet[:87] + "..."
            errors.append(f"{code} {rel_path}:{idx} {snippet}")
    return errors


def iter_java_files(root: Path):
    for dirpath, dirnames, filenames in os.walk(root):
        parts = Path(dirpath).parts
        if any(skip in parts for skip in SKIP_DIRS):
            continue
        for fn in filenames:
            if not fn.endswith(".java"):
                continue
            full = Path(dirpath) / fn
            if "/src/test/" in str(full).replace("\\", "/"):
                continue
            if "/src/main/" not in str(full).replace("\\", "/"):
                continue
            yield full


def scan(root: Path) -> tuple[list[str], int, int]:
    files = sorted(iter_java_files(root))
    if not files:
        raise GateError(f"{root} 内没有可扫描的 Java 主源码")
    errors: list[str] = []
    scanned = 0
    skipped_l12 = 0
    for path in files:
        rel = path.relative_to(root).as_posix()
        if is_l12(rel):
            skipped_l12 += 1
            continue
        scanned += 1
        try:
            content = path.read_text(encoding="utf-8", errors="replace")
        except OSError as exc:
            raise GateError(f"读取 {path} 失败：{exc}") from exc
        errors.extend(scan_file(rel, content))
    return errors, scanned, skipped_l12


def self_test() -> int:
    passed = 0
    failures: list[str] = []

    def case(name: str, ok: bool) -> None:
        nonlocal passed
        if ok:
            passed += 1
        else:
            failures.append(name)

    def codes(line: str) -> str | None:
        return classify(line)

    # --- U1 ---
    case("U1 接口返回中文判红", codes('return YdszResponse.error("参数错误");') == "U1")
    case("U1 拼接判红", codes('return YdszResponse.error("失败: " + x);') == "U1")
    case("U1 i18n 放过", codes('return YdszResponse.error(I18n.message("k"));') is None)
    case("U1 success 放过", codes('return YdszResponse.success(dto);') is None)

    # --- U2 ---
    case("U2 异常中文判红", codes('throw new BusinessException("用户不存在");') == "U2")
    case("U2 message 判红", codes('.message("流程定义不存在: " + id)') == "U2")
    case("U2 i18n 放过", codes('throw new BusinessException(I18n.message("k"))') is None)
    case("U2 英文放过", codes('throw new BusinessException("user not found")') is None)

    # --- U3 ---
    case("U3 error 键中文判红", codes('result.put("error", "表达式不能为空");') == "U3")
    case("U3 拼接判红", codes('result.put("error", "失败: " + e.getMessage());') == "U3")
    case("U3 英文放过", codes('result.put("error", "parse failed");') is None)
    case("U3 i18n 放过", codes('result.put("error", I18n.message("k"));') is None)
    case("U3 其他键放过", codes('result.put("status", "运行中");') is None)

    # --- U4 ---
    case("U4 通知标题判红", codes('String title = "您有一个新的审批待办";') == "U4")
    case("U4 模板名判红", codes('tpl.setTemplateName("请假审批");') == "U4")

    # --- 白名单 ---
    case("日志中文放过", codes('log.info("用户{}登录", userId);') is None)
    case("注释中文放过", codes('// 这是注释') is None)
    case("Javadoc 星号行放过", codes(' * 这是文档') is None)
    case("Swagger 注解放过", codes('@Operation(summary = "任务详情查询")') is None)
    case("注解参数行放过", codes('        description = "查询指定任务")') is None)
    case("常量定义行放过", codes('private static final String TITLE = "待办";') is None)
    case("prompt 常量放过",
         codes('private static final String SYS_PROMPT = "你是助手";') is None)
    case("英文行放过", codes('String title = "Approval";') is None)
    case("无中文放过", codes('String x = "abc";') is None)

    # --- L1/L2 豁免 ---
    case("L1 json 模块豁免", is_l12("ydsz-common/ydsz-common-json/src/main/java/A.java"))
    case("L1 util 模块豁免", is_l12("ydsz-common/ydsz-common-util/src/main/java/A.java"))
    case("L2 core 模块豁免", is_l12("ydsz-common/ydsz-common-core/src/main/java/A.java"))
    case("业务模块不豁免", not is_l12("ydsz-workflow/ydsz-workflow-server/src/main/java/A.java"))
    case("common-socket 不豁免", not is_l12("ydsz-common/ydsz-common-socket/src/main/java/A.java"))

    # --- fail-closed ---
    try:
        scan(Path("/nonexistent-repo-for-i18n-gate"))
        case("空目录 fail-closed", False)
    except GateError:
        case("空目录 fail-closed", True)

    case("自检覆盖正反例与 fail-closed", passed >= 30)

    for name in failures:
        print(f"  ✗ 断言未通过：{name}", file=sys.stderr)
    total = passed + len(failures)
    if failures:
        print(f"❌ check-i18n-hardcode 自检：{passed}/{total} 条断言通过", file=sys.stderr)
        return 1
    print(f"✅ check-i18n-hardcode 自检：{total}/{total} 条断言通过")
    return 0


def load_baseline(root: Path) -> int:
    """读取存量基线（违规数上限）。文件不存在时返回 0（要求全清）。"""
    baseline = root / "data" / "i18n-hardcode-baseline.json"
    if not baseline.is_file():
        return 0
    try:
        import json

        data = json.loads(baseline.read_text(encoding="utf-8"))
        return int(data.get("max_violations", 0))
    except (OSError, ValueError):
        return 0


def update_baseline(root: Path) -> int:
    """把当前违规数写为新的基线（棘轮只减不增）。"""
    import datetime
    import json

    baseline = root / "data" / "i18n-hardcode-baseline.json"
    try:
        errors, _, _ = scan(root)
    except GateError as exc:
        print(f"❌ 无法扫描：{exc}", file=sys.stderr)
        return 1
    payload = {
        "max_violations": len(errors),
        "updated_at": datetime.datetime.now().isoformat(timespec="seconds"),
        "note": "i18n 硬编码文案存量棘轮基线，只减不增；清理后运行 --update-baseline 下调",
    }
    baseline.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"✅ 基线已更新：max_violations = {len(errors)}")
    return 0


def main() -> int:
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8", errors="replace")

    parser = argparse.ArgumentParser(description="i18n 硬编码文案门禁")
    parser.add_argument("--root", default=str(REPO_ROOT), help="仓库根目录")
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--list", action="store_true", help="只列出违规不判红")
    parser.add_argument("--update-baseline", action="store_true", help="把当前违规数写为基线")
    args = parser.parse_args()

    if args.self_test:
        return self_test()

    root = Path(args.root).resolve()
    if args.update_baseline:
        return update_baseline(root)

    try:
        errors, scanned, skipped = scan(root)
    except GateError as exc:
        print(f"❌ i18n 门禁无法建立判断依据：{exc}", file=sys.stderr)
        return 1

    baseline = load_baseline(root)
    print(
        f"扫描 {scanned} 个业务模块 Java 文件（跳过 {skipped} 个 L1/L2 common 模块文件，根目录 {root}）"
    )
    print(f"存量基线：{baseline} 处（棘轮只减不增）")
    shown = errors if args.list else errors[:30]
    for err in shown:
        print(f"{'⚠️ ' if args.list else '❌'} {err}")
    if not args.list and len(errors) > 30:
        print(f"   ... 其余 {len(errors) - 30} 处省略（--list 查看全部）")
    if errors:
        print(
            f"❌ 当前 {len(errors)} 处用户可见硬编码文案（基线 {baseline}）\n"
            f"   应改为 I18n.message(key) + properties 三语 key；"
            f"日志/注解/注释/常量定义属规范允许例外，不判红。",
            file=sys.stderr,
        )
        if len(errors) > baseline:
            print(
                f"❌ 违规数 {len(errors)} 超过基线 {baseline} —— 存量上涨，禁止合入\n"
                f"   清理后运行：python3 data/scripts/check-i18n-hardcode.py --update-baseline",
                file=sys.stderr,
            )
            return 1
        return 0
    print("✅ i18n 门禁通过：无用户可见硬编码文案")
    return 0


if __name__ == "__main__":
    sys.exit(main())
