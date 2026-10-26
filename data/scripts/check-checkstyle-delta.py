#!/usr/bin/env python3
"""Checkstyle 存量棘轮 + 增量红线门禁 — 把 CLAUDE.md/AGENTS.md 宣称的"checkstyle 强制"变成可执行断言。

背景（实测，见报告 B1-2）：根 pom 的 maven-checkstyle-plugin 只出现在 pluginManagement，
没有 executions/phase 绑定，`mvn compile|package` 从不跑 checkstyle；CI 用独立步骤
`mvn checkstyle:check` 全仓执行，而全仓实测有 4388 条 error 级违规 —— 该步骤恒红，
把下游 `mvn verify`（单测 + 覆盖率）整段遮断。全量清零不现实，本脚本改用两条可执行判据：

  C1 增量红线：本次改动涉及的 .java 文件必须 0 违规（AI/人工新写的代码当场拦住）
  C2 存量棘轮：每个模块的违规数只减不增；模块违规数上涨即红，新模块带违规即红，
     下降则打印"可下调基线"的建议值（上调基线必须是一次显式提交，不是脚本自动写）

判据来源是 `mvn -o checkstyle:checkstyle` 产出的 target/checkstyle-result.xml（只出报告不判失败），
因此本脚本负责"判"，Maven 负责"量"。git 不可用、报告缺失、XML 解析失败一律判红（fail-closed）——
"扫不到"绝不能被读成"没问题"。

用法：
  mvn -o checkstyle:checkstyle                    # 先量（全仓，不失败）
  python3 data/scripts/check-checkstyle-delta.py --self-test
  python3 data/scripts/check-checkstyle-delta.py --base origin/main
  python3 data/scripts/check-checkstyle-delta.py --update-baseline   # 人工复核后刷新基线
退出码非 0 表示存在缺陷。
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
from datetime import date
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
BASELINE_FILE = REPO_ROOT / "data" / "checkstyle-baseline.json"

# 本机换行符噪声：Windows 工作区在 core.autocrlf=true 下会把 LF 检出为 CRLF，
# NewlineAtEndOfFile 因此报红，而入库 blob 是干净的 LF（由 check-file-encoding.py E3 负责判）。
# 这两类规则不计入棘轮与增量，只在汇总里作为提示输出。
PLATFORM_NOISE_CHECKS = frozenset({"NewlineAtEndOfFile"})

# 排除目录：backup_logs/ 是整份源码副本（已入库，属 B1-9 仓库卫生项），不参与门禁
EXCLUDED_DIR_PARTS = ("data/scripts/backup_logs",)


class GateError(RuntimeError):
    """门禁前提不成立（git 不可用、报告缺失、基线损坏）。"""


def check_name_of(source_attr: str) -> str:
    """com.puppycrawl.tools.checkstyle.checks.imports.CustomImportOrderCheck -> CustomImportOrder"""
    leaf = (source_attr or "").rsplit(".", 1)[-1]
    return leaf[: -len("Check")] if leaf.endswith("Check") else leaf


def to_repo_rel(root: Path, raw_path: str) -> str:
    """把 XML 里的 file@name（通常是绝对路径）归一成仓库相对 posix 路径。"""
    candidate = Path(raw_path)
    try:
        rel = os.path.relpath(str(candidate), str(root))
    except ValueError:  # 跨盘符（Windows 绝对路径 vs 不同盘 root）
        rel = raw_path
    if rel.startswith(".."):
        rel = raw_path
    return rel.replace("\\", "/")


def collect_violations(root: Path, exclude_noise: bool = True) -> tuple[dict, int, int]:
    """扫描 target/checkstyle-result.xml，返回 (模块统计, 噪声违规数, 报告文件数)。

    模块统计结构：{ "<module dir>": {"errors": int, "by_rule": {rule: int}, "files": {rel: int}} }
    """
    noise = 0
    reports = 0
    modules: dict[str, dict] = {}
    for xml_path in sorted(root.glob("**/target/checkstyle-result.xml")):
        rel_xml = to_repo_rel(root, str(xml_path))
        if any(part in rel_xml for part in EXCLUDED_DIR_PARTS):
            continue
        reports += 1
        module_dir = rel_xml[: -len("/target/checkstyle-result.xml")].rstrip("/") or "."
        try:
            tree = ET.parse(xml_path)
        except ET.ParseError as exc:
            raise GateError(f"checkstyle 报告无法解析：{rel_xml}（{exc}）") from exc
        stat = modules.setdefault(module_dir, {"errors": 0, "by_rule": {}, "files": {}})
        for file_node in tree.getroot().iter("file"):
            file_rel = to_repo_rel(root, file_node.get("name", ""))
            for err in file_node.findall("error"):
                if err.get("severity") != "error":
                    continue  # 与 pom 的 violationSeverity=error 口径一致
                rule = check_name_of(err.get("source", ""))
                if exclude_noise and rule in PLATFORM_NOISE_CHECKS:
                    noise += 1
                    continue
                stat["errors"] += 1
                stat["by_rule"][rule] = stat["by_rule"].get(rule, 0) + 1
                stat["files"][file_rel] = stat["files"].get(file_rel, 0) + 1
    return modules, noise, reports


def load_baseline(path: Path) -> dict:
    if not path.is_file():
        raise GateError(f"基线文件缺失：{path} —— 用 --update-baseline 生成一次")
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise GateError(f"基线文件不可用：{path}（{exc}）") from exc
    if not isinstance(data, dict) or not isinstance(data.get("modules"), dict):
        raise GateError(f"基线结构不合法：{path} 需含 modules 对象")
    return data


def changed_java_files(root: Path, base_ref: str) -> set[str]:
    """取 base...HEAD、HEAD 与工作区两段、以及未跟踪新文件的并集，保证 C1 覆盖"刚写还没 add"的代码。"""
    files: set[str] = set()
    for args in (
        ["diff", "--name-only", "-z", f"{base_ref}...HEAD"],
        ["diff", "--name-only", "-z", "HEAD"],
        ["ls-files", "--others", "--exclude-standard", "-z"],
    ):
        files |= {p for p in run_git(root, args)}
    return {f for f in files if f.endswith(".java")}


def run_git(root: Path, args: list[str]) -> list[str]:
    """执行 git，非零退出即 fail-closed。空输出按 NUL 分隔解析。"""
    proc = subprocess.run(
        ["git", "-C", str(root), *args],
        capture_output=True,
        check=False,
    )
    if proc.returncode != 0:
        raise GateError(
            f"git {' '.join(args)} 失败（退出码 {proc.returncode}）："
            f"{proc.stderr.decode('utf-8', errors='replace').strip()}"
        )
    out = proc.stdout.decode("utf-8", errors="replace")
    return [item for item in out.split("\0") if item]


def evaluate(
    modules: dict, baseline: dict, changed: set[str]
) -> tuple[list[str], list[str]]:
    """返回 (判红列表, 建议下调列表)。"""
    errors: list[str] = []
    suggestions: list[str] = []
    base_modules: dict = baseline["modules"]

    # C1 增量红线：改动文件 0 违规
    for module_dir, stat in sorted(modules.items()):
        for file_rel, count in sorted(stat["files"].items()):
            if file_rel in changed:
                errors.append(f"C1 增量违规：{file_rel}（{count} 条，模块 {module_dir}）")

    # C2 存量棘轮
    for module_dir, stat in sorted(modules.items()):
        entry = base_modules.get(module_dir)
        if entry is None:
            if stat["errors"]:
                errors.append(
                    f"C2 新模块带违规：{module_dir} 有 {stat['errors']} 条，"
                    "基线未登记（要么清零，要么显式评审后写入基线）"
                )
            continue
        allowed = int(entry.get("errors", 0))
        if stat["errors"] > allowed:
            top = ", ".join(
                f"{r}×{c}" for r, c in sorted(stat["by_rule"].items(), key=lambda x: -x[1])[:3]
            )
            errors.append(
                f"C2 存量上涨：{module_dir} {allowed} → {stat['errors']} 条（主要来自 {top}）"
            )
        elif stat["errors"] < allowed:
            suggestions.append(f"C2 可下调棘轮：{module_dir} {allowed} → {stat['errors']}")

    for module_dir in sorted(set(base_modules) - set(modules)):
        if int(base_modules[module_dir].get("errors", 0)) > 0:
            errors.append(
                f"C0 无法核验：基线登记的 {module_dir} 本次没有 checkstyle 报告"
                "（模块被删？还是没跑 mvn -o checkstyle:checkstyle？）"
            )
    return errors, suggestions


def write_baseline(path: Path, modules: dict, noise: int, total: int) -> None:
    payload = {
        "generated_at": date.today().isoformat(),
        "tool": "mvn -o checkstyle:checkstyle + data/scripts/check-checkstyle-delta.py",
        "note": (
            "error 级违规存量基线；口径=只计 pom violationSeverity=error 且排除 "
            f"{sorted(PLATFORM_NOISE_CHECKS)}（本机 CRLF 噪声，由 check-file-encoding.py E3 管入库换行符）。"
            "上调本文件必须随一次显式评审提交。"
        ),
        "excluded_checks": sorted(PLATFORM_NOISE_CHECKS),
        "total_errors": total,
        "platform_noise_errors": noise,
        "modules": {
            name: {"errors": stat["errors"], "by_rule": stat["by_rule"]}
            for name, stat in sorted(modules.items())
            if stat["errors"]
        },
    }
    path.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
        newline="\n",
    )


def fixture_xml(cases: list[tuple[str, int, str, str]]) -> str:
    """构造 checkstyle-result.xml 夹具：(文件名, 行号, severity, source)。"""
    parts = ["<?xml version='1.0' encoding='UTF-8'?>", "<checkstyle version='9.3'>"]
    for name, line, severity, source in cases:
        parts.append(f"  <file name='{name}'>")
        parts.append(
            f"    <error line='{line}' severity='{severity}' message='m' source='{source}'/>"
        )
        parts.append("  </file>")
    parts.append("</checkstyle>")
    return "\n".join(parts)


def make_module(root: Path, module: str, cases: list[tuple[str, int, str, str]]) -> None:
    target = root / module / "target"
    target.mkdir(parents=True, exist_ok=True)
    abs_names = []
    for name, line, severity, source in cases:
        file_path = root / module / name
        file_path.parent.mkdir(parents=True, exist_ok=True)
        file_path.write_text("// fixture\n", encoding="utf-8")
        abs_names.append((str(file_path), line, severity, source))
    (target / "checkstyle-result.xml").write_text(fixture_xml(abs_names), encoding="utf-8")


def self_test() -> int:
    failures: list[str] = []
    passed = 0

    def case(name: str, ok: bool) -> None:
        nonlocal passed
        if ok:
            passed += 1
        else:
            failures.append(name)

    imp = "com.puppycrawl.tools.checkstyle.checks.imports.CustomImportOrderCheck"
    magic = "com.puppycrawl.tools.checkstyle.checks.sizes.MagicNumberCheck"
    eol = "com.puppycrawl.tools.checkstyle.checks.NewlineAtEndOfFileCheck"
    warn = "com.puppycrawl.tools.checkstyle.checks.sizes.LineLengthCheck"

    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        make_module(
            root,
            "ydsz-demo/ydsz-demo-server",
            [("src/main/java/A.java", 3, "error", imp), ("src/main/java/B.java", 9, "error", magic)],
        )
        make_module(root, "ydsz-demo/ydsz-demo-web", [("src/main/java/C.java", 1, "error", imp)])
        make_module(
            root,
            "ydsz-noise/ydsz-noise-server",
            [("src/main/java/D.java", 1, "error", eol)],
        )
        make_module(
            root,
            "ydsz-warn/ydsz-warn-server",
            [("src/main/java/E.java", 1, "warning", warn)],
        )
        make_module(root, "ydsz-clean/ydsz-clean-server", [])

        modules, noise, reports = collect_violations(root)
        case("扫描到 5 份报告 + 1 条噪声", reports == 5 and noise == 1)
        case("换行符噪声不计入违规但单独计数",
             modules["ydsz-noise/ydsz-noise-server"]["errors"] == 0 and noise == 1)
        case("warning 级不计入", modules["ydsz-warn/ydsz-warn-server"]["errors"] == 0)
        case("按模块归集", modules["ydsz-demo/ydsz-demo-server"]["errors"] == 2)
        case("规则名去 Check 后缀", modules["ydsz-demo/ydsz-demo-server"]["by_rule"]["CustomImportOrder"] == 1)

        baseline = {
            "modules": {
                "ydsz-demo/ydsz-demo-server": {"errors": 2},
                "ydsz-demo/ydsz-demo-web": {"errors": 5},
                "ydsz-noise/ydsz-noise-server": {"errors": 0},
            }
        }

        errs, sugg = evaluate(modules, baseline, set())
        case("存量持平不判红", not any("demo-server" in e for e in errs))
        case("存量下降给出下调建议", any("demo-web 5 → 1" in s for s in sugg))

        # C1：改动文件命中违规 → 红；未改动 → 不拦
        changed = {"ydsz-demo/ydsz-demo-server/src/main/java/A.java"}
        errs1, _ = evaluate(modules, baseline, changed)
        case("C1 改动文件违规即红", any(e.startswith("C1") and "A.java" in e for e in errs1))
        changed_clean = {"ydsz-demo/ydsz-demo-server/src/main/java/Z.java"}
        errs2, _ = evaluate(modules, baseline, changed_clean)
        case("C1 改动文件干净不判红", not any(e.startswith("C1") for e in errs2))

        # C2：上涨 → 红
        grown = dict(modules)
        grown["ydsz-demo/ydsz-demo-web"] = {"errors": 6, "by_rule": {"MagicNumber": 6}, "files": {}}
        errs3, _ = evaluate(grown, baseline, set())
        case("C2 存量上涨即红", any("demo-web 5 → 6" in e for e in errs3))

        # C2：未登记新模块带违规 → 红
        newmod = dict(modules)
        newmod["ydsz-new/ydsz-new-server"] = {"errors": 3, "by_rule": {"MagicNumber": 3}, "files": {}}
        errs4, _ = evaluate(newmod, baseline, set())
        case("C2 新模块带违规即红", any(e.startswith("C2 新模块") for e in errs4))

        # C0：基线模块本次无报告 → 红（fail-closed）
        missing = {k: v for k, v in modules.items() if k != "ydsz-demo/ydsz-demo-web"}
        errs5, _ = evaluate(missing, baseline, set())
        case("C0 基线模块缺报告即红", any(e.startswith("C0") for e in errs5))

        # 坏 XML → GateError
        bad = root / "ydsz-bad" / "target"
        bad.mkdir(parents=True, exist_ok=True)
        (bad / "checkstyle-result.xml").write_text("<checkstyle", encoding="utf-8")
        try:
            collect_violations(root)
            case("坏 XML fail-closed 抛 GateError", False)
        except GateError:
            case("坏 XML fail-closed 抛 GateError", True)
        (bad / "checkstyle-result.xml").unlink()
        (root / "ydsz-bad" / "target").rmdir()
        (root / "ydsz-bad").rmdir()

        # 基线缺失 / 结构不合法 → GateError
        try:
            load_baseline(root / "data" / "nope.json")
            case("基线缺失 fail-closed", False)
        except GateError:
            case("基线缺失 fail-closed", True)
        (root / "data").mkdir(exist_ok=True)
        (root / "data" / "broken.json").write_text("[]", encoding="utf-8")
        try:
            load_baseline(root / "data" / "broken.json")
            case("基线结构不合法 fail-closed", False)
        except GateError:
            case("基线结构不合法 fail-closed", True)

        # 无任何报告 → 判红（不能把"没跑"读成"没问题"）
        with tempfile.TemporaryDirectory() as empty_root:
            empty_modules, empty_noise, empty_reports = collect_violations(Path(empty_root))
            case("空仓库扫到 0 报告", empty_reports == 0 and not empty_modules)

        # 写基线后可回读，且 total 与棘轮一致
        total = sum(m["errors"] for m in modules.values())
        write_baseline(root / "data" / "cb.json", modules, noise, total)
        reloaded = load_baseline(root / "data" / "cb.json")
        case("基线写读闭环", reloaded["total_errors"] == total and reloaded["modules"])
        case("基线不含零违规模块", "ydsz-clean/ydsz-clean-server" not in reloaded["modules"])
        case("基线记录噪声排除口径", reloaded["excluded_checks"] == ["NewlineAtEndOfFile"])

    print(f"自检结果：{passed} 条通过，{len(failures)} 条未通过")
    for name in failures:
        print(f"  ✗ 断言未通过：{name}", file=sys.stderr)
    if failures:
        print("❌ check-checkstyle-delta 自检失败", file=sys.stderr)
        return 1
    return 0


def main() -> int:
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8", errors="replace")

    parser = argparse.ArgumentParser(description="Checkstyle 存量棘轮 + 增量红线门禁")
    parser.add_argument("--root", default=str(REPO_ROOT), help="仓库根目录")
    parser.add_argument("--base", help="增量比较的基线 ref（如 origin/main）；缺省则跳过 C1")
    parser.add_argument("--self-test", action="store_true", help="运行反例夹具自检")
    parser.add_argument("--update-baseline", action="store_true", help="按本次实测重写存量基线")
    parser.add_argument("--include-eol", action="store_true", help="把换行符类规则也计入（CI/Linux 可用）")
    parser.add_argument("--list-top", type=int, default=0, help="打印违规最多的 N 个规则")
    args = parser.parse_args()

    if args.self_test:
        return self_test()

    root = Path(args.root).resolve()
    modules, noise, reports = collect_violations(
        root, exclude_noise=not args.include_eol
    )
    if reports == 0:
        raise GateError(
            "未找到任何 target/checkstyle-result.xml —— 请先执行 mvn -o checkstyle:checkstyle"
        )
    total = sum(stat["errors"] for stat in modules.values())
    dirty = [m for m, s in modules.items() if s["errors"]]
    print(
        f"实测：{reports} 份报告 / {len(dirty)} 个模块有违规 / error 级 {total} 条"
        + (f" / 已排除换行符噪声 {noise} 条" if noise else "")
    )

    if args.list_top:
        merged: dict[str, int] = {}
        for stat in modules.values():
            for rule, count in stat["by_rule"].items():
                merged[rule] = merged.get(rule, 0) + count
        for rule, count in sorted(merged.items(), key=lambda x: -x[1])[: args.list_top]:
            print(f"  {count:5d}  {rule}")

    changed: set[str] = set()
    if args.base:
        changed = changed_java_files(root, args.base)
        print(f"C1 增量范围：{args.base}...工作区，涉及 {len(changed)} 个 .java 文件")
    else:
        print("⚠️ 未传 --base，本次只执行 C2 存量棘轮，增量红线（C1）未执行")

    if args.update_baseline:
        write_baseline(BASELINE_FILE, modules, noise, total)
        print(f"✅ 已写入存量基线：{BASELINE_FILE.relative_to(REPO_ROOT)}（total={total}）")
        return 0

    baseline = load_baseline(BASELINE_FILE)
    errors, suggestions = evaluate(modules, baseline, changed)
    for line in suggestions:
        print(f"ℹ️ {line}")
    for line in errors:
        print(f"❌ {line}", file=sys.stderr)
    if errors:
        print(f"违规判红 {len(errors)} 条；基线 total={baseline.get('total_errors')}", file=sys.stderr)
        return 1
    print(f"✅ 门禁通过（C1 {'已执行' if changed or args.base else '未执行'}，C2 未回退）")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except GateError as exc:
        print(f"❌ 门禁无法执行：{exc}", file=sys.stderr)
        sys.exit(2)
