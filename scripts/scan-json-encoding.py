# -*- coding: utf-8 -*-
"""
扫描 ydsz-common-json 模块所有 .java 文件的中文注释乱码。

损坏模式（PowerShell 写入痕迹）：
1. 「，」(U+FF0C) 被替换为 「。」(U+3002)
2. 单字被截断（如「字节码」→「字节点/p」、「捕获」→「捕。」、「创建」→「创。」）
3. 句中「。」出现在代码标识符旁（非真正句末）

输出：每行 file:line:content，便于人工逐条 review 修复。
"""
import pathlib
import re

ROOT = pathlib.Path(r"d:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-common\ydsz-common-json\src\main\java")

# 模式 1：中文 + 。 + 中文/大写字母（典型误替换）
# 例：「单。ConcurrentHashMap」「捕。AsmSerializer」「创。ASM」
P1 = re.compile(r"[\u4e00-\u9fa5]\u3002[\u4e00-\u9fa5A-Z]")

# 模式 2：中文 + 。 + 空格 + 中文（句中误用句号）
# 例：「避免重复生成字节点/p」不在此模式，需单独扫描
P2 = re.compile(r"[\u4e00-\u9fa5]\u3002\s+[\u4e00-\u9fa5]")

# 模式 3：行末「。」紧接代码标识符（非句末）
# 例：「使用单。ConcurrentHashMap」中的「。Concurrent」
# 此模式已被 P1 覆盖

# 模式 4：截断词，如「字节点/p」「字节点\\」
P4 = re.compile(r"字节点[/\\]?p?")

# 模式 5：中文 + 。（行尾），但句意明显不是结束
# 此模式无法机器判定，需人工 review

# 模式 6：中文 + 。（行末标点）后跟代码
P6 = re.compile(r"[\u4e00-\u9fa5]\u3002`")

# 模式 7：连续两个中文之间夹 「。」
P7 = re.compile(r"[\u4e00-\u9fa5]\u3002[\u4e00-\u9fa5]")

print("=" * 80)
print("ydsz-common-json 中文乱码扫描报告")
print("=" * 80)

total_findings = 0
file_findings = {}

for f in sorted(ROOT.rglob("*.java")):
    rel = f.relative_to(ROOT)
    try:
        content = f.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        # 尝试 GBK
        try:
            content = f.read_text(encoding="gbk")
            print(f"[ENC-GBK] {rel} 文件用 GBK 解码成功，可能编码错误")
        except Exception:
            print(f"[ENC-FAIL] {rel} 文件编码无法识别")
            continue

    findings = []
    for i, line in enumerate(content.splitlines(), 1):
        for pat_name, pat in [("P1", P1), ("P2", P2), ("P4", P4), ("P7", P7)]:
            for m in pat.finditer(line):
                findings.append((i, pat_name, m.group(), line.rstrip()))
                total_findings += 1

    if findings:
        file_findings[str(rel)] = findings

print(f"\n共扫描 {sum(1 for _ in ROOT.rglob('*.java'))} 个 Java 文件")
print(f"共发现 {total_findings} 处疑似乱码")
print(f"涉及 {len(file_findings)} 个文件\n")

for filepath, findings in file_findings.items():
    print(f"\n--- {filepath} ({len(findings)} 处) ---")
    for line_no, pat, match, line in findings:
        print(f"  L{line_no} [{pat}] match={match!r}")
        print(f"       {line}")

print("\n" + "=" * 80)
print("扫描完成")
print("=" * 80)
