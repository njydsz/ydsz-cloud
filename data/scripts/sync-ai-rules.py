#!/usr/bin/env python3
"""
云顶编码规范 - AI 工具规则同步脚本
Ydsz Cloud - AI Tool Rules Sync Script

功能:
  generate  - 从 docs/ai-rules/shared-rules.yaml 生成 CatPaw/WorkBuddy 规则文件
  check     - 验证两个输出目录的规则文件与源文件是否同步（CI 门禁用）
  diff      - 展示源文件与输出文件的差异

用法:
  python data/scripts/sync-ai-rules.py generate
  python data/scripts/sync-ai-rules.py check
  python data/scripts/sync-ai-rules.py diff

退出码:
  0 = 同步成功 / 检查通过
  1 = 不同步 / 检查失败（CI 阻断）
"""

import sys
import os
import hashlib
import re
import yaml
from pathlib import Path
from datetime import datetime

# 项目根目录（脚本在 data/scripts/，项目根为上两层）
SCRIPT_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = SCRIPT_DIR.parent.parent

# 路径配置
SOURCE_FILE = PROJECT_ROOT / "docs" / "ai-rules" / "shared-rules.yaml"
CATPAW_RULES = PROJECT_ROOT / ".meituan-catpaw" / "5728405356" / "rules" / "catpaw-always.md"
WORKBUDDY_RULES = PROJECT_ROOT / ".workbuddy" / "rules" / "workbuddy-system.md"
HASH_FILE = PROJECT_ROOT / "docs" / "ai-rules" / ".sync-hash"


def load_source_rules():
    """加载并解析源 YAML 规则文件"""
    if not SOURCE_FILE.exists():
        print(f"[ERROR] 源文件不存在: {SOURCE_FILE}")
        sys.exit(1)
    with open(SOURCE_FILE, 'r', encoding='utf-8') as f:
        return yaml.safe_load(f)


def generate_catpaw_rules(data):
    """生成 CatPaw always 级别规则文件"""
    rules = data.get('rules', [])
    meta = data.get('meta', {})

    p0_rules = [r for r in rules if r['severity'] == 'P0']
    p1_rules = [r for r in rules if r['severity'] == 'P1']
    p2_rules = [r for r in rules if r['severity'] == 'P2']

    lines = [
        "# 云顶编码规范 - CatPaw Always 规则",
        "# 本文件由 data/scripts/sync-ai-rules.py 自动生成，请勿手动修改",
        f"# 生成时间: {datetime.now().strftime('%Y-%m-%d')}",
        f"# 源文件: docs/ai-rules/shared-rules.yaml",
        "",
        "> 本文件为 CatPaw AI 编码工具的 always 级别规则，编码时自动加载。",
        "",
        "## 项目基本信息",
        "",
        "- 项目: ydsz-cloud（云顶云平台）",
        f"- 规范版本: {meta.get('source', 'unknown')}",
        f"- 规则总数: {meta.get('total_rules', len(rules))} 条（P0={len(p0_rules)}, P1={len(p1_rules)}, P2={len(p2_rules)}）",
        "",
        "---",
        "",
        f"## P0 阻断级（{len(p0_rules)} 条，AI 编码绝对禁止违反）",
        "",
    ]

    for r in p0_rules:
        lines.append(f"### {r['id']}: {r['title']}")
        lines.append(f"**说明**: {r['description']}")
        if 'fix' in r:
            lines.append(f"**修复**: {r['fix']}")
        if 'example_bad' in r:
            lines.append(f"**反例**: `{r['example_bad']}`")
        if 'example_good' in r:
            lines.append(f"**正例**: `{r['example_good']}`")
        lines.append(f"**来源**: {r.get('source', '编码规范')}")
        lines.append("")

    lines.append(f"## P1 严格级（{len(p1_rules)} 条，违反需人工确认）")
    lines.append("")
    for r in p1_rules:
        lines.append(f"- **{r['id']}**: {r['title']} — {r['description']}")

    lines.append("")
    lines.append(f"## P2 建议级（{len(p2_rules)} 条）")
    lines.append("")
    for r in p2_rules:
        lines.append(f"- **{r['id']}**: {r['title']} — {r['description']}")

    lines.append("")
    lines.append("---")
    lines.append(f"*本文件由脚本自动生成，源文件: {SOURCE_FILE.relative_to(PROJECT_ROOT)}*")

    return '\n'.join(lines)


def generate_workbuddy_rules(data):
    """生成 WorkBuddy system prompt 增强规则文件"""
    rules = data.get('rules', [])
    meta = data.get('meta', {})

    p0_rules = [r for r in rules if r['severity'] == 'P0']
    p1_rules = [r for r in rules if r['severity'] == 'P1']
    p2_rules = [r for r in rules if r['severity'] == 'P2']

    lines = [
        "# 云顶编码规范 - WorkBuddy System Prompt 增强规则",
        "# 本文件由 data/scripts/sync-ai-rules.py 自动生成，请勿手动修改",
        f"# 生成时间: {datetime.now().strftime('%Y-%m-%d')}",
        f"# 源文件: docs/ai-rules/shared-rules.yaml",
        "",
        "> 本文件为 WorkBuddy AI 编码工具的系统提示词增强规则。",
        "> 在 WorkBuddy 创建/编辑任务时，应将此文件内容附加到 system prompt 末尾。",
        "",
        "---",
        "",
        "## 编码红线（遇到以下场景，必须遵守，不得通融）",
        "",
    ]

    # 按 category 分组输出 P0 规则
    categories = {}
    for r in p0_rules:
        cat = r['category']
        if cat not in categories:
            categories[cat] = []
        categories[cat].append(r)

    cat_titles = {
        'import': 'Import 规则',
        'naming': '命名规则',
        'oop': 'OOP 规约',
        'logging': '日志与异常',
        'datetime': '日期时间',
        'collection': '集合与数据结构',
        'ddd': 'DDD 分层架构',
        'concurrency': '并发规范',
        'performance': '性能规范',
        'common-reuse': 'common 模块复用',
        'security': '安全规范',
        'engineering': '工程约束',
    }

    for cat, cat_rules in categories.items():
        title = cat_titles.get(cat, cat)
        lines.append(f"### {title}")
        for r in cat_rules:
            lines.append(f"- **{r['id']}**: {r['title']} — {r['description']}")
        lines.append("")

    lines.append("## P1 严格约束（不应违反，违反需人工确认）")
    lines.append("")
    for r in p1_rules:
        lines.append(f"- **{r['id']}**: {r['title']} — {r['description']}")

    lines.append("")
    lines.append("## P2 建议遵循")
    lines.append("")
    for r in p2_rules:
        lines.append(f"- **{r['id']}**: {r['title']}")

    lines.append("")
    lines.append("---")
    lines.append(f"*本文件由脚本自动生成，源文件: {SOURCE_FILE.relative_to(PROJECT_ROOT)}*")

    return '\n'.join(lines)


def compute_source_hash():
    """计算源文件的 hash，用于增量检测"""
    with open(SOURCE_FILE, 'rb') as f:
        return hashlib.sha256(f.read()).hexdigest()[:16]


def write_hash():
    """写入当前源文件的 hash 到缓存文件"""
    h = compute_source_hash()
    with open(HASH_FILE, 'w') as f:
        f.write(h)


def read_hash():
    """读取上次同步的 hash"""
    if HASH_FILE.exists():
        with open(HASH_FILE, 'r') as f:
            return f.read().strip()
    return None


def cmd_generate():
    """生成模式：从源文件生成所有输出"""
    print(f"[INFO] 加载源文件: {SOURCE_FILE}")
    data = load_source_rules()

    # 确保输出目录存在
    CATPAW_RULES.parent.mkdir(parents=True, exist_ok=True)
    WORKBUDDY_RULES.parent.mkdir(parents=True, exist_ok=True)

    # 生成 CatPaw
    catpaw_content = generate_catpaw_rules(data)
    with open(CATPAW_RULES, 'w', encoding='utf-8') as f:
        f.write(catpaw_content)
    print(f"[OK] CatPaw 规则已生成: {CATPAW_RULES}")

    # 生成 WorkBuddy
    workbuddy_content = generate_workbuddy_rules(data)
    with open(WORKBUDDY_RULES, 'w', encoding='utf-8') as f:
        f.write(workbuddy_content)
    print(f"[OK] WorkBuddy 规则已生成: {WORKBUDDY_RULES}")

    # 写入 hash 标记为已同步
    write_hash()
    rules = data.get('rules', [])
    print(f"[OK] 同步完成，共处理 {len(rules)} 条规则，hash={compute_source_hash()}")
    return 0


def cmd_check():
    """检查模式：验证输出是否与源文件同步"""
    if not CATPAW_RULES.exists():
        print(f"[FAIL] CatPaw 规则文件不存在: {CATPAW_RULES}")
        print("[HINT] 请先运行: python data/scripts/sync-ai-rules.py generate")
        return 1

    if not WORKBUDDY_RULES.exists():
        print(f"[FAIL] WorkBuddy 规则文件不存在: {WORKBUDDY_RULES}")
        print("[HINT] 请先运行: python data/scripts/sync-ai-rules.py generate")
        return 1

    current_hash = compute_source_hash()
    last_hash = read_hash()

    if current_hash == last_hash:
        print(f"[PASS] 规则文件与源文件同步（hash={current_hash}）")
        return 0
    else:
        print(f"[FAIL] 规则文件与源文件不同步！")
        print(f"       源文件 hash: {current_hash}")
        print(f"       上次同步:   {last_hash or 'N/A（未记录）'}")
        print("[HINT] 请运行: python data/scripts/sync-ai-rules.py generate")
        return 1


def cmd_diff():
    """差异模式：展示源文件与输出的差异概要"""
    data = load_source_rules()
    current_hash = compute_source_hash()
    last_hash = read_hash()

    rules = data.get('rules', [])
    p0 = len([r for r in rules if r['severity'] == 'P0'])
    p1 = len([r for r in rules if r['severity'] == 'P1'])
    p2 = len([r for r in rules if r['severity'] == 'P2'])

    print("=" * 60)
    print("云顶编码规范 - AI 工具规则同步状态")
    print("=" * 60)
    print(f"源文件:       {SOURCE_FILE}")
    print(f"源文件 hash:  {current_hash}")
    print(f"上次同步:     {last_hash or '未记录'}")
    print(f"同步状态:     {'已同步 ✓' if current_hash == last_hash else '不同步 ✗'}")
    print(f"规则总数:     {len(rules)} (P0={p0}, P1={p1}, P2={p2})")
    print(f"")
    print(f"输出文件:")
    print(f"  CatPaw:     {CATPAW_RULES} ({'存在' if CATPAW_RULES.exists() else '不存在'})")
    print(f"  WorkBuddy:  {WORKBUDDY_RULES} ({'存在' if WORKBUDDY_RULES.exists() else '不存在'})")
    print("=" * 60)
    return 0


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        print("请指定命令: generate | check | diff")
        sys.exit(1)

    cmd = sys.argv[1].lower()
    if cmd == 'generate':
        sys.exit(cmd_generate())
    elif cmd == 'check':
        sys.exit(cmd_check())
    elif cmd == 'diff':
        sys.exit(cmd_diff())
    else:
        print(f"[ERROR] 未知命令: {cmd}")
        print("可用命令: generate | check | diff")
        sys.exit(1)


if __name__ == '__main__':
    main()
