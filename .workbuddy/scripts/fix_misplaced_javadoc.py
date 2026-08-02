# -*- coding: utf-8 -*-
"""
修复 Javadoc 错位缺陷。

问题形态（Javadoc 被写在注解之后 → javadoc 工具与 IDE 均不采集，注释失效）：

    @Override
    /**
     * 说明
     */
    public void foo() {}

修复为（Javadoc 必须位于所有注解之前）：

    /**
     * 说明
     */
    @Override
    public void foo() {}

用法：
    python fix_misplaced_javadoc.py --dry-run   # 预览
    python fix_misplaced_javadoc.py --apply     # 落盘
"""
import os
import re
import sys

ROOT = r"D:\Code\ydsz\ydsz-pmis"
SKIP_DIRS = {"target", "node_modules", "dist", ".git", ".idea", "build"}

DECL = re.compile(
    r"^\s*(?:public|protected|private|static|final|abstract|synchronized|default|native|"
    r"class|interface|enum|record|<)"
)


def iter_java(base):
    for dirpath, dirnames, filenames in os.walk(base):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for fn in filenames:
            if fn.endswith(".java"):
                yield os.path.join(dirpath, fn)


def annotation_group_start(lines, javadoc_start):
    """从 Javadoc 起始行向上回溯，返回其上方连续注解块的首行下标。

    支持跨行注解（如 @RequestMapping(\n  value = "..",\n  method = ..)）。
    找不到注解则返回 None。
    """
    i = javadoc_start - 1
    while i >= 0 and lines[i].strip() == "":
        i -= 1
    if i < 0:
        return None
    last = i

    # 向上收集，直到遇到空行 / 注释 / 声明 / 语句
    best = None
    cur = last
    while cur >= 0:
        s = lines[cur].strip()
        if s == "" or s.endswith("*/") or s.startswith("*") or s.startswith("/*") or s.startswith("//"):
            break
        if s.endswith(";") or s.endswith("{") or s.endswith("}"):
            # 上一个成员的结尾，停止
            break
        if s.startswith("@"):
            chunk = "\n".join(lines[cur:last + 1])
            if chunk.count("(") == chunk.count(")"):
                best = cur          # 记录一个完整的注解块起点
        cur -= 1
    return best


def fix_file(path):
    with open(path, "r", encoding="utf-8") as f:
        src = f.read()
    lines = src.split("\n")

    fixes = []
    i = 0
    while i < len(lines):
        if lines[i].strip().startswith("/**"):
            # 定位 Javadoc 结束行
            j = i
            while j < len(lines) and not lines[j].strip().endswith("*/"):
                j += 1
            if j >= len(lines):
                break
            # Javadoc 之后必须紧跟声明（跳过空行）
            k = j + 1
            while k < len(lines) and lines[k].strip() == "":
                k += 1
            if k < len(lines) and DECL.match(lines[k]):
                a = annotation_group_start(lines, i)
                if a is not None:
                    fixes.append((a, i, j))
            i = j + 1
        else:
            i += 1

    if not fixes:
        return None, 0

    # 从后往前改，避免下标失效
    for a, ds, de in reversed(fixes):
        doc = lines[ds:de + 1]
        annos = lines[a:ds]
        lines[a:de + 1] = doc + annos

    return "\n".join(lines), len(fixes)


def main():
    apply = "--apply" in sys.argv
    targets = [
        os.path.join(ROOT, "ydsz-backend", "ydsz-gateway"),
        os.path.join(ROOT, "ydsz-backend", "ydsz-message"),
    ]
    # 允许全量
    if "--all" in sys.argv:
        targets = [os.path.join(ROOT, "ydsz-backend")]

    total_files = total_fix = 0
    for base in targets:
        for p in iter_java(base):
            new, n = fix_file(p)
            if n:
                total_files += 1
                total_fix += n
                rel = os.path.relpath(p, ROOT)
                print(f"{'[修复]' if apply else '[预览]'} {n:>2} 处  {rel}")
                if apply:
                    with open(p, "w", encoding="utf-8") as f:
                        f.write(new)
    print()
    print(f"{'已修复' if apply else '待修复'}：{total_files} 个文件，{total_fix} 处错位")
    if not apply:
        print("加 --apply 参数执行实际修改")


if __name__ == "__main__":
    main()
