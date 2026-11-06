#!/usr/bin/env python3
"""规则源同步门禁 —— 由 docs/ai-rules/shared-rules.yaml 生成各 AI 工具的规则副本。

背景：
  shared-rules.yaml 是单一权威源（Single Source of Truth）。
  CatPaw / WorkBuddy 等 AI 编码工具加载的是 Markdown 副本，必须由本脚本生成，
  禁止手工编辑生成物（手工编辑会在下次同步时被覆盖，且导致规则漂移）。

职责：
  1. 解析 shared-rules.yaml（解析失败即判红 —— fail-closed）
  2. 校验规则源自洽性：
     - meta.total_rules == 实际规则条数
     - meta.summary.P0/P1/P2 == 实际 severity 分布
     - 每条规则必须含 id / severity / title / description
     - id 全局唯一
  3. 渲染 Markdown 并写入目标文件（仅当内容变化时写盘）
  4. 校验生成物头部声明与实际内容一致（防手工篡改）

用法：
  python3 data/scripts/sync-ai-rules.py                # 同步（内容无变化则跳过写盘）
  python3 data/scripts/sync-ai-rules.py --check        # 只校验生成物是否为最新（CI 用）
  python3 data/scripts/sync-ai-rules.py --self-test    # 自测
  python3 data/scripts/sync-ai-rules.py --list         # 列出全部规则

退出码非 0 表示规则源或生成物有问题；前提不成立同样失败（fail-closed）。
"""

from __future__ import annotations

import argparse
import sys
from datetime import datetime
from pathlib import Path

import yaml

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
SOURCE = REPO_ROOT / "docs" / "ai-rules" / "shared-rules.yaml"

TARGETS = [
    REPO_ROOT / ".meituan-catpaw" / "5728405356" / "rules" / "catpaw-always.md",
    REPO_ROOT / ".workbuddy" / "rules" / "workbuddy-system.md",
]

SEVERITY_ORDER = {"P0": 0, "P1": 1, "P2": 2}
SEVERITY_DESC = {
    "P0": "阻断级，AI 编码绝对违反（违反即判红，必须修复）",
    "P1": "严重级，AI 编码不应违反（违反需人工确认）",
    "P2": "建议级，AI 编码建议遵循（违反需说明理由）",
}

REQUIRED_FIELDS = ("id", "severity", "title", "description")


# --------------------------------------------------------------------------- #
# 解析与校验
# --------------------------------------------------------------------------- #
def load_source() -> dict:
    """解析规则源；解析失败直接抛异常（fail-closed）。"""
    if not SOURCE.exists():
        raise SystemExit(f"❌ 规则源不存在：{SOURCE}")
    try:
        data = yaml.safe_load(SOURCE.read_text(encoding="utf-8"))
    except yaml.YAMLError as exc:
        raise SystemExit(f"❌ 规则源 YAML 解析失败：{exc}") from exc
    if not isinstance(data, dict):
        raise SystemExit("❌ 规则源顶层必须是映射（meta/rules/stats）")
    return data


def validate_source(data: dict) -> list[str]:
    """校验规则源自洽性，返回错误列表（空 = 通过）。"""
    errors: list[str] = []
    meta = data.get("meta") or {}
    rules = data.get("rules")

    if not isinstance(rules, list) or not rules:
        return ["规则源缺少 rules 列表或为空"]

    ids = [r.get("id") for r in rules]
    dup = sorted({i for i in ids if ids.count(i) > 1})
    if dup:
        errors.append(f"规则 ID 重复：{dup}")

    missing = [r.get("id", "<无 id>") for r in rules if not all(r.get(f) for f in REQUIRED_FIELDS)]
    if missing:
        errors.append(f"以下规则缺少必需字段 {REQUIRED_FIELDS}：{missing}")

    sev_dist = {"P0": 0, "P1": 0, "P2": 0}
    for r in rules:
        sev = r.get("severity")
        if sev not in SEVERITY_ORDER:
            errors.append(f"{r.get('id')} 的 severity 非法：{sev!r}")
        else:
            sev_dist[sev] += 1

    declared_total = meta.get("total_rules")
    if declared_total != len(rules):
        errors.append(f"meta.total_rules={declared_total} 与实际条数 {len(rules)} 不一致")

    summary = meta.get("summary") or {}
    for sev in ("P0", "P1", "P2"):
        if summary.get(sev) != sev_dist[sev]:
            errors.append(f"meta.summary.{sev}={summary.get(sev)} 与实际 {sev_dist[sev]} 不一致")

    return errors


# --------------------------------------------------------------------------- #
# 渲染
# --------------------------------------------------------------------------- #
def _clean(text: object) -> str:
    """压平多行文本为单行（Markdown 表格/列表内可用）。"""
    if text is None:
        return ""
    return " ".join(str(text).split())


def render(data: dict) -> str:
    """把规则源渲染为 Markdown。"""
    meta = data["meta"]
    rules = data["rules"]
    now = datetime.now().strftime("%Y-%m-%d %H:%M")

    lines: list[str] = []
    lines.append("# 云顶编码规范 · AI 红线规则集（自动生成，请勿手工编辑）")
    lines.append("")
    lines.append("> **本文件由 `data/scripts/sync-ai-rules.py` 从 `docs/ai-rules/shared-rules.yaml` 自动生成。**")
    lines.append("> 手工编辑会在下次同步时被覆盖。规则变更请修改规则源后重新运行同步脚本。")
    lines.append(">")
    lines.append(f"> 生成时间：{now}　|　规则源版本：`{meta.get('version')}`　|　"
                 f"规则总数：**{len(rules)}**（P0={meta['summary']['P0']} P1={meta['summary']['P1']} P2={meta['summary']['P2']}）")
    lines.append("")

    for sev in ("P0", "P1", "P2"):
        group = [r for r in rules if r.get("severity") == sev]
        lines.append(f"## {sev} — {SEVERITY_DESC[sev]}（{len(group)} 条）")
        lines.append("")
        for r in sorted(group, key=lambda x: str(x.get("id"))):
            lines.append(f"### `{r['id']}`　{_clean(r.get('title'))}")
            lines.append("")
            desc = _clean(r.get("description"))
            if desc:
                lines.append(desc)
                lines.append("")
            pattern = _clean(r.get("pattern"))
            if pattern:
                lines.append(f"- **判定模式**：`{pattern}`")
            fix = _clean(r.get("fix"))
            if fix:
                lines.append(f"- **修复方式**：{fix}")
            source = _clean(r.get("source"))
            if source:
                lines.append(f"- **来源**：{source}")
            lines.append("")
        lines.append("")

    lines.append("---")
    lines.append("")
    lines.append("## 按 category 索引")
    lines.append("")
    cats: dict[str, list[str]] = {}
    for r in rules:
        cats.setdefault(str(r.get("category") or "未分类"), []).append(str(r["id"]))
    for cat in sorted(cats):
        lines.append(f"- **{cat}**（{len(cats[cat])}）：{', '.join(sorted(cats[cat]))}")
    lines.append("")
    return "\n".join(lines)


def parse_header_counts(text: str) -> tuple[int, int, int, int] | None:
    """从生成物头部解析声明的规则数与 severity 分布。"""
    import re

    m = re.search(r"规则总数：\*\*(\d+)\*\*（P0=(\d+) P1=(\d+) P2=(\d+)）", text)
    if not m:
        return None
    return tuple(int(x) for x in m.groups())  # type: ignore[return-value]


# --------------------------------------------------------------------------- #
# 主流程
# --------------------------------------------------------------------------- #
def main() -> int:
    parser = argparse.ArgumentParser(description="规则源同步门禁")
    parser.add_argument("--check", action="store_true", help="只校验生成物是否为最新（CI 用）")
    parser.add_argument("--self-test", action="store_true", help="自测")
    parser.add_argument("--list", action="store_true", help="列出全部规则")
    args = parser.parse_args()

    data = load_source()
    errors = validate_source(data)
    if errors:
        for e in errors:
            print(f"❌ {e}", file=sys.stderr)
        return 1

    rules = data["rules"]
    meta = data["meta"]
    print(f"✅ 规则源自洽：{len(rules)} 条（P0={meta['summary']['P0']} "
          f"P1={meta['summary']['P1']} P2={meta['summary']['P2']}）")

    if args.list:
        for r in sorted(rules, key=lambda x: str(x.get("id"))):
            print(f"  {r['severity']}  {r['id']:<22} {_clean(r.get('title'))[:70]}")
        return 0

    if args.self_test:
        return self_test(data)

    rendered = render(data)
    expected = (len(rules), meta["summary"]["P0"], meta["summary"]["P1"], meta["summary"]["P2"])

    rc = 0
    for target in TARGETS:
        if not target.parent.exists():
            print(f"⚠️  目标目录不存在，跳过：{target}")
            continue
        current = target.read_text(encoding="utf-8") if target.exists() else ""
        if args.check:
            declared = parse_header_counts(current)
            if declared is None:
                print(f"❌ {target.name}：无法解析头部声明（可能被手工篡改）")
                rc = 1
            elif declared != expected:
                print(f"❌ {target.name}：声明 {declared} ≠ 实际 {expected}（规则源已变更，需重新同步）")
                rc = 1
            else:
                # 进一步校验正文关键规则是否齐全
                missing = [r["id"] for r in rules if f"`{r['id']}`" not in current]
                if missing:
                    print(f"❌ {target.name}：缺失 {len(missing)} 条规则 {missing[:5]}...")
                    rc = 1
                else:
                    print(f"✅ {target.name}：已是最新（{len(rules)} 条）")
            continue

        if current == rendered:
            print(f"✅ {target.name}：内容无变化，跳过写盘")
            continue
        target.write_text(rendered, encoding="utf-8", newline="\n")
        print(f"✅ {target.name}：已写入（{len(rules)} 条规则）")

    return rc


def self_test(data: dict) -> int:
    """自测：渲染 → 解析头部 → 校验条数一致。"""
    rules = data["rules"]
    meta = data["meta"]
    rendered = render(data)
    declared = parse_header_counts(rendered)
    expected = (len(rules), meta["summary"]["P0"], meta["summary"]["P1"], meta["summary"]["P2"])
    if declared != expected:
        print(f"❌ 自测失败：渲染头部 {declared} ≠ 期望 {expected}")
        return 1
    missing = [r["id"] for r in rules if f"`{r['id']}`" not in rendered]
    if missing:
        print(f"❌ 自测失败：渲染结果缺失 {len(missing)} 条 {missing[:5]}")
        return 1
    print(f"✅ 自测通过：渲染 {len(rules)} 条规则，头部声明一致，无遗漏")
    return 0


if __name__ == "__main__":
    sys.exit(main())
