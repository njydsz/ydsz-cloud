#!/usr/bin/env python3
"""Checkstyle 基线「只降不升」门禁（B1，2026-11-06 新增）。

背景
====
`check-checkstyle-delta.py` 的 C2 棘轮逻辑本身是「存量只减不增」，但它比较的是
**当前报告 vs 基线文件**——只要有人跑一次 `--update-baseline`，基线就会被刷成当前值，
棘轮随即失效。历史上已发生 4 次基线刷新（4131 → 4000 → 737 → 747），
其中 747 那次让 `message-server` 从 9 涨到 34（+25 条 LineLength），门禁全程绿色。

本门禁的做法
============
比较**基线文件在 git 中的历史版本**与**工作区版本**：
- `total_errors` 上升 → 判红（除非显式传入 `--allow-increase` 并有评审记录）
- 任一模块 `errors` 上升 → 判红
- 下降或持平 → 通过

用法
====
    python3 data/scripts/check-baseline-monotonic.py              # 门禁判定
    python3 data/scripts/check-baseline-monotonic.py --update-baseline  # 允许刷新（需评审）
    python3 data/scripts/check-baseline-monotonic.py --self-test       # 自测

退出码：0=通过，1=判红，2=环境不满足（git 不可用等，交由 CI 决定是否跳过）
"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
import tempfile
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
BASELINE_FILE = REPO_ROOT / "data" / "checkstyle-baseline.json"

# 与 check-checkstyle-delta.py 保持同一噪声口径：只把
# 「NewlineAtEndOfFile + 消息含 CRLF」视为平台噪声（本机检出被转成 CRLF，
# 入库 blob 干净）。若不沿用此口径，基线计数会因噪声漂移而误判上涨。
PLATFORM_NOISE_RULES = frozenset({"NewlineAtEndOfFile"})
PLATFORM_NOISE_MARKERS = ("CRLF",)


class GateError(RuntimeError):
    """门禁前提不成立（git 不可用、基线损坏）。"""


def load_json(path: Path) -> dict:
    """加载 JSON 文件。"""
    try:
        with path.open(encoding="utf-8") as handle:
            return json.load(handle)
    except (OSError, json.JSONDecodeError) as exc:
        raise GateError(f"无法读取基线文件 {path}: {exc}") from exc


def load_baseline_from_git(ref: str) -> dict | None:
    """从 git 历史中读取基线文件；文件不存在时返回 None。"""
    try:
        proc = subprocess.run(
            ["git", "-C", str(REPO_ROOT), "show", f"{ref}:data/checkstyle-baseline.json"],
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=30,
        )
    except (OSError, subprocess.SubprocessError) as exc:
        raise GateError(f"git 不可用：{exc}") from exc
    if proc.returncode != 0:
        return None
    try:
        return json.loads(proc.stdout)
    except json.JSONDecodeError as exc:
        raise GateError(f"git 中的基线文件无法解析：{exc}") from exc


def count_current_errors() -> dict[str, int]:
    """从 checkstyle 报告统计当前各模块 error 数（沿用门禁噪声口径）。

    <p>复用 {@code check-checkstyle-delta.py} 的判定：{@code NewlineAtEndOfFile}
    且消息含 {@code CRLF} 视为平台噪声不计入；其余（含缺尾行）均计入。
    """
    import xml.etree.ElementTree as ET

    counts: dict[str, int] = {}
    reports = sorted(REPO_ROOT.glob("*/target/checkstyle-result.xml"))
    reports += sorted(REPO_ROOT.glob("*/*/target/checkstyle-result.xml"))
    reports += sorted(REPO_ROOT.glob("*/*/*/target/checkstyle-result.xml"))
    for report in reports:
        try:
            tree = ET.parse(report)
        except (OSError, ET.ParseError):
            continue
        module_dir = report.parent.parent.relative_to(REPO_ROOT).as_posix()
        for file_node in tree.getroot().iter("file"):
            for error in file_node.findall("error"):
                rule = _rule_short_name(error.get("source", ""))
                message = error.get("message") or ""
                if rule in PLATFORM_NOISE_RULES and any(
                    marker in message for marker in PLATFORM_NOISE_MARKERS
                ):
                    continue
                counts[module_dir] = counts.get(module_dir, 0) + 1
    return counts


def _rule_short_name(source_attr: str) -> str:
    """提取 checkstyle 规则短名。"""
    leaf = (source_attr or "").rsplit(".", 1)[-1]
    return leaf[: -len("Check")] if leaf.endswith("Check") else leaf


def compare(old: dict, new: dict) -> tuple[list[str], list[str]]:
    """比较两个基线，返回 (错误列表, 警告列表)。"""
    errors: list[str] = []
    warnings: list[str] = []

    old_total = old.get("total_errors")
    new_total = new.get("total_errors")
    if isinstance(old_total, int) and isinstance(new_total, int):
        if new_total > old_total:
            errors.append(
                f"基线 total_errors 上涨：{old_total} → {new_total}（+{new_total - old_total}）"
                "—— 棘轮门禁要求存量只减不增；若确需上调，请在 PR 描述中说明原因并"
                "使用 --allow-increase"
            )
        elif new_total < old_total:
            warnings.append(f"基线 total_errors 下降：{old_total} → {new_total}（良）")

    # 模块维度：优先用「当前 checkstyle 报告」实测值（与门禁同口径），
    # 避免基线 JSON 与实际报告口径不一致导致的误判。
    old_modules = old.get("modules", {})
    try:
        current_counts = count_current_errors()
    except Exception:  # noqa: BLE001 - 统计失败时回退到基线 JSON 对比
        current_counts = {}

    if current_counts:
        # 只比较基线中存在且当前有报告的模块
        compared = set()
        for module_dir, old_stat in sorted(old_modules.items()):
            if module_dir not in current_counts:
                continue
            compared.add(module_dir)
            old_errors = old_stat.get("errors", 0)
            new_errors = current_counts[module_dir]
            if new_errors > old_errors:
                errors.append(
                    f"模块 {module_dir} 存量上涨：{old_errors} → {new_errors}"
                    f"（+{new_errors - old_errors}）"
                )
        # 新模块带违规也判红（与 C2 一致）
        for module_dir, new_errors in sorted(current_counts.items()):
            if module_dir in old_modules or new_errors == 0:
                continue
            errors.append(f"新模块 {module_dir} 带违规入库：{new_errors} 条")
    else:
        new_modules = new.get("modules", {})
        for module_dir, stat in sorted(new_modules.items()):
            old_stat = old_modules.get(module_dir)
            if old_stat is None:
                continue
            old_errors = old_stat.get("errors", 0)
            new_errors = stat.get("errors", 0)
            if new_errors > old_errors:
                errors.append(
                    f"模块 {module_dir} 存量上涨：{old_errors} → {new_errors}"
                    f"（+{new_errors - old_errors}）"
                )
    return errors, warnings


def self_test() -> int:
    """自测：构造基线对比场景（不依赖真实 checkstyle 报告）。"""
    base = {"total_errors": 100, "modules": {"a": {"errors": 10}, "b": {"errors": 5}}}
    # 注入空报告统计，强制走「基线 JSON 对比」分支，使自测可独立运行
    original = globals().get("count_current_errors")
    globals()["count_current_errors"] = lambda: {}
    try:
        cases = [
            ("基线上涨判红", base, {"total_errors": 120, "modules": {"a": {"errors": 10}}}, True),
            ("模块上涨判红", base, {"total_errors": 100, "modules": {"a": {"errors": 15}}}, True),
            ("基线下降通过", base, {"total_errors": 90, "modules": {"a": {"errors": 10}}}, False),
            ("持平通过", base, {"total_errors": 100, "modules": {"a": {"errors": 10}}}, False),
        ]
        failed = 0
        for name, old, new, expect_error in cases:
            errs, _ = compare(old, new)
            got_error = bool(errs)
            ok = got_error == expect_error
            print(f"  {'✓' if ok else '✗'} {name}: 期望 {'红' if expect_error else '绿'}，"
                  f"实际 {'红' if got_error else '绿'}")
            if not ok:
                failed += 1
        print(f"\n自测结果：{'全部通过' if failed == 0 else f'{failed} 个用例失败'}")
        return 0 if failed == 0 else 1
    finally:
        if original is not None:
            globals()["count_current_errors"] = original


def main() -> int:
    """主入口。"""
    parser = argparse.ArgumentParser(description="Checkstyle 基线只降不升门禁")
    parser.add_argument(
        "--ref",
        default="HEAD",
        help="用于对比的 git ref（默认 HEAD，即与当前提交比较）",
    )
    parser.add_argument(
        "--allow-increase",
        action="store_true",
        help="允许基线上涨（需在 PR 中说明原因，CI 中不建议启用）",
    )
    parser.add_argument("--self-test", action="store_true", help="运行自测")
    args = parser.parse_args()

    if args.self_test:
        return self_test()

    if not BASELINE_FILE.exists():
        print(f"[SKIP] 基线文件不存在：{BASELINE_FILE}")
        return 2

    try:
        current = load_json(BASELINE_FILE)
        previous = load_baseline_from_git(args.ref)
    except GateError as exc:
        print(f"[SKIP] {exc}")
        return 2

    if previous is None:
        print(f"[SKIP] git ref {args.ref} 中无基线文件（首次引入或路径变更）")
        return 2

    errors, warnings = compare(previous, current)
    for warning in warnings:
        print(f"[WARN] {warning}")
    if not errors:
        print("[PASS] 基线未上涨，棘轮门禁通过")
        return 0
    for error in errors:
        print(f"[FAIL] {error}")
    if args.allow_increase:
        print("[WARN] 已启用 --allow-increase，跳过判红（请在 PR 中说明原因）")
        return 0
    return 1


if __name__ == "__main__":
    sys.exit(main())
