#!/usr/bin/env python3
"""文件编码门禁 — 把 CLAUDE.md CODE-001（UTF-8 无 BOM）与 .editorconfig/.gitattributes 的换行符约定变成可执行断言。

历史上 CI 的「编码规范前置校验」步骤调用 data/scripts/fix-file-encoding.py，而该脚本从未存在，
步骤每次都因 "No such file" 失败：CI 从这一步起就是红的，后面的所有门禁形同虚设。
本脚本是它的真身，判断依据取自 git 而非磁盘，避免把开发者机器的换行符状态当成仓库缺陷：

  E1 文件以 EF BB BF 开头（BOM）—— javac 直接报 illegal character，YAML/properties 解析也可能失败
  E2 内容不是合法 UTF-8 —— 中文 Windows 默认 GBK，AI 生成/编辑器另存都可能写坏
  E3 **入库 blob**（index）的换行符不是 LF —— .gitattributes 对 java/xml/properties/yml/yaml/md/json
     声明了 eol=lf，index 出现 CRLF/mixed 说明归一化没生效（工作区 CRLF 只影响本机，不判红）
  W1 文本文件末尾缺换行 —— .editorconfig insert_final_newline=true，属存量待清，只告警不阻断

用法：
  python3 data/scripts/check-file-encoding.py            # 扫描当前仓库
  python3 data/scripts/check-file-encoding.py --self-test  # 反例夹具自检
退出码非 0 表示存在缺陷；git 不可用、仓库无跟踪文件等前提不成立时同样失败（fail-closed）。
"""

from __future__ import annotations

import argparse
import subprocess
import sys
import tempfile
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]

# .gitattributes 中声明为 binary 的扩展名 + 常见二进制产物，不做文本判定
BINARY_SUFFIXES = frozenset(
    {
        ".jar", ".class", ".png", ".jpg", ".jpeg", ".gif", ".ico", ".bmp", ".webp",
        ".woff", ".woff2", ".ttf", ".eot", ".pdf", ".zip", ".gz", ".tar", ".bz2",
        ".7z", ".xlsx", ".xls", ".docx", ".pptx", ".dat", ".jks", ".keystore",
        ".p12", ".pfx", ".so", ".dll", ".exe", ".bin", ".ser",
    }
)

BOM = b"\xef\xbb\xbf"
# index 换行符允许值：LF / none（空文件） / -text（git 判定为二进制）
OK_INDEX_EOL = frozenset({"lf", "none", "-text"})


class GateError(RuntimeError):
    """门禁前提不成立（git 不可用或仓库状态读不出来）。"""


def check_bytes(data: bytes) -> tuple[str, str] | None:
    """返回该字节串违反 E1/E2 的 (编号, 理由)，合法时返回 None。"""
    if data.startswith(BOM):
        return "E1", "UTF-8 BOM（EF BB BF）—— CODE-001 要求 UTF-8 无 BOM"
    if not data:
        return None
    if b"\x00" in data[:4096]:
        return None  # 含 NUL 视为二进制，交由扩展名/git 判定
    try:
        data.decode("utf-8")
    except UnicodeDecodeError as exc:
        return "E2", f"不是合法 UTF-8（第 {exc.start + 1} 字节起：{exc.reason}）—— 疑似 GBK 写入"
    return None


def parse_index_eol(lines: list[str]) -> dict[str, str]:
    """解析 `git ls-files --eol` 输出，取每个文件的 index 换行符。

    行格式：`i/<eol>  w/<eol>  attr/<attr>\\t<path>`，path 可能含空格或中文，只按首个 TAB 切分。
    """
    result: dict[str, str] = {}
    for line in lines:
        if not line.strip():
            continue
        meta, sep, path = line.partition("\t")
        if not sep:
            continue
        fields = meta.split()
        index_field = next((f for f in fields if f.startswith("i/")), None)
        if index_field is None:
            continue
        result[path] = index_field[2:]
    return result


def run_git(root: Path, args: list[str]) -> bytes:
    try:
        proc = subprocess.run(
            ["git", *args], cwd=str(root), capture_output=True, check=False
        )
    except OSError as exc:
        raise GateError(f"无法执行 git {args[0]}：{exc}") from exc
    if proc.returncode != 0:
        raise GateError(
            f"git {args[0]} 失败（退出码 {proc.returncode}）："
            f"{proc.stderr.decode('utf-8', errors='replace').strip()[:200]}"
        )
    return proc.stdout


def tracked_paths(root: Path) -> list[str]:
    out = run_git(root, ["ls-files", "-z"])
    paths = [p.decode("utf-8") for p in out.split(b"\x00") if p]
    if not paths:
        raise GateError(f"{root} 内没有任何被 git 跟踪的文件，门禁无从判断")
    return paths


def scan_files(root: Path, paths: list[str]) -> tuple[list[str], list[str]]:
    """按 E1/E2/E3 与 W1 扫描给定文件，返回 (错误, 告警)。"""
    eol_text = run_git(
        root, ["-c", "core.quotePath=false", "ls-files", "--eol"]
    ).decode("utf-8", errors="replace")
    eol_map = parse_index_eol(eol_text.splitlines())
    errors: list[str] = []
    warnings: list[str] = []
    for rel in paths:
        if Path(rel).suffix.lower() in BINARY_SUFFIXES:
            continue
        blob = root / rel
        if not blob.is_file():
            # 已删除但仍在 index 中的文件：工作区不存在不是缺陷
            continue
        data = blob.read_bytes()
        violation = check_bytes(data)
        if violation:
            code, reason = violation
            errors.append(f"{code} {rel}: {reason}")
        index_eol = eol_map.get(rel)
        if index_eol is None:
            warnings.append(f"W0 {rel}: git 未报告其 index 换行符，请核对 .gitattributes")
        elif index_eol not in OK_INDEX_EOL:
            errors.append(f"E3 {rel}: 入库内容换行符为 {index_eol}，.gitattributes 要求 LF")
        if data and not data.endswith(b"\n"):
            warnings.append(f"W1 {rel}: 文件末尾缺少换行符（.editorconfig insert_final_newline）")
    return errors, warnings


def self_test() -> int:
    passed = 0
    failures: list[str] = []

    def case(name: str, ok: bool) -> None:
        nonlocal passed
        if ok:
            passed += 1
        else:
            failures.append(name)

    clean = "package a;\n".encode()
    case("干净文本不报红", check_bytes(clean) is None)
    case("BOM 报红为 E1", check_bytes(BOM + clean)[0] == "E1")
    case("BOM 理由含 CODE-001", "CODE-001" in check_bytes(BOM + clean)[1])
    case("GBK 内容报红为 E2", check_bytes("中文".encode("gbk"))[0] == "E2")
    case("GBK 理由点明疑似 GBK", "GBK" in check_bytes("中文".encode("gbk"))[1])
    case("空文件放过", check_bytes(b"") is None)
    case("NUL 开头视为二进制", check_bytes(b"\x00\x01abc") is None)

    sample = [
        "i/lf    w/lf    attr/text eol=lf\t.editorconfig",
        "i/none  w/none  attr/text=auto\tdocs/ai-rules/.sync-hash",
        "i/crlf  w/crlf  attr/text eol=lf\told/Windows File.java",
        "i/mixed w/lf    attr/-text\tbin/mixed.png",
        "garbage-line-without-tab",
    ]
    parsed = parse_index_eol(sample)
    case("ls-files --eol 解析出 4 条", len(parsed) == 4)
    case("TAB 前元数据切分正确", parsed.get(".editorconfig") == "lf")
    case("含空格路径完整保留", parsed.get("old/Windows File.java") == "crlf")
    case("空文件记为 none", parsed.get("docs/ai-rules/.sync-hash") == "none")
    case("无 TAB 的异常行忽略", "garbage-line-without-tab" not in parsed)
    case("LF 不算违反", "lf" in OK_INDEX_EOL)
    case("CRLF 算违反", "crlf" not in OK_INDEX_EOL)
    case("mixed 算违反", "mixed" not in OK_INDEX_EOL)
    case("二进制 -text 放过", "-text" in OK_INDEX_EOL)

    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        (root / "A.java").write_bytes(BOM + b"class A {}\n")
        (root / "B.java").write_bytes("网关".encode("gbk"))
        (root / "C.java").write_bytes(b"class C {}\r\n")
        (root / "D.jar").write_bytes(BOM + b"\x00junk")
        (root / "E.java").write_bytes(b"class E {}")
        (root / "F.java").write_bytes(b"")

        real_git = run_git

        def fake_git(_root: Path, args: list[str]) -> bytes:
            if args[-2:] == ["ls-files", "--eol"]:
                return (
                    b"i/lf w/crlf attr/text eol=lf\tA.java\n"
                    b"i/crlf w/crlf attr/text eol=lf\tC.java\n"
                    b"i/lf w/lf attr/text=auto\tB.java\n"
                    b"i/lf w/lf attr/text=auto\tE.java\n"
                    b"i/none w/none attr/text=auto\tF.java\n"
                    b"i/-text w/-text attr/-text\tD.jar\n"
                )
            return real_git(_root, args)

        globals()["run_git"] = fake_git
        try:
            errs, warns = scan_files(
                root, ["A.java", "B.java", "C.java", "D.jar", "E.java", "F.java"]
            )
        finally:
            globals()["run_git"] = real_git
        case("BOM/GBK/CRLF 三条全部判红", len(errs) == 3)
        case("BOM 命中 E1 文案", any(e.startswith("E1 A.java") and "BOM" in e for e in errs))
        case("GBK 命中 E2 文案", any("GBK" in e for e in errs))
        case("index CRLF 命中 E3", any(e.startswith("E3 C.java") for e in errs))
        case("工作区 CRLF 但 index LF 不判红", not any("E3 A.java" in e for e in errs))
        case("二进制扩展名不扫描", not any("D.jar" in e for e in errs + warns))
        case("末尾缺换行降级为告警", any(e.startswith("W1 E.java") for e in warns))
        case("空文件不报缺换行", not any("F.java" in e for e in warns))
        case("错误列表不带 W 前缀", all(e.startswith("E") for e in errs))

        def no_eol_git(_root: Path, args: list[str]) -> bytes:
            if args[-2:] == ["ls-files", "--eol"]:
                return b""
            return real_git(_root, args)

        globals()["run_git"] = no_eol_git
        try:
            _, orphan_warns = scan_files(root, ["E.java"])
        finally:
            globals()["run_git"] = real_git
        case("index 换行符缺失降级为 W0 告警", any(e.startswith("W0 E.java") for e in orphan_warns))

    try:
        with tempfile.TemporaryDirectory() as tmp:
            run_git(Path(tmp), ["bogus-subcommand-for-self-test"])
        case("git 非零退出即 fail-closed", False)
    except GateError:
        case("git 非零退出即 fail-closed", True)

    case("自检同时覆盖正反例与 fail-closed", passed >= 27)

    for name in failures:
        print(f"  ✗ 断言未通过：{name}", file=sys.stderr)
    total = passed + len(failures)
    if failures:
        print(f"❌ check-file-encoding 自检：{passed}/{total} 条断言通过", file=sys.stderr)
        return 1
    print(f"✅ check-file-encoding 自检：{total}/{total} 条断言通过")
    return 0


def main() -> int:
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8", errors="replace")

    parser = argparse.ArgumentParser(description="文件编码与换行符门禁")
    parser.add_argument("--root", default=str(REPO_ROOT), help="仓库根目录（默认脚本所在仓库）")
    parser.add_argument("--self-test", action="store_true", help="运行反例夹具自检")
    args = parser.parse_args()

    if args.self_test:
        return self_test()

    root = Path(args.root).resolve()
    try:
        paths = tracked_paths(root)
        errors, warnings = scan_files(root, paths)
    except GateError as exc:
        print(f"❌ 编码门禁无法建立判断依据：{exc}", file=sys.stderr)
        return 1
    except OSError as exc:
        print(f"❌ 编码门禁读取文件失败：{exc}", file=sys.stderr)
        return 1

    print(f"扫描 {len(paths)} 个 git 跟踪文件（根目录 {root}）")
    for warn in warnings:
        print(f"⚠️  {warn}")
    for err in errors:
        print(f"❌ {err}")
    if errors:
        print(f"❌ 编码门禁失败：{len(errors)} 处违反 CODE-001 / .gitattributes（告警 {len(warnings)} 条）")
        return 1
    print(f"✅ 编码门禁通过：无 BOM、无非法 UTF-8、入库换行符全部 LF（告警 {len(warnings)} 条）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
