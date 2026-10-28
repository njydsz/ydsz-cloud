#!/usr/bin/env python3
"""增量行覆盖率门禁 — 落实《云顶编码规范》§14.7「增量行覆盖率 ≥ 40% 阻断 PR」。

为什么需要它（实测，见报告 B1-2）：根 pom 的 jacoco:check 原先用全局 0.10/0.05 判定整包
BUNDLE，而 21 个可测模块里 20 个达不到 —— 该步骤恒红，等价于"没有覆盖率门禁"。
全局阈值改成每模块棘轮（地板按实测钉死，覆盖率上升后应上调锁定，只可升不可降）后，它只能防"覆盖率倒退"，防不住"新增代码 0 测试"。
本脚本补的正是后者：只看本次 PR 新增的 src/main/java 行，按 jacoco.xml 逐行判定是否被覆盖。

判据：
  D1 增量行覆盖率 = 命中的已覆盖新增行 / 命中的可执行新增行 ≥ --fail-under（默认 40%）
  D2 分母口径：只有 jacoco.xml 里出现 <line nr> 的行才算"可执行"；
     注释、import、空行、纯接口签名不进入分母（否则一行注释就能稀释指标）
  D3 fail-closed：改动涉及的模块若没有 target/site/jacoco/jacoco.xml，直接判错退出
     —— "没跑测试"绝不能被读成"覆盖率 100%"

数据源是 `mvn -o verify` 产出的 target/site/jacoco/jacoco.xml（逐行 ci/mi 计数），
因此本脚本负责"判"，Maven 负责"量"。

区间口径（易踩，实测过才写成条款）：
  R1 比较范围 = `git merge-base <base> HEAD` → **工作树**，不是 → HEAD。AGENTS.md 把本脚本
     列为"提交前自查"，若区间终点取 HEAD，未提交的改动就一行也量不到，脚本会打印
     "无 src/main/java 新增行，不适用"并退 0 —— 恒绿等于无门禁（见报告 B1-2 增补）。
     CI runner 的工作树等于 HEAD，两种取法在 CI 里等价。
  R2 未跟踪（untracked）的 src/main/java 文件**直接判红**：它们不在 `git diff` 输出里，
     会让分母凭空缩小、把没测的新代码算成 100%，需先 `git add` 再复跑。
  R3 新增行**全部**为 import/package/注释/空行/单括号的文件免核验并打印原因：这类行不产生
     字节码，jacoco 报告无从告知更多信息；注解行与字段声明行不在免核验之列。

用法：
  mvn -o verify -DskipTests=false                 # 先量（产出 jacoco.xml）
  python3 data/scripts/check-diff-coverage.py --self-test
  python3 data/scripts/check-diff-coverage.py --base origin/main
  python3 data/scripts/check-diff-coverage.py --base origin/main --fail-under 60
退出码：0 通过；1 未达阈值/存在未跟踪生产源码；2 门禁前提不成立（git/报告缺失）。
读取退出码不要隔着管道（`… | tail` 拿到的是 tail 的状态），用 `${PIPESTATUS[0]}`。
"""

from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_FAIL_UNDER = 40.0

# 只度量生产代码：测试自身、生成代码目录不参与增量分母
MAIN_SOURCE_MARKER = "/src/main/java/"
EXCLUDED_PATH_PARTS = ("/target/", "/src/main/resources/", "/generated-sources/")

HUNK_RE = re.compile(r"^@@ -\d+(?:,\d+)? \+(\d+)(?:,(\d+))? @@")
DIFF_GIT_NOISE = (
    "diff --git",
    "index ",
    "--- ",
    "+++ ",
    "@@ ",
    "\\ ",
    "new file mode",
    "deleted file mode",
    "rename ",
    "similarity ",
    "Mode change",
)


class GateError(RuntimeError):
    """门禁前提不成立（git 不可用、覆盖率报告缺失、XML 解析失败）。"""


# ---------------------------------------------------------------- 新增行解析


def parse_added_lines(diff_text: str) -> dict[str, list[int]]:
    """解析 unified diff（--unified=0），返回 {新文件路径: [新增行号, ...]}。"""
    return {
        path: [nr for nr, _ in pairs]
        for path, pairs in parse_added_lines_with_text(diff_text).items()
    }


# 永不成字节码的行：空行、注释、import/package 声明、单独的块闭合符。
# 判据刻意保守（不含注解行、字段声明行）——宁可多要求一次报告，也不放过一行真语句。
NEVER_EXECUTABLE_EXACT = {"{", "}"}


def is_never_executable_line(text: str) -> bool:
    """仅凭源码文本即可断定该行不产生字节码（无需 jacoco 报告佐证）。"""
    stripped = text.strip()
    if not stripped or stripped in NEVER_EXECUTABLE_EXACT:
        return True
    if stripped.startswith(("import ", "package ")):
        return True
    return stripped.startswith(("//", "/*", "*"))


def split_never_executable(
    added_with_text: dict[str, list[tuple[int, str]]]
) -> tuple[dict[str, list[int]], list[str]]:
    """把"新增行全是非可执行行"的文件从分母里摘出来。

    这类文件不需要 jacoco 报告：报告能告诉我们的（哪些行可执行）已由源码文本否定。
    没有这一步，一次纯 import 分组修正就会在零测试模块里触发 D3"无法核验"，
    逼出为了变绿而写的空跑测试。
    """
    measurable: dict[str, list[int]] = {}
    skipped: list[str] = []
    for path, pairs in added_with_text.items():
        if pairs and all(is_never_executable_line(text) for _, text in pairs):
            skipped.append(path)
        else:
            measurable[path] = [nr for nr, _ in pairs]
    return measurable, skipped


def parse_added_lines_with_text(diff_text: str) -> dict[str, list[tuple[int, str]]]:
    """解析 unified diff（--unified=0），返回 {新文件路径: [(新增行号, 行文本), ...]}。

    只统计 '+' 行；'-' 与上下文行忽略。删除文件（+++ /dev/null）整体跳过。
    """
    added: dict[str, list[tuple[int, str]]] = {}
    current: str | None = None
    new_line = 0
    deleted = False
    for raw in diff_text.splitlines():
        if raw.startswith("diff --git "):
            current, deleted = None, False
            continue
        if raw.startswith("+++ "):
            target = raw[4:].strip()
            if target == "/dev/null":
                deleted = True
                current = None
            else:
                # "b/path" → "path"；带时间戳的格式取空格后第一段
                path = target.split("\t", 1)[0]
                if path.startswith("b/"):
                    path = path[2:]
                deleted = False
                current = path
            continue
        if current is None or deleted:
            continue
        match = HUNK_RE.match(raw)
        if match:
            new_line = int(match.group(1))
            continue
        if raw.startswith("+"):
            added.setdefault(current, []).append((new_line, raw[1:]))
            new_line += 1
        elif raw.startswith("-"):
            continue
        elif raw.startswith(" "):
            new_line += 1
        # 其余（"\ No newline…"、"\r" 噪声）不动行号
    return added


def is_main_java_source(path: str) -> bool:
    """只保留 src/main/java 下的 .java；排除 target/ 与生成目录。"""
    posix = path.replace("\\", "/")
    if not posix.endswith(".java"):
        return False
    if MAIN_SOURCE_MARKER not in f"/{posix}":
        return False
    return not any(part in posix for part in EXCLUDED_PATH_PARTS)


def module_of(path: str) -> str:
    """ydsz-a/ydsz-a-server/src/main/java/... → ydsz-a/ydsz-a-server"""
    posix = path.replace("\\", "/")
    idx = posix.find(MAIN_SOURCE_MARKER)
    return posix[:idx] if idx > 0 else "."


def source_suffix_of(path: str) -> str:
    """ydsz-a/ydsz-a-server/src/main/java/com/x/Y.java → com/x/Y.java"""
    posix = path.replace("\\", "/")
    idx = posix.find(MAIN_SOURCE_MARKER)
    return posix[idx + len(MAIN_SOURCE_MARKER) :] if idx > 0 else posix


# ---------------------------------------------------------------- 覆盖率索引


def load_line_coverage(xml_path: Path) -> dict[str, dict[int, bool]]:
    """jacoco.xml → { "com/x/Y.java": {行号: 是否覆盖} }。只收录出现过的行。"""
    try:
        tree = ET.parse(xml_path)
    except ET.ParseError as exc:
        raise GateError(f"覆盖率报告无法解析：{xml_path}（{exc}）") from exc
    index: dict[str, dict[int, bool]] = {}
    for package in tree.getroot().findall("package"):
        pkg = (package.get("name") or "").strip("/")
        for source in package.findall("sourcefile"):
            name = source.get("name") or ""
            key = f"{pkg}/{name}" if pkg else name
            lines: dict[int, bool] = {}
            for line in source.findall("line"):
                nr = line.get("nr")
                if not nr:
                    continue
                covered = int(line.get("ci") or 0) > 0
                lines[int(nr)] = covered
            index[key] = lines
    return index


class CoverageStore:
    """按模块缓存 jacoco.xml；模块缺报告时留痕，由调用方 fail-closed。"""

    def __init__(self, root: Path):
        self.root = root
        self._cache: dict[str, dict[str, dict[int, bool]]] = {}
        self._missing: set[str] = set()

    def lines_for(self, path: str) -> dict[int, bool] | None:
        module = module_of(path)
        if module not in self._cache and module not in self._missing:
            xml_path = self.root / module / "target" / "site" / "jacoco" / "jacoco.xml"
            if xml_path.is_file():
                self._cache[module] = load_line_coverage(xml_path)
            else:
                self._missing.add(module)
                return None
        if module in self._missing:
            return None
        return self._cache[module].get(source_suffix_of(path), {})


# ---------------------------------------------------------------- 判据


def evaluate(store: CoverageStore, added: dict[str, list[int]]) -> dict:
    """把新增行映射到 jacoco 行覆盖，产出明细与全局计数。"""
    files: dict[str, dict] = {}
    unmeasurable: list[str] = []
    unreported: list[str] = []
    total = covered = 0
    for path, lines in sorted(added.items()):
        if not lines:
            continue
        per_file = store.lines_for(path)
        if per_file is None:
            unmeasurable.append(path)
            continue
        if not per_file:
            unreported.append(path)
            continue
        executable = [ln for ln in lines if ln in per_file]
        hit = [ln for ln in executable if per_file[ln]]
        entry = {
            "added": len(lines),
            "executable": len(executable),
            "covered": len(hit),
            "missed_lines": [ln for ln in executable if not per_file[ln]],
        }
        files[path] = entry
        total += entry["executable"]
        covered += entry["covered"]
    ratio = (covered / total * 100.0) if total else None
    return {
        "files": files,
        "total": total,
        "covered": covered,
        "ratio": ratio,
        "unmeasurable": unmeasurable,
        "unreported": unreported,
    }


def enforce(result: dict, fail_under: float) -> list[str]:
    """D1 + D3：返回判红原因（空列表 = 通过）。"""
    errors: list[str] = []
    if result["unmeasurable"]:
        modules = sorted({module_of(p) for p in result["unmeasurable"]})
        errors.append(
            "D3 无法核验：以下模块缺少 target/site/jacoco/jacoco.xml —— "
            f"先执行 mvn -o verify（模块：{', '.join(modules)}）；"
            f"涉及文件：{', '.join(result['unmeasurable'][:5])}"
        )
    if result["ratio"] is None:
        return errors  # 没有可执行新增行：本 PR 不涉及生产代码逻辑
    if result["ratio"] < fail_under:
        worst = sorted(
            (
                (f, s)
                for f, s in result["files"].items()
                if s["executable"] and s["covered"] == 0
            ),
            key=lambda x: -x[1]["executable"],
        )
        errors.append(
            f"D1 增量行覆盖率 {result['ratio']:.1f}% "
            f"（{result['covered']}/{result['total']} 行）< 阈值 {fail_under:g}%"
        )
        for file_path, stat in worst[:10]:
            preview = ", ".join(str(n) for n in stat["missed_lines"][:12])
            errors.append(
                f"   0 覆盖：{file_path} 新增 {stat['executable']} 行未测（行 {preview}）"
            )
    return errors


def diff_range_label(base_ref: str) -> str:
    """门禁输出里的区间标签：实测范围是 merge-base → 工作区，不是 → HEAD。"""
    return f"{base_ref}...工作区"


# ---------------------------------------------------------------- git


def run_git(root: Path, args: list[str]) -> str:
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
    return proc.stdout.decode("utf-8", errors="replace")


def select_main_java(paths: list[str]) -> list[str]:
    """从路径清单里挑出 src/main/java 文件（Windows 反斜杠先归一）。"""
    return sorted({p for p in (x.replace("\\", "/").strip() for x in paths)
                   if p and is_main_java_source(p)})


def report_untracked_main_java(root: Path) -> list[str]:
    """列出未跟踪的 src/main/java 文件（这些行永远进不了 diff，必须显式拦下而不是静默漏算）。"""
    try:
        proc = subprocess.run(
            ["git", "-C", str(root), "ls-files", "--others", "--exclude-standard", "--", "*.java"],
            capture_output=True,
            check=True,
            text=True,
            encoding="utf-8",
            errors="replace",
        )
    except (subprocess.CalledProcessError, OSError):
        return []
    return select_main_java(proc.stdout.splitlines())


def diff_git_args(merge_base: str) -> list[str]:
    """构造 merge-base → 工作区 的 git diff 参数。

    <p>口径要点：末尾**不能**出现 `HEAD`。带 `HEAD` 时只看已提交内容，而 AGENTS.md 的
    "AI 必做清单"把本脚本列为**提交前自查**——未提交的改动恰好是本脚本要量的东西；
    实测后果是 `--base HEAD` 求出一个空区间，打印"无新增行，门禁不适用"并退出 0，
    把"1 个文件 6 行新代码没测"读成绿灯。不带第二个提交号时 git 直接比到工作树，
    CI（runner 工作树 == HEAD）行为不变。
    """
    return [
        "-c",
        "core.quotepath=false",
        "diff",
        "--unified=0",
        "--no-color",
        "-M",
        merge_base,
        "--",
        "*.java",
    ]


def diff_against(root: Path, base_ref: str) -> tuple[dict[str, list[int]], list[str]]:
    """merge-base → 工作区（含未提交改动）的新增行；口径差异见 diff_git_args。

    返回 (需报告核验的文件, 新增行全为非可执行行而免核验的文件)。
    """
    run_git(root, ["rev-parse", "--verify", f"{base_ref}^{{commit}}"])
    merge_base = run_git(root, ["merge-base", base_ref, "HEAD"]).strip()
    if not merge_base:
        raise GateError(f"无法求 merge-base：{base_ref} 与 HEAD 没有公共提交（浅克隆？需 fetch-depth: 0）")
    raw = run_git(root, diff_git_args(merge_base))
    with_text = {
        path: pairs
        for path, pairs in parse_added_lines_with_text(raw).items()
        if is_main_java_source(path)
    }
    return split_never_executable(with_text)


# ---------------------------------------------------------------- 自检


def write_jacoco_xml(path: Path, entries: list[tuple[str, str, list[tuple[int, int, int]]]]) -> None:
    """夹具：entries = [(包路径, 源文件名, [(行号, missed insn, covered insn), ...])]。"""
    parts = ['<?xml version="1.0" encoding="UTF-8"?>', '<report name="fixture">']
    for pkg, file_name, lines in entries:
        parts.append(f'  <package name="{pkg}">')
        parts.append(f'    <sourcefile name="{file_name}">')
        for nr, mi, ci in lines:
            parts.append(f'      <line nr="{nr}" mi="{mi}" ci="{ci}" mb="0" cb="0"/>')
        parts.append("    </sourcefile>")
        parts.append("  </package>")
    parts.append("</report>")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(parts) + "\n", encoding="utf-8")


def self_test() -> int:
    failures: list[str] = []
    passed = 0

    def case(name: str, ok: bool) -> None:
        nonlocal passed
        if ok:
            passed += 1
        else:
            failures.append(name)

    # ---- diff 解析
    diff = "\n".join(
        [
            "diff --git a/m/src/main/java/com/x/A.java b/m/src/main/java/com/x/A.java",
            "index 111..222 100644",
            "--- a/m/src/main/java/com/x/A.java",
            "+++ b/m/src/main/java/com/x/A.java",
            "@@ -3,0 +4,3 @@",
            "+line4",
            "+line5",
            "+line6",
            "@@ -10,1 +12,1 @@",
            "-old10",
            "+new12",
            "diff --git a/m/src/main/java/com/x/B.java b/m/src/test/java/com/x/BTest.java",
            "deleted file mode 100644",
            "--- a/m/src/main/java/com/x/B.java",
            "+++ /dev/null",
            "@@ -1,2 +0,0 @@",
            "-gone1",
            "-gone2",
            "diff --git a/m/src/main/java/com/x/C.java b/m/src/main/java/com/x/C.java",
            "new file mode 100644",
            "--- /dev/null",
            "+++ b/m/src/main/java/com/x/C.java",
            "@@ -0,0 +1,2 @@",
            "+c1",
            "\\ No newline at end of file",
            "+c2",
        ]
    )
    parsed = parse_added_lines(diff)
    case("hunk 头驱动行号（含 -0,0 +N 纯新增）", parsed["m/src/main/java/com/x/A.java"] == [4, 5, 6, 12])
    case("删除文件不计入", "m/src/main/java/com/x/B.java" not in parsed)
    case("反斜杠噪声不移动行号", parsed["m/src/main/java/com/x/C.java"] == [1, 2])
    case("只保留 src/main/java", [p for p in parsed if not is_main_java_source(p)] == [])
    case("模块与包内路径可还原", module_of("m/src/main/java/com/x/A.java") == "m"
         and source_suffix_of("m/src/main/java/com/x/A.java") == "com/x/A.java")

    # ---- 非可执行行免核验（不能把"没法测的 import"读成"没测的业务代码"，也不能反过来）
    with_text = parse_added_lines_with_text(diff)
    case("带文本解析出行号与内容",
         with_text["m/src/main/java/com/x/A.java"][0] == (4, "line4"))
    case("import/注释/空行/单括号判为非可执行",
         all(is_never_executable_line(t) for t in
             ["import java.util.List;", "package com.x;", "", "   ", "// c", "/* c */",
              " * javadoc", "}"]))
    case("真实语句不得免核验（反例）",
         all(not is_never_executable_line(t) for t in
             ["if (x) {", "int a = 1;", "return foo;", "@Bean", "private String s;",
              "super.standaloneSetup(c);"]))
    exempt_only = {"m/src/main/java/com/x/Only.java": [(1, "import a.B;"), (2, "  ")]}
    measurable, skipped = split_never_executable(exempt_only)
    case("整文件仅 import 变更免报告", measurable == {} and skipped == ["m/src/main/java/com/x/Only.java"])
    mixed = {
        "m/src/main/java/com/x/Mixed.java": [(1, "import a.B;"), (2, "int c = 0;")],
        **exempt_only,
    }
    measurable2, skipped2 = split_never_executable(mixed)
    case("混入一行真实语句即恢复核验（反例）",
         list(measurable2) == ["m/src/main/java/com/x/Mixed.java"]
         and skipped2 == ["m/src/main/java/com/x/Only.java"])

    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        xml_a = root / "m" / "target" / "site" / "jacoco" / "jacoco.xml"
        write_jacoco_xml(
            xml_a,
            [("com/x", "A.java", [(4, 0, 3), (5, 2, 0), (6, 1, 0), (12, 0, 5)])],
        )
        store = CoverageStore(root)

        added = {"m/src/main/java/com/x/A.java": [1, 2, 3, 4, 5, 6, 12]}
        res = evaluate(store, added)
        case("注释/空行不进分母（1..3 未出现在报告）", res["total"] == 4 and res["covered"] == 2)
        case("比例按可执行行计", abs(res["ratio"] - 50.0) < 1e-9)
        case("未覆盖行号透出", res["files"]["m/src/main/java/com/x/A.java"]["missed_lines"] == [5, 6])
        case("50% 通过 40% 阈值", not enforce(res, 40.0))
        case("60% 阈值拦下同一份改动", bool(enforce(res, 60.0)))

        # 全无覆盖 → 判红，且列出 0 覆盖文件
        res_zero = evaluate(
            CoverageStore(root), {"m/src/main/java/com/x/A.java": [5, 6]}
        )
        errs = enforce(res_zero, 40.0)
        case("0% 增量覆盖判红", any(e.startswith("D1") for e in errs)
             and any("0 覆盖" in e for e in errs))

        # 模块缺报告 → D3 fail-closed（而不是当成 100%）
        res_missing = evaluate(
            CoverageStore(root), {"other/src/main/java/com/x/A.java": [5, 6]}
        )
        case("缺报告文件计入 unmeasurable", res_missing["unmeasurable"] == ["other/src/main/java/com/x/A.java"])
        case("D3 缺报告即判错", any(e.startswith("D3") for e in enforce(res_missing, 40.0)))

        # sourcefile 不在报告里（纯接口/注解）→ 不进分母，也不判红
        res_iface = evaluate(
            CoverageStore(root), {"m/src/main/java/com/x/Api.java": [3, 4]}
        )
        case("报告无此文件时不稀释也不判红", res_iface["ratio"] is None
             and res_iface["unreported"] == ["m/src/main/java/com/x/Api.java"]
             and not enforce(res_iface, 40.0))

        # 无新增行（纯删除 PR）→ 直接通过
        case("纯删除 PR 无可度量行", evaluate(store, {}).get("ratio") is None
             and not enforce(evaluate(store, {}), 40.0))

        # 坏 XML → GateError
        bad = root / "bad" / "target" / "site" / "jacoco" / "jacoco.xml"
        bad.parent.mkdir(parents=True, exist_ok=True)
        bad.write_text("<report>", encoding="utf-8")
        try:
            evaluate(CoverageStore(root), {"bad/src/main/java/com/x/A.java": [1]})
            case("坏 XML fail-closed 抛 GateError", False)
        except GateError:
            case("坏 XML fail-closed 抛 GateError", True)

        # 区间口径：必须比到工作区（提交前自查量的就是未提交改动）
        args_list = diff_git_args("abcdef")
        case("diff 区间到工作区而非 HEAD（否则未提交改动恒被读成绿灯）",
             "HEAD" not in args_list and args_list[-3:] == ["abcdef", "--", "*.java"])
        case("未跟踪清单只挑 src/main/java",
             select_main_java(
                 [
                     "m\\src\\main\\java\\com\\x\\New.java",
                     "m/src/main/java/com/x/A.java",
                     "m/src/test/java/com/x/ATest.java",
                     "m/src/main/resources/mapper/A.xml",
                     "   ",
                 ]
             )
             == ["m/src/main/java/com/x/A.java", "m/src/main/java/com/x/New.java"])

    print(f"自检结果：{passed} 条通过，{len(failures)} 条未通过")
    for name in failures:
        print(f"  ✗ 断言未通过：{name}", file=sys.stderr)
    if failures:
        print("❌ check-diff-coverage 自检失败", file=sys.stderr)
        return 1
    return 0


# ---------------------------------------------------------------- main


def main() -> int:
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8", errors="replace")

    parser = argparse.ArgumentParser(description="增量行覆盖率门禁（云顶编码规范 §14.7）")
    parser.add_argument("--root", default=str(REPO_ROOT), help="仓库根目录")
    parser.add_argument(
        "--base",
        default=os.environ.get("GITHUB_BASE_REF") or "origin/main",
        help="比较基线 ref（缺省取 $GITHUB_BASE_REF，再退到 origin/main）；"
             "区间为该 ref 与 HEAD 的 merge-base → 工作树，含未提交改动",
    )
    parser.add_argument(
        "--fail-under",
        type=float,
        default=DEFAULT_FAIL_UNDER,
        help=f"增量行覆盖率下限（%%，默认 {DEFAULT_FAIL_UNDER:g}）",
    )
    parser.add_argument("--self-test", action="store_true", help="运行反例夹具自检")
    parser.add_argument("--show-files", action="store_true", help="打印每个文件的可执行新增行明细")
    args = parser.parse_args()

    if args.self_test:
        return self_test()

    root = Path(args.root).resolve()
    base = args.base
    if os.environ.get("GITHUB_BASE_REF") and not base.startswith("origin/"):
        base = f"origin/{base}"
    untracked = report_untracked_main_java(root)
    if untracked:
        # 未跟踪文件不在 git diff 输出里 → 这些新增行会整批消失于分母，
        # 结果是"没测的新代码"被算成 100%。宁可判红让人 git add 后复跑。
        print(
            f"❌ {len(untracked)} 个未跟踪的 src/main/java 文件进不了增量分母（请先 git add 或删除）：",
            file=sys.stderr,
        )
        for path in untracked[:10]:
            print(f"     {path}", file=sys.stderr)
        if len(untracked) > 10:
            print(f"     …另 {len(untracked) - 10} 个", file=sys.stderr)
        return 1

    added, exempt = diff_against(root, base)
    for path in exempt:
        print(f"ℹ️ 免核验（新增行均为 import/package/注释/空行）：{path}")
    if not added:
        print(f"✅ {diff_range_label(base)} 无需要覆盖率核验的 src/main/java 新增行")
        return 0

    store = CoverageStore(root)
    result = evaluate(store, added)
    for module in sorted({module_of(p) for p in result["unmeasurable"]}):
        print(f"   缺 jacoco.xml：{module}")
    if result["unreported"]:
        print(f"ℹ️ 报告未收录 {len(result['unreported'])} 个文件（接口/注解/无字节码，不计入分母）")
    if args.show_files:
        for file_path, stat in sorted(result["files"].items()):
            print(
                f"   {file_path}: 新增 {stat['added']} 行 / 可执行 {stat['executable']} / "
                f"已覆盖 {stat['covered']}"
            )

    if result["ratio"] is None and not result["unmeasurable"]:
        print(
            f"✅ {diff_range_label(base)}：{sum(len(v) for v in added.values())} "
            f"个新增行均非可执行行，无分母"
        )
        return 0

    ratio_text = "n/a" if result["ratio"] is None else f"{result['ratio']:.1f}%"
    print(
        f"增量行覆盖率：{ratio_text}（{result['covered']}/{result['total']} 行可执行新增行被覆盖），"
        f"阈值 {args.fail_under:g}%"
    )

    errors = enforce(result, args.fail_under)
    for line in errors:
        print(f"❌ {line}", file=sys.stderr)
    if errors:
        print(
            "补测指引：《云顶编码规范》§14.1.1 按模块列出增量/全量目标；"
            "只补新增行的分支即可，不要求回填历史。",
            file=sys.stderr,
        )
        return 1
    print("✅ 增量行覆盖率门禁通过")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except GateError as exc:
        print(f"❌ 门禁无法执行：{exc}", file=sys.stderr)
        sys.exit(2)
