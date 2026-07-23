#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
ydsz-pmis json/cache 模块类名回退脚本

将 json 模块的 Json* 类回退为 YdszJson* 前缀（24 个类），
将 cache 模块的 LocalCache* 类回退为 YdszCache* 前缀（5 个类）。

背景：2026-07-15 完成了去 Ydsz 化（YdszJson* -> Json* / YdszCache* -> LocalCache*），
2026-07-16 决策方向错误，需要回退为 Ydsz 前缀。

约束：
  - 使用词边界（\\b）匹配，避免误伤（如 JsonType 不能误伤 JsonTypeCode）
  - 按长度降序替换（先替换长名字，再替换短名字）
  - JsonParser 特殊处理：stream/JsonParser.java 不回退，parser/JsonParser.java 回退
  - 文件编码 UTF-8 无 BOM
  - 排除 .git/target/node_modules 目录
"""

import pathlib
import re

ROOT = pathlib.Path(r"d:\Code\ydsz\ydsz-pmis")

JSON_MODULE = ROOT / "ydsz-backend" / "ydsz-common" / "ydsz-common-json"
CACHE_MODULE = ROOT / "ydsz-backend" / "ydsz-common" / "ydsz-common-cache"

# json 模块 Java 源码根目录
JSON_BASE = JSON_MODULE / "src" / "main" / "java" / "com" / "njydsz" / "common" / "json"
# cache 模块 Java 源码根目录
CACHE_BASE = CACHE_MODULE / "src" / "main" / "java" / "com" / "njydsz" / "common" / "cache"

# 排除目录
EXCLUDE_DIRS = {".git", "target", "node_modules", ".idea", "build"}

# 文件扩展名白名单
# 注意：.json 也包含在内（native-image.json / spring-configuration-metadata.json 引用了类名）
EXTENSIONS = {".java", ".xml", ".yml", ".yaml", ".properties", ".imports", ".md", ".json"}

# ============================================================
# JSON 模块：Json* -> YdszJson*（词边界匹配，按长度降序）
# ============================================================
# 注意：\b 确保不误伤同类前缀的其他类
#   - \bJsonType\b 不会匹配 JsonTypeCode（C 是单词字符，无边界）
#   - \bJsonSchema\b 不会匹配 JsonSchemaTest（T 是单词字符，无边界）
#   - \bJson\b 不会匹配 JsonConfig（C 是单词字符，无边界）
JSON_REPLACEMENTS = [
    # 多字符类名（16+ 字符）
    (r"\bJsonPropertyOrder\b", "YdszJsonPropertyOrder"),
    # 14 字符
    (r"\bJsonCacheStats\b", "YdszJsonCacheStats"),
    (r"\bJsonVisibility\b", "YdszJsonVisibility"),
    # 12 字符
    (r"\bJsonSubTypes\b", "YdszJsonSubTypes"),      # 必须在 JsonSubType 之前
    (r"\bJsonTypeInfo\b", "YdszJsonTypeInfo"),
    (r"\bJsonProperties\b", "YdszJsonProperties"),
    (r"\bJsonException\b", "YdszJsonException"),
    # 11 字符
    (r"\bJsonSubType\b", "YdszJsonSubType"),
    (r"\bJsonBuilder\b", "YdszJsonBuilder"),
    (r"\bJsonCreator\b", "YdszJsonCreator"),
    (r"\bJsonSchema\b", "YdszJsonSchema"),
    (r"\bJsonFormat\b", "YdszJsonFormat"),
    (r"\bJsonConfig\b", "YdszJsonConfig"),
    (r"\bJsonModule\b", "YdszJsonModule"),
    (r"\bJsonMetrics\b", "YdszJsonMetrics"),
    # 9 字符
    (r"\bJsonArray\b", "YdszJsonArray"),
    (r"\bJsonObject\b", "YdszJsonObject"),
    # 8 字符
    (r"\bJsonPath\b", "YdszJsonPath"),
    (r"\bJsonType\b", "YdszJsonType"),                # 不会匹配 JsonTypeCode
    (r"\bJsonField\b", "YdszJsonField"),
    (r"\bJsonClass\b", "YdszJsonClass"),
    (r"\bJsonView\b", "YdszJsonView"),
    # JsonParser 特殊处理：见下方单独逻辑（stream 目录排除）
    # 裸 Json 类（必须放在最后；\b 确保不匹配 JsonConfig/JsonException 等）
    (r"\bJson\b", "YdszJson"),
]

# JsonParser 特殊处理：
#   1. FQN 替换（全局）：com.njydsz.common.json.parser.JsonParser -> ...YdszJsonParser
#   2. 裸名替换（排除 stream 目录的文件）：\bJsonParser\b -> YdszJsonParser
# 原因：stream/JsonParser.java 原本就不带 Ydsz 前缀，不能回退
JSON_PARSER_FQN_OLD = "com.njydsz.common.json.parser.JsonParser"
JSON_PARSER_FQN_NEW = "com.njydsz.common.json.parser.YdszJsonParser"
JSON_PARSER_BARE_PATTERN = r"\bJsonParser\b"
JSON_PARSER_BARE_REPLACEMENT = "YdszJsonParser"

# ============================================================
# Cache 模块：LocalCache* -> YdszCache*（词边界匹配，按长度降序）
# ============================================================
CACHE_REPLACEMENTS = [
    (r"\bLocalCacheAutoConfiguration\b", "YdszCacheAutoConfiguration"),
    (r"\bLocalCacheProperties\b", "YdszCacheProperties"),
    (r"\bLocalCacheManager\b", "YdszCacheManager"),
    (r"\bSpringLocalCache\b", "SpringYdszCache"),
    # LocalCache 词边界（不会匹配 LocalCacheManager/SpringLocalCache/LocalCacheProperties 等）
    (r"\bLocalCache\b", "YdszCache"),
]

# ============================================================
# 文件重命名映射
# ============================================================

JSON_FILE_RENAMES = [
    (JSON_BASE / "Json.java", JSON_BASE / "YdszJson.java"),
    (JSON_BASE / "annotation" / "JsonBuilder.java",
     JSON_BASE / "annotation" / "YdszJsonBuilder.java"),
    (JSON_BASE / "annotation" / "JsonClass.java",
     JSON_BASE / "annotation" / "YdszJsonClass.java"),
    (JSON_BASE / "annotation" / "JsonCreator.java",
     JSON_BASE / "annotation" / "YdszJsonCreator.java"),
    (JSON_BASE / "annotation" / "JsonField.java",
     JSON_BASE / "annotation" / "YdszJsonField.java"),
    (JSON_BASE / "annotation" / "JsonFormat.java",
     JSON_BASE / "annotation" / "YdszJsonFormat.java"),
    (JSON_BASE / "annotation" / "JsonPropertyOrder.java",
     JSON_BASE / "annotation" / "YdszJsonPropertyOrder.java"),
    (JSON_BASE / "annotation" / "JsonSubType.java",
     JSON_BASE / "annotation" / "YdszJsonSubType.java"),
    (JSON_BASE / "annotation" / "JsonSubTypes.java",
     JSON_BASE / "annotation" / "YdszJsonSubTypes.java"),
    (JSON_BASE / "annotation" / "JsonTypeInfo.java",
     JSON_BASE / "annotation" / "YdszJsonTypeInfo.java"),
    (JSON_BASE / "annotation" / "JsonView.java",
     JSON_BASE / "annotation" / "YdszJsonView.java"),
    (JSON_BASE / "annotation" / "JsonVisibility.java",
     JSON_BASE / "annotation" / "YdszJsonVisibility.java"),
    (JSON_BASE / "cache" / "JsonCacheStats.java",
     JSON_BASE / "cache" / "YdszJsonCacheStats.java"),
    (JSON_BASE / "config" / "JsonConfig.java",
     JSON_BASE / "config" / "YdszJsonConfig.java"),
    (JSON_BASE / "exception" / "JsonException.java",
     JSON_BASE / "exception" / "YdszJsonException.java"),
    (JSON_BASE / "jsonpath" / "JsonPath.java",
     JSON_BASE / "jsonpath" / "YdszJsonPath.java"),
    (JSON_BASE / "metric" / "JsonMetrics.java",
     JSON_BASE / "metric" / "YdszJsonMetrics.java"),
    (JSON_BASE / "module" / "JsonModule.java",
     JSON_BASE / "module" / "YdszJsonModule.java"),
    (JSON_BASE / "object" / "JsonArray.java",
     JSON_BASE / "object" / "YdszJsonArray.java"),
    (JSON_BASE / "object" / "JsonObject.java",
     JSON_BASE / "object" / "YdszJsonObject.java"),
    (JSON_BASE / "parser" / "JsonParser.java",
     JSON_BASE / "parser" / "YdszJsonParser.java"),
    (JSON_BASE / "schema" / "JsonSchema.java",
     JSON_BASE / "schema" / "YdszJsonSchema.java"),
    (JSON_BASE / "spring" / "JsonProperties.java",
     JSON_BASE / "spring" / "YdszJsonProperties.java"),
    (JSON_BASE / "type" / "JsonType.java",
     JSON_BASE / "type" / "YdszJsonType.java"),
]

CACHE_FILE_RENAMES = [
    (CACHE_BASE / "LocalCache.java", CACHE_BASE / "YdszCache.java"),
    (CACHE_BASE / "spring" / "LocalCacheAutoConfiguration.java",
     CACHE_BASE / "spring" / "YdszCacheAutoConfiguration.java"),
    (CACHE_BASE / "spring" / "LocalCacheManager.java",
     CACHE_BASE / "spring" / "YdszCacheManager.java"),
    (CACHE_BASE / "spring" / "LocalCacheProperties.java",
     CACHE_BASE / "spring" / "YdszCacheProperties.java"),
    (CACHE_BASE / "spring" / "SpringLocalCache.java",
     CACHE_BASE / "spring" / "SpringYdszCache.java"),
]


def is_excluded(path: pathlib.Path) -> bool:
    """检查路径是否在排除目录中"""
    for part in path.parts:
        if part in EXCLUDE_DIRS:
            return True
    return False


def should_process(path: pathlib.Path) -> bool:
    """判断文件是否需要处理"""
    if path.suffix not in EXTENSIONS:
        return False
    if is_excluded(path):
        return False
    return True


def is_stream_file(path: pathlib.Path) -> bool:
    """判断文件是否在 json 模块的 stream 目录中"""
    try:
        rel = path.relative_to(JSON_BASE)
        return "stream" in rel.parts
    except ValueError:
        return False


def collect_files():
    """收集所有需要处理的文件"""
    files = []
    for f in ROOT.rglob("*"):
        if f.is_file() and should_process(f):
            files.append(f)
    return files


def apply_replacements(content: str, file_path: pathlib.Path) -> tuple:
    """应用所有替换规则，返回 (新内容, 替换次数)"""
    count = 0

    # 1. JSON 模块：先替换 JsonParser 的 FQN（全局）
    if JSON_PARSER_FQN_OLD in content:
        n = content.count(JSON_PARSER_FQN_OLD)
        content = content.replace(JSON_PARSER_FQN_OLD, JSON_PARSER_FQN_NEW)
        count += n

    # 2. JSON 模块：替换其他 JSON 类名（词边界匹配）
    for pattern, replacement in JSON_REPLACEMENTS:
        new_content, n = re.subn(pattern, replacement, content)
        if n > 0:
            content = new_content
            count += n

    # 3. JSON 模块：裸 JsonParser 替换（排除 stream 目录的文件）
    if not is_stream_file(file_path):
        new_content, n = re.subn(
            JSON_PARSER_BARE_PATTERN, JSON_PARSER_BARE_REPLACEMENT, content)
        if n > 0:
            content = new_content
            count += n

    # 4. Cache 模块：替换 Cache 类名（词边界匹配）
    for pattern, replacement in CACHE_REPLACEMENTS:
        new_content, n = re.subn(pattern, replacement, content)
        if n > 0:
            content = new_content
            count += n

    return content, count


def rename_files():
    """重命名 Java 文件"""
    renamed = []
    skipped = []
    all_renames = JSON_FILE_RENAMES + CACHE_FILE_RENAMES
    for old_path, new_path in all_renames:
        if old_path.exists():
            old_path.rename(new_path)
            renamed.append((str(old_path.name), str(new_path.name)))
        else:
            skipped.append(str(old_path))
    return renamed, skipped


def verify_source_files():
    """验证所有待重命名的源文件都存在"""
    missing = []
    all_renames = JSON_FILE_RENAMES + CACHE_FILE_RENAMES
    for old_path, _ in all_renames:
        if not old_path.exists():
            missing.append(str(old_path))
    return missing


def main():
    print("=" * 70)
    print(" ydsz-pmis json/cache 模块类名回退（Ydsz 前缀恢复）")
    print("=" * 70)

    # 步骤 0：验证源文件存在性
    print("\n[0/3] 验证源文件存在性...")
    missing = verify_source_files()
    if missing:
        print("  WARNING: 以下文件不存在，将跳过重命名：")
        for m in missing:
            print(f"    {m}")
    else:
        print("  所有待重命名文件均存在 ✓")

    # 步骤 1：内容替换
    files = collect_files()
    print(f"\n[1/3] 扫描 {len(files)} 个文件进行内容替换...")
    modified = 0
    total_replacements = 0
    modified_files = []
    for f in files:
        try:
            content = f.read_text(encoding="utf-8")
        except (UnicodeDecodeError, PermissionError):
            continue

        new_content, count = apply_replacements(content, f)
        if count > 0:
            f.write_text(new_content, encoding="utf-8")
            modified += 1
            total_replacements += count
            modified_files.append((f, count))

    print(f"  共修改 {modified} 个文件，{total_replacements} 处替换")
    # 打印修改的文件列表（最多显示前 50 个）
    show = min(len(modified_files), 50)
    for f, c in modified_files[:show]:
        try:
            rel = f.relative_to(ROOT)
        except ValueError:
            rel = f
        print(f"    {rel} ({c} 处)")
    if len(modified_files) > show:
        print(f"    ... 还有 {len(modified_files) - show} 个文件")

    # 步骤 2：文件重命名
    print(f"\n[2/3] 重命名 Java 文件...")
    renamed, skipped = rename_files()
    print(f"  共重命名 {len(renamed)} 个文件：")
    for old_name, new_name in renamed:
        print(f"    {old_name} -> {new_name}")
    if skipped:
        print(f"  跳过 {len(skipped)} 个不存在的文件")

    # 步骤 3：完成
    print(f"\n[3/3] 完成")
    print(f"  修改文件数: {modified}")
    print(f"  替换引用数: {total_replacements}")
    print(f"  重命名文件数: {len(renamed)}")
    print("\n" + "=" * 70)
    print(" 请运行 mvn clean compile 验证编译。")
    print("=" * 70)


if __name__ == "__main__":
    main()
