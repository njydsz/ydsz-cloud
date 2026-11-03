#!/usr/bin/env python3
"""事务回滚边界门禁。

检查指定范围内写方法上的 @Transactional 是否显式声明 rollbackFor = Exception.class。
只读事务（readOnly = true）无需强制；private/package-private 方法不参与代理调用，跳过。

用法：
  python3 data/scripts/check-transaction-rollback.py --roots ydsz-system,ydsz-userinfo
  python3 data/scripts/check-transaction-rollback.py --self-test
"""

from __future__ import annotations

import argparse
import re
import sys
import tempfile
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
TRANSACTIONAL_RE = re.compile(r"(?<![\w{])@Transactional(?!\w)(?:\s*\(([^)]*)\))?")
METHOD_RE = re.compile(
    r"(?:public\s+)?(?:static\s+)?(?:final\s+)?[\w<>\[\], ?]+\s+(\w+)\s*\([^;{]*\)\s*(?:throws [^{]+)?\s*(?:\{|;)",
    re.MULTILINE,
)


class GateError(RuntimeError):
    """门禁前提不成立。"""


def strip_java_comments(text: str) -> str:
    """移除 Java 注释，避免 Javadoc 中的注解示例被当成真实代码。"""
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.DOTALL)
    return re.sub(r"//[^\n]*", "", text)


def check_file(path: Path, root: Path) -> list[str]:
    try:
        text = path.read_text(encoding="utf-8")
    except (OSError, UnicodeDecodeError) as exc:
        raise GateError(f"无法读取 {path}: {exc}") from exc

    findings: list[str] = []
    relative = path.relative_to(root).as_posix()
    code = strip_java_comments(text)
    for match in TRANSACTIONAL_RE.finditer(code):
        annotation = match.group(1) or ""
        if "readOnly" in annotation and "true" in annotation:
            continue
        if "rollbackFor" in annotation:
            continue

        after = code[match.end():]
        method_match = METHOD_RE.search(after)
        if method_match is None:
            continue
        method_line = code[: match.start()].count("\n") + 1
        findings.append(
            f"{relative}:{method_line}: @Transactional 缺少 rollbackFor = Exception.class"
        )
    return findings


def collect(roots: list[str]) -> list[str]:
    findings: list[str] = []
    checked = 0
    for root_name in roots:
        root = REPO_ROOT / root_name
        if not root.exists():
            raise GateError(f"模块不存在：{root}")
        paths = sorted(root.rglob("*.java"))
        if not paths:
            raise GateError(f"模块中没有 Java 文件：{root}")
        for path in paths:
            if "/target/" in path.as_posix() or "/src/test/" in path.as_posix():
                continue
            checked += 1
            findings.extend(check_file(path, REPO_ROOT))

    print(f"实测：{checked} 个 Java 文件")
    return findings


def self_test() -> int:
    failures: list[str] = []

    def case(name: str, ok: bool) -> None:
        if not ok:
            failures.append(name)

    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        module = root / "ydsz-demo"
        module.mkdir()
        (module / "GoodService.java").write_text(
            "class GoodService {\n"
            "  @Transactional(rollbackFor = Exception.class)\n"
            "  public void save() {}\n"
            "}\n",
            encoding="utf-8",
        )
        (module / "ReadOnlyService.java").write_text(
            "class ReadOnlyService {\n"
            "  @Transactional(readOnly = true)\n"
            "  public void read() {}\n"
            "}\n",
            encoding="utf-8",
        )
        (module / "BadService.java").write_text(
            "class BadService {\n"
            "  @Transactional\n"
            "  public void delete() {}\n"
            "}\n",
            encoding="utf-8",
        )
        bad_findings = check_file(module / "BadService.java", root)
        case("缺少 rollbackFor 被拦截", bool(bad_findings))
        case("只读事务不报错", "ReadOnlyService.java" not in check_file(module / "ReadOnlyService.java", root))
        case("合规事务不报错", "GoodService.java" not in check_file(module / "GoodService.java", root))

    empty_root = Path(tempfile.mkdtemp())
    try:
        collect([empty_root.name])
        case("无 Java 文件 fail-closed", False)
    except GateError:
        case("无 Java 文件 fail-closed", True)

    print(f"自检结果：{'全部通过' if not failures else f'{len(failures)} 条未通过'}")
    for name in failures:
        print(f"  ✗ {name}", file=sys.stderr)
    return 0 if not failures else 1


def main() -> int:
    parser = argparse.ArgumentParser(description="事务回滚边界门禁")
    parser.add_argument("--roots", default="ydsz-system,ydsz-userinfo", help="逗号分隔的模块目录")
    parser.add_argument("--self-test", action="store_true", help="运行反例夹具自检")
    args = parser.parse_args()

    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")
        except AttributeError:
            pass

    if args.self_test:
        return self_test()

    try:
        findings = collect([item.strip() for item in args.roots.split(",") if item.strip()])
    except GateError as exc:
        print(f"❌ {exc}", file=sys.stderr)
        return 2

    if findings:
        print(f"❌ 发现 {len(findings)} 处事务回滚边界问题：", file=sys.stderr)
        for item in findings[:50]:
            print(f"   {item}", file=sys.stderr)
        return 1

    print("✅ 事务回滚边界门禁通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
