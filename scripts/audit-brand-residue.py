#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
P2-1 阶段：全仓库 Ydsz 品牌残留清单摸底。

按品牌策略维度分类：
  1. 类名（Java 源文件中 class/interface/enum/@interface 声明）
  2. 文件名（Ydsz*.java）
  3. 包名/import 路径（com.njydsz.pmis.*）
  4. Maven groupId / artifactId
  5. 文档（README / *.md）
  6. 资源文件（*.json / *.yml / *.yaml / *.properties / *.xml / *.sql / *.sh）
  7. 前端代码
  8. 部署与运维（Docker / K8s / Helm / Argo / 镜像 tag）

输出：
  - 全仓库 Ydsz 命中统计
  - 各维度 Top N
  - 模块级分布
"""
import pathlib
import re
import json
from collections import Counter, defaultdict

REPO = pathlib.Path("d:/Code/ydsz/ydsz-pmis")

# 跳过目录
SKIP_DIRS = {".git", "node_modules", "dist", "build", "target", ".idea", ".vscode", ".mvn", "venv", "__pycache__"}

# 文件后缀分组
EXT_GROUPS = {
    "Java源文件": {".java"},
    "前端代码": {".ts", ".tsx", ".js", ".jsx", ".vue", ".scss", ".css", ".html"},
    "YAML/配置": {".yml", ".yaml", ".properties"},
    "JSON配置": {".json"},
    "XML/SQL/SH": {".xml", ".sql", ".sh", ".bat", ".ps1"},
    "Markdown": {".md"},
    "Docker": {".dockerfile", "Dockerfile"},
    "文本/其它": set(),
}

# 维度正则
DIMENSIONS = {
    "类名含Ydsz": re.compile(r"\b(class|interface|enum|@interface|record)\s+Ydsz[A-Z]\w*"),
    "Ydsz*文件": re.compile(r"Ydsz[A-Z]\w*\.(java|json|xml|yml|yaml|md)"),
    "import含Ydsz": re.compile(r"import\s+com\.njydsz\.pmis\.[^;]*Ydsz", re.IGNORECASE),
    "groupId含Ydsz": re.compile(r"<groupId>\s*com\.njydsz", re.IGNORECASE),
    "artifactId含Ydsz": re.compile(r"<artifactId>\s*ydsz-", re.IGNORECASE),
    "package含njydsz": re.compile(r"package\s+com\.njydsz", re.IGNORECASE),
    "Ydsz字面引用": re.compile(r"\bYdsz[A-Z]\w*"),
    "ydsz-pmis字面": re.compile(r"\bydsz-pmis\b"),
    "njydsz字面": re.compile(r"\bnjydsz\b"),
    "PMIS字面引用": re.compile(r"\bPMIS\b"),
}

# 统计
dim_hits = Counter()
dim_files = defaultdict(set)
ext_hits = Counter()
module_hits = Counter()
file_detail = defaultdict(list)

for f in REPO.rglob("*"):
    if not f.is_file():
        continue
    # 跳过目录
    parts = f.parts
    if any(p in SKIP_DIRS for p in parts):
        continue
    # 跳过二进制（粗略）
    if f.suffix in {".class", ".jar", ".war", ".zip", ".png", ".jpg", ".jpeg", ".gif", ".ico", ".woff", ".woff2", ".ttf", ".pdf", ".exe", ".dll", ".so", ".dylib", ".bin", ".dat"}:
        continue
    # 文件大小限制（10MB）
    try:
        if f.stat().st_size > 10 * 1024 * 1024:
            continue
    except Exception:
        continue

    try:
        text = f.read_text(encoding="utf-8", errors="ignore")
    except Exception:
        continue

    if not text:
        continue

    # 检测每个维度
    file_dim_hits = {}
    for dim_name, pattern in DIMENSIONS.items():
        matches = pattern.findall(text)
        if matches:
            file_dim_hits[dim_name] = len(matches)
            dim_hits[dim_name] += len(matches)
            dim_files[dim_name].add(f)

    if file_dim_hits:
        # 文件后缀
        ext = f.suffix.lower()
        for group_name, exts in EXT_GROUPS.items():
            if ext in exts or (ext == "" and f.name.startswith("Dockerfile")):
                ext_hits[group_name] += 1
                break
        else:
            ext_hits["文本/其它"] += 1

        # 模块归类
        rel = f.relative_to(REPO).parts
        if len(rel) > 0:
            module = rel[0]
            module_hits[module] += 1
        file_detail[str(f.relative_to(REPO))] = file_dim_hits

# 输出报告
print("=" * 70)
print("ydsz-pmis 全仓库品牌残留摸底报告")
print("=" * 70)
print(f"\n【总览】")
print(f"  涉及文件数：{len(file_detail)}")
print(f"  涉及模块数：{len(module_hits)}")

print(f"\n【按维度统计】")
for dim, count in dim_hits.most_common():
    print(f"  {dim:25s} : {count:5d} 处  (分布于 {len(dim_files[dim])} 个文件)")

print(f"\n【按文件类型分布】")
for ext_name, count in ext_hits.most_common():
    print(f"  {ext_name:15s} : {count:4d} 个文件")

print(f"\n【按模块分布 Top 20】")
for mod, count in module_hits.most_common(20):
    print(f"  {mod:30s} : {count:4d} 个文件")

# 导出 JSON
report = {
    "summary": {
        "total_files_with_brand": len(file_detail),
        "total_modules": len(module_hits),
        "dimension_hits": dict(dim_hits),
        "dimension_files": {k: len(v) for k, v in dim_files.items()},
        "ext_distribution": dict(ext_hits),
        "module_distribution": dict(module_hits),
    },
    "file_detail": file_detail,
}
report_path = REPO / "scripts" / "brand-residue-report.json"
report_path.parent.mkdir(parents=True, exist_ok=True)
report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
print(f"\n详细报告已保存到：{report_path}")
