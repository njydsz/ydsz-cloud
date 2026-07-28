# -*- coding: utf-8 -*-
"""
更全面的乱码扫描：
1. 检测所有 「。」 出现的位置
2. 上下文分类：
   - 句末合法：句号后是空格+中文或换行或句号
   - 句中误用：句号后直接跟中文或大写字母
   - 词尾截断：句号出现在不该结束的词后面（如「字段匹。」应为「字段匹配。」）
3. 额外检测：行内文本突然结束（缺标点）后跟 `*/` 或 `</p>` 等
"""
import pathlib
import re

ROOT = pathlib.Path(r"d:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-common\ydsz-common-json\src\main\java")

# 模式 1：中文 + 。 + 大写字母（典型乱码）
P1 = re.compile(r"[\u4e00-\u9fa5]\u3002[A-Z]")

# 模式 2：中文 + 。 + 小写字母（典型乱码，较少见）
P2 = re.compile(r"[\u4e00-\u9fa5]\u3002[a-z]")

# 模式 3：中文 + 。 + 中文（句中误用，可能是逗号被替换）
P3 = re.compile(r"[\u4e00-\u9fa5]\u3002[\u4e00-\u9fa5]")

# 模式 4：截断词：中文 + 。 + 空格 + 中文（句中误用）
P4 = re.compile(r"[\u4e00-\u9fa5]\u3002\s+[\u4e00-\u9fa5]")

# 模式 5：中文 + 。 + `*` （Javadoc 结尾 `。*/`）
# 例：「字段匹。*/」应为「字段匹配。*/
P5 = re.compile(r"[\u4e00-\u9fa5]\u3002\*/")

# 模式 6：中文 + 。 + `</` （HTML 标签前的句号）
# 例：「字节点/p>」是损坏的 HTML 标签
P6 = re.compile(r"[\u4e00-\u9fa5]\u3002</")

# 模式 7：行尾「中文。」后跟 `*/`（可能是截断）
P7 = re.compile(r"[\u4e00-\u9fa5]\u3002$")

# 模式 8：「中文」+ 空格 + 「*/」或 「中文*/」（缺标点）
P8 = re.compile(r"[\u4e00-\u9fa5]\s*\*/$")

# 模式 9：特殊截断词
P9 = re.compile(r"字节点[/\\]?p?")

print("=" * 80)
print("ydsz-common-json 中文乱码全面扫描报告 v2")
print("=" * 80)

total_findings = 0
file_findings = {}

for f in sorted(ROOT.rglob("*.java")):
    rel = f.relative_to(ROOT)
    try:
        content = f.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        print(f"[ENC-FAIL] {rel}")
        continue

    findings = []
    for i, line in enumerate(content.splitlines(), 1):
        for pat_name, pat in [
            ("P1-ZH+", P1), ("P2-zh+", P2), ("P3-ZH+ZH", P3),
            ("P4-ZH+SP+ZH", P4), ("P5-*/", P5), ("P6-</", P6),
            ("P7-EOL", P7), ("P9-trunc", P9)
        ]:
            for m in pat.finditer(line):
                findings.append((i, pat_name, m.group(), line.rstrip()))
                total_findings += 1

    if findings:
        file_findings[str(rel)] = findings

print(f"\n共扫描 {sum(1 for _ in ROOT.rglob('*.java'))} 个 Java 文件")
print(f"共发现 {total_findings} 处疑似乱码")
print(f"涉及 {len(file_findings)} 个文件\n")

for filepath, findings in file_findings.items():
    print(f"\n=== {filepath} ({len(findings)} 处) ===")
    for line_no, pat, match, line in findings:
        print(f"  L{line_no} [{pat}] match={match!r}")
        print(f"       {line}")

print("\n" + "=" * 80)
print("扫描完成")
print("=" * 80)
