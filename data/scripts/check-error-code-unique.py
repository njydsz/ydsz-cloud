#!/usr/bin/env python3
"""错误码唯一性门禁 — 把"全平台错误码不得重复"从口头约定变成可执行断言（报告 B1-12）。

为什么需要它：错误码是**跨模块全局命名空间**（前端按 code 反解文案，见
`comm/effects/request/src/error-codes.generated.ts`）。但现有校验只在**单模块内**生效：
`ErrorCodeTable.registerCode` 对同模块重复抛异常，跨模块重复则各进各的模块索引；
`registerAll` 虽有全局 fail-fast，但只在"扫描注册"那条路径上生效。
实测 22 个错误码枚举、528 个码，**16 个码被重复占用**，其中包括
`B91001`（system 的字典类型不存在 vs message 的模板不存在）—— 前端产物里前者被后者覆盖，
用户看到的是"Template not found"。

判据（fail-closed）：
  E1 扫描 `**/*ExceptionCode.java`、`YdszResultCode.java`、`CoreExceptionCode.java` 里的
     `NAME("CODE"...` 字面量；扫不到任何文件直接判错（"扫不到"不等于"没问题"）
  E2 同一 code 出现在 >1 处 → 冲突项。**新增冲突立即判红**
  E3 存量冲突记录在 `data/error-code-baseline.json`，只可减不可增；
     消失的冲突会打印"可下调基线"建议，`--update-baseline` 由人复核后写入
  E4 重号需要改号时，本脚本不替人决定改谁 —— 只保证"不再变多"

用法：
  python3 data/scripts/check-error-code-unique.py
  python3 data/scripts/check-error-code-unique.py --self-test
  python3 data/scripts/check-error-code-unique.py --update-baseline
退出码：0 通过；1 存在新增冲突；2 前提不成立（扫不到文件等）。
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import tempfile
from collections import defaultdict
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
BASELINE_FILE = REPO_ROOT / "data" / "error-code-baseline.json"

# 只认"枚举常量定义行的首个字符串字面量"：NAME("B91001", ...) / NAME("A00000", ...)
CODE_LITERAL_RE = re.compile(r'^\s*([A-Z][A-Z0-9_]*)\s*\(\s*"([A-Z]\d{4,6})"', re.MULTILINE)

SCAN_SUFFIXES = ("ExceptionCode.java",)
SCAN_EXACT = {"YdszResultCode.java", "CoreExceptionCode.java"}

EXCLUDED_PATH_PARTS = ("/target/", "/backup_logs/", "/build/")


def rel(path: Path, root: Path) -> str:
    try:
        return path.resolve().relative_to(root.resolve()).as_posix()
    except ValueError:
        return path.as_posix()


def is_candidate(path: Path) -> bool:
    posix = path.as_posix()
    if any(part in posix for part in EXCLUDED_PATH_PARTS):
        return False
    name = path.name
    return name.endswith(SCAN_SUFFIXES) or name in SCAN_EXACT


def collect(root: Path) -> dict[str, list[tuple[str, str]]]:
    """code -> [(相对路径, 枚举常量名), ...]"""
    by_code: dict[str, list[tuple[str, str]]] = defaultdict(list)
    for path in sorted(root.rglob("*.java")):
        if not is_candidate(path):
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        for enum_name, code in CODE_LITERAL_RE.findall(text):
            by_code[code].append((rel(path, root), enum_name))
    return by_code


def findings(by_code: dict[str, list[tuple[str, str]]]) -> list[str]:
    """把重号拍平成可比较的稳定字符串。"""
    out: list[str] = []
    for code, owners in sorted(by_code.items()):
        if len(owners) < 2:
            continue
        rendered = " | ".join(f"{path}:{name}" for path, name in sorted(owners))
        out.append(f"{code} -> {rendered}")
    return out


def evaluate(current: list[str], baseline: list[str]) -> tuple[list[str], list[str]]:
    """返回 (新增冲突, 已消失冲突)。"""
    current_set, baseline_set = set(current), set(baseline)
    added = sorted(current_set - baseline_set)
    fixed = sorted(baseline_set - current_set)
    return added, fixed


def self_test() -> int:
    failures: list[str] = []

    def case(name: str, ok: bool) -> None:
        if not ok:
            failures.append(name)

    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        (root / "a").mkdir()
        (root / "a" / "FooExceptionCode.java").write_text(
            'public enum FooExceptionCode {\n  OK("B10001", "x"),\n  BAD("B10002", "y");\n}\n',
            encoding="utf-8",
        )
        (root / "b").mkdir()
        (root / "b" / "BarExceptionCode.java").write_text(
            'public enum BarExceptionCode {\n  NOPE("B10002", "z");\n}\n', encoding="utf-8"
        )
        (root / "target").mkdir()
        (root / "target" / "CopyExceptionCode.java").write_text(
            'public enum CopyExceptionCode {\n  DUP("B10002", "ignored");\n}\n', encoding="utf-8"
        )

        by_code = collect(root)
        case("扫描到两个文件里的三条字面量", sum(len(v) for v in by_code.values()) == 3)
        case("target 目录被排除", all("target" not in p for v in by_code.values() for p, _ in v))

        found = findings(by_code)
        case("跨文件重号被识别", len(found) == 1 and found[0].startswith("B10002 -> "))

        added, fixed = evaluate(found, [])
        case("无基线时新增即判红", len(added) == 1 and not fixed)

        added, fixed = evaluate(found, found)
        case("与基线一致时不判红", not added and not fixed)

        added, fixed = evaluate([], found)
        case("冲突消失 → 建议下调基线", not added and len(fixed) == 1)

        empty = Path(tmp) / "empty"
        empty.mkdir()
        case("扫不到文件返回空（由调用方判红）", collect(empty) == {})

    case(
        "同一枚举内重号也算冲突",
        findings({"B20001": [("x/AExceptionCode.java", "A"), ("x/AExceptionCode.java", "B")]}) != [],
    )
    case("A00000 这类成功码同样参与判定", findings({"A00000": [("p", "SUCCESS"), ("q", "SUCCESS")]}) != [])

    print(f"自检结果：{'全部通过' if not failures else f'{len(failures)} 条未通过'}")
    for name in failures:
        print(f"  ✗ {name}", file=sys.stderr)
    return 0 if not failures else 1


def load_baseline() -> list[str]:
    if not BASELINE_FILE.exists():
        print(f"❌ 基线文件不存在：{BASELINE_FILE}（先跑 --update-baseline 并由人复核）", file=sys.stderr)
        sys.exit(2)
    data = json.loads(BASELINE_FILE.read_text(encoding="utf-8"))
    return list(data.get("collisions", []))


def main() -> int:
    parser = argparse.ArgumentParser(description="错误码唯一性门禁（云顶编码规范 / 报告 B1-12）")
    parser.add_argument("--root", default=str(REPO_ROOT), help="仓库根目录")
    parser.add_argument("--self-test", action="store_true", help="运行反例夹具自检")
    parser.add_argument("--update-baseline", action="store_true", help="人复核后刷新存量冲突基线")
    args = parser.parse_args()

    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")
        except AttributeError:
            pass

    if args.self_test:
        return self_test()

    root = Path(args.root)
    by_code = collect(root)
    if not by_code:
        print("❌ 一个错误码都没扫到 —— 扫不到不等于没问题（fail-closed）", file=sys.stderr)
        return 2

    current = findings(by_code)
    total_codes = len(by_code)

    if args.update_baseline:
        BASELINE_FILE.parent.mkdir(parents=True, exist_ok=True)
        BASELINE_FILE.write_text(
            json.dumps({"collisions": current}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
        print(f"✅ 已写入基线 {BASELINE_FILE}（{len(current)} 条冲突 / {total_codes} 个码）")
        return 0

    added, fixed = evaluate(current, load_baseline())
    print(f"实测：{total_codes} 个错误码 / 存量冲突 {len(current)} 条 / 基线 {len(current) - len(added) + len(fixed)} 条")

    if added:
        print("❌ 新增错误码冲突（同一 code 被多个枚举占用）：", file=sys.stderr)
        for item in added:
            print(f"   {item}", file=sys.stderr)
        print("   处理：改用本模块码段内的空号；确需新增码段请先更新规范再登记基线。", file=sys.stderr)
        return 1

    for item in fixed:
        print(f"ℹ️ 可下调基线：冲突已消失 → {item}")
    print(f"✅ 错误码唯一性门禁通过（{len(current)} 条存量冲突未增加）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
