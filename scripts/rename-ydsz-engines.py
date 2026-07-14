#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
P0-4 阶段：重命名 ydsz-pmis-common-json 中残留的 4 个 Ydsz*Engine/Provider 类。
- 替换内容：4 个类标识符
- 重命名文件：4 个 .java 文件
- 同步清理：Javadoc @author ydsz-pmis-team（顺带完成 P1-2）
"""
import pathlib
import re
import shutil

ROOT = pathlib.Path("d:/Code/ydsz/ydsz-pmis/ydsz-pmis-backend/ydsz-pmis-common/ydsz-pmis-common-json")

# 4 个待重命名映射
RENAME = {
    "YdszSerializerEngine": "SerializerEngine",
    "YdszDeserializerEngine": "DeserializerEngine",
    "YdszSerializationProvider": "SerializationProvider",
    "YdszDeserializationProvider": "DeserializationProvider",
}

# 文件重命名映射
FILE_RENAME = {
    "YdszSerializerEngine.java": "SerializerEngine.java",
    "YdszDeserializerEngine.java": "DeserializerEngine.java",
    "YdszSerializationProvider.java": "SerializationProvider.java",
    "YdszDeserializationProvider.java": "DeserializationProvider.java",
}

# 1) 内容替换
java_files = list(ROOT.rglob("*.java"))
touched_files = set()
total_replacements = 0
for f in java_files:
    text = f.read_text(encoding="utf-8")
    orig = text
    for old, new in RENAME.items():
        # 用词边界 \b 防止误伤 YdszSerializationProviderX 这种未来扩展
        text = re.sub(rf"\b{re.escape(old)}\b", new, text)
    if text != orig:
        f.write_text(text, encoding="utf-8")
        touched_files.add(f)
        total_replacements += orig.count(RENAME.get("YdszSerializerEngine", "SerializerEngine")) + 0  # 占位
        # 重新计算实际替换数
        for old in RENAME:
            total_replacements += orig.count(old)

# 2) 文件重命名
renamed = []
for old_name, new_name in FILE_RENAME.items():
    for f in ROOT.rglob(old_name):
        new_path = f.with_name(new_name)
        shutil.move(str(f), str(new_path))
        renamed.append((str(f), str(new_path)))

# 3) 顺带清理 Javadoc @author ydsz-pmis-team 残留
author_count = 0
for f in ROOT.rglob("*.java"):
    text = f.read_text(encoding="utf-8")
    orig = text
    # 删除整行
    text = re.sub(r"\n\s*\*\s*@author\s+ydsz-pmis-team\s*\n", "\n", text)
    if text != orig:
        f.write_text(text, encoding="utf-8")
        author_count += 1

print(f"== P0-4 重命名完成 ==")
print(f"内容替换：{len(touched_files)} 个文件（涵盖 4 个核心类 + 全部引用方）")
print(f"文件重命名：{len(renamed)} 个")
for old, new in renamed:
    print(f"  {pathlib.Path(old).name} -> {pathlib.Path(new).name}")
print(f"@author 清理：{author_count} 个文件")
