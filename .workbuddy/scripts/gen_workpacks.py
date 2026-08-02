# -*- coding: utf-8 -*-
"""根据扫描报告生成分模块工作包清单，供并行补注释使用。"""
import json
import os

ROOT = r"D:\Code\ydsz\ydsz-pmis"
REPORT = os.path.join(ROOT, ".workbuddy", "scripts", "comment_report.json")
OUTDIR = os.path.join(ROOT, ".workbuddy", "scripts", "workpacks")
os.makedirs(OUTDIR, exist_ok=True)

rep = json.load(open(REPORT, encoding="utf-8"))


def emit(name, todos, kind):
    safe = name.replace("/", "_").replace("@", "")
    path = os.path.join(OUTDIR, f"{safe}.txt")
    with open(path, "w", encoding="utf-8") as f:
        f.write(f"# 工作包: {name}   待办文件 {len(todos)} 个\n")
        f.write("# 格式: [标记] 缺失数 | 文件路径 | 缺注释的成员\n")
        f.write("#   类头/文件头 = 该文件缺少类级或文件级文档注释\n\n")
        for t in todos:
            flag = "类头" if (t.get("type_doc") or t.get("file_doc")) else "--"
            f.write(f"[{flag}] {t['miss']:>2} | {t['file']}\n")
            if t.get("names"):
                f.write(f"        成员: {', '.join(t['names'])}\n")
    return path, len(todos)


print("=== 后端工作包 ===")
for mod in sorted(rep["backend"]):
    v = rep["backend"][mod]
    if v["todo"]:
        p, n = emit(mod, v["todo"], "java")
        print(f"{n:>4}  {os.path.basename(p)}")

print("\n=== 前端工作包 ===")
for mod in sorted(rep["frontend"]):
    v = rep["frontend"][mod]
    if v["todo"]:
        p, n = emit(mod, v["todo"], "ts")
        print(f"{n:>4}  {os.path.basename(p)}")

# ydsz-common 太大，按子模块再拆
common = rep["backend"].get("ydsz-common", {}).get("todo", [])
if common:
    groups = {}
    for t in common:
        parts = t["file"].split(os.sep)
        sub = parts[2] if len(parts) > 2 else "misc"
        groups.setdefault(sub, []).append(t)
    print("\n=== ydsz-common 子包拆分 ===")
    for sub in sorted(groups, key=lambda x: -len(groups[x])):
        p, n = emit(f"common__{sub}", groups[sub], "java")
        print(f"{n:>4}  {os.path.basename(p)}")
