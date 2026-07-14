#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
P1-1 阶段：批量重命名 22 个 Ydsz*.java 文件（类名已无 Ydsz 前缀，仅文件名残留）。
同时清理 YdszJson.java 中两个同名 JsonParser 的 import 冲突。
"""
import pathlib
import shutil
import re

ROOT = pathlib.Path("d:/Code/ydsz/ydsz-pmis/ydsz-pmis-backend/ydsz-pmis-common/ydsz-pmis-common-json")

# 文件重命名映射
renamed = []
for f in list(ROOT.rglob("Ydsz*.java")):
    new_name = f.name[4:]  # 去掉 "Ydsz" 前缀
    new_path = f.with_name(new_name)
    if new_path.exists():
        print(f"  WARN: {new_path.name} already exists, skip {f.name}")
        continue
    shutil.move(str(f), str(new_path))
    renamed.append((f.name, new_path.name))

# 处理 YdszJson.java 中的 JsonParser import 冲突
# 删除 line 28 的 stream.JsonParser import（与 line 19 的 parser.JsonParser 同名冲突）
# 同步删除 createParser(String) 方法（依赖 stream.JsonParser.of，已无等价的 parser.JsonParser.of）
ydsz_json = ROOT / "YdszJson.java"
text = ydsz_json.read_text(encoding="utf-8")
orig = text

# 删除 stream.JsonParser import
text = re.sub(
    r"^import com\.njydsz\.pmis\.common\.json\.stream\.JsonParser;\n",
    "",
    text,
    flags=re.MULTILINE,
)

# 删除 createParser 方法（Javadoc + 签名 + body）
create_parser_pattern = re.compile(
    r"/\*\*\s*\n"
    r"\s*\*\s*创建流式解析器.*?\*/\s*\n"
    r"\s*public\s+static\s+JsonParser\s+createParser\s*\(\s*String\s+json\s*\)\s*\{[^}]*\}\s*\n",
    re.DOTALL,
)
text = create_parser_pattern.sub("", text)

# 同步修复"直接解析 JSON 到对象"语义：把 JsonParser.parse(json) 改为调用 parser 包下的 JsonParser（已经默认 import 进来）
# 实际上 line 19 已经 import 了 parser.JsonParser，无需任何修改

ydsz_json.write_text(text, encoding="utf-8")

# 同步把 YdszJson.java 文件名改为 Json.java（最后一步）
if ydsz_json.exists():
    json_main = ydsz_json.with_name("Json.java")
    if not json_main.exists():
        shutil.move(str(ydsz_json), str(json_main))
        renamed.append((ydsz_json.name, json_main.name))
    else:
        print(f"  WARN: {json_main.name} already exists, skip rename of {ydsz_json.name}")

# 同步更新所有引用 YdszJson.java 的 import 语句
import re
for f in ROOT.rglob("*.java"):
    txt = f.read_text(encoding="utf-8")
    new_txt = txt.replace("com.njydsz.pmis.common.json.YdszJson", "com.njydsz.pmis.common.json.Json")
    if new_txt != txt:
        f.write_text(new_txt, encoding="utf-8")
        print(f"  updated import in {f.name}")

print(f"== P1-1 重命名完成 ==")
print(f"文件重命名：{len(renamed)} 个")
for old, new in renamed:
    print(f"  {old} -> {new}")
