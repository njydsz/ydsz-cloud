#!/usr/bin/env python3
"""Mapper XML 布尔字面量门禁。

项目布尔列统一为 SMALLINT（YDIZ-DB-007 / OOP-006），SQL 条件必须使用 0/1。
PostgreSQL 的 TRUE/FALSE 是 boolean 类型，与 SMALLINT 直接比较会产生类型错误或方言耦合。

用法：
  python3 data/scripts/check-mapper-boolean-literals.py
  python3 data/scripts/check-mapper-boolean-literals.py --root .
  python3 data/scripts/check-mapper-boolean-literals.py --self-test

退出码：0 通过；1 发现违规；2 前提不成立。
"""

from __future__ import annotations

import argparse
import re
import sys
import tempfile
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
BOOLEAN_LITERAL_RE = re.compile(
    r"\b((is_[a-z][a-z0-9_]*|isDeleted)\s*=\s*(?:TRUE|FALSE|true|false))\b",
    re.IGNORECASE,
)


class GateError(RuntimeError):
    """门禁前提不成立。"""


def strip_xml_comments(text: str) -> str:
    return re.sub(r"<!--.*?-->", "", text, flags=re.DOTALL)


def collect(root: Path) -> list[str]:
    if not root.exists():
        raise GateError(f"仓库根目录不存在：{root}")

    paths = sorted(root.rglob("*.xml"))
    mapper_paths = [path for path in paths if "mapper" in path.parts and "/target/" not in path.as_posix()]
    if not mapper_paths:
        raise GateError(f"未找到 Mapper XML：{root}")

    findings: list[str] = []
    for path in mapper_paths:
        try:
            text = path.read_text(encoding="utf-8")
        except (OSError, UnicodeDecodeError) as exc:
            raise GateError(f"无法读取 {path}: {exc}") from exc
        cleaned = strip_xml_comments(text)
        for match in BOOLEAN_LITERAL_RE.finditer(cleaned):
            line = cleaned[: match.start()].count("\n") + 1
            relative = path.relative_to(root).as_posix() if path.is_relative_to(root) else path.as_posix()
            findings.append(f"{relative}:{line}: 布尔字段必须使用 0/1，禁止 {match.group(1)}")

    return findings


def self_test() -> int:
    failures: list[str] = []

    def case(name: str, ok: bool) -> None:
        if not ok:
            failures.append(name)

    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        mapper_dir = root / "src" / "main" / "resources" / "mapper"
        mapper_dir.mkdir(parents=True)
        (mapper_dir / "GoodMapper.xml").write_text(
            "<mapper><select id=\"x\">WHERE is_deleted = 0</select></mapper>",
            encoding="utf-8",
        )
        (mapper_dir / "BadMapper.xml").write_text(
            "<mapper><select id=\"x\">WHERE is_deleted = FALSE</select>"
            "<!-- is_enabled = TRUE --></mapper>",
            encoding="utf-8",
        )
        findings = collect(root)
        joined = "\n".join(findings)
        case("布尔 TRUE/FALSE 被拦截", "BadMapper.xml" in joined)
        case("注释中的布尔值不误报", joined.count("BadMapper.xml") == 1)
        case("合规 Mapper 不报错", "GoodMapper.xml" not in joined)

    empty_root = Path(tempfile.mkdtemp())
    try:
        collect(empty_root)
        case("无 Mapper fail-closed", False)
    except GateError:
        case("无 Mapper fail-closed", True)

    print(f"自检结果：{'全部通过' if not failures else f'{len(failures)} 条未通过'}")
    for name in failures:
        print(f"  ✗ {name}", file=sys.stderr)
    return 0 if not failures else 1


def main() -> int:
    parser = argparse.ArgumentParser(description="Mapper XML 布尔字面量门禁")
    parser.add_argument("--root", default=str(REPO_ROOT), help="仓库根目录")
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
        findings = collect(Path(args.root))
    except GateError as exc:
        print(f"❌ {exc}", file=sys.stderr)
        return 2

    if findings:
        print(f"❌ 发现 {len(findings)} 处 Mapper 布尔字面量违规：", file=sys.stderr)
        for item in findings:
            print(f"   {item}", file=sys.stderr)
        return 1

    print("✅ Mapper XML 布尔字面量门禁通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
