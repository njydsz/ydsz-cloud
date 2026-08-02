# -*- coding: utf-8 -*-
"""
安全校验：确认本次改动**只涉及注释**，没有动到任何代码。

原理：把改动前后的文件都做「剥离注释 + 归一化空白」处理，
      再逐字符比对。若相同，说明改动纯粹是注释；若不同，打印差异定位。

用法：
    python verify_comment_only.py            # 校验工作区所有已改 .java/.ts/.vue
    python verify_comment_only.py --show N   # 打印前 N 个差异细节
"""
import os
import re
import subprocess
import sys

ROOT = r"D:\Code\ydsz\ydsz-pmis"


def run(args):
    return subprocess.run(args, cwd=ROOT, capture_output=True, text=True,
                          encoding="utf-8", errors="replace")


def strip_comments_java(src):
    """移除 // 与 /* */ 注释，保留字符串字面量内容。"""
    out = []
    i, n = 0, len(src)
    while i < n:
        c = src[i]
        if c == '"' or c == "'":
            q = c
            out.append(c)
            i += 1
            while i < n:
                if src[i] == "\\":
                    out.append(src[i])
                    if i + 1 < n:
                        out.append(src[i + 1])
                    i += 2
                    continue
                out.append(src[i])
                if src[i] == q:
                    i += 1
                    break
                if src[i] == "\n":      # 未闭合字符串，放弃
                    i += 1
                    break
                i += 1
        elif c == "/" and i + 1 < n and src[i + 1] == "/":
            while i < n and src[i] != "\n":
                i += 1
        elif c == "/" and i + 1 < n and src[i + 1] == "*":
            i += 2
            while i + 1 < n and not (src[i] == "*" and src[i + 1] == "/"):
                i += 1
            i += 2
        else:
            out.append(c)
            i += 1
    return "".join(out)


def normalize(src):
    """归一化：去注释 + 压缩所有空白，得到「代码骨架」。"""
    code = strip_comments_java(src)
    return re.sub(r"\s+", " ", code).strip()


def main():
    show = 5
    if "--show" in sys.argv:
        show = int(sys.argv[sys.argv.index("--show") + 1])

    r = run(["git", "diff", "--name-only"])
    files = [f.strip() for f in r.stdout.split("\n") if f.strip()]
    targets = [f for f in files if f.endswith((".java", ".ts", ".vue", ".tsx"))]

    print(f"待校验文件: {len(targets)}")
    ok = bad = skipped = 0
    problems = []

    for f in targets:
        old = run(["git", "show", f"HEAD:{f}"])
        if old.returncode != 0:
            skipped += 1
            continue
        path = os.path.join(ROOT, f.replace("/", os.sep))
        try:
            with open(path, "r", encoding="utf-8") as fh:
                new_src = fh.read()
        except Exception as e:
            problems.append((f, f"读取失败: {e}"))
            bad += 1
            continue

        a, b = normalize(old.stdout), normalize(new_src)
        if a == b:
            ok += 1
        else:
            bad += 1
            # 定位第一个差异
            k = 0
            m = min(len(a), len(b))
            while k < m and a[k] == b[k]:
                k += 1
            problems.append((
                f,
                f"代码骨架不一致 @偏移{k}\n"
                f"      原: ...{a[max(0,k-60):k+60]}...\n"
                f"      新: ...{b[max(0,k-60):k+60]}..."
            ))

    print(f"\n✅ 纯注释改动: {ok}")
    print(f"❌ 代码被改动: {bad}")
    print(f"⏭  新增文件跳过: {skipped}")

    if problems:
        print("\n" + "=" * 70)
        print("需人工确认的文件：")
        print("=" * 70)
        for f, msg in problems[:show]:
            print(f"\n【{f}】\n      {msg}")
        if len(problems) > show:
            print(f"\n... 另有 {len(problems)-show} 个，用 --show N 查看更多")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
