# -*- coding: utf-8 -*-
"""
批量修复 ydsz-common-json 模块的中文注释乱码。

修复策略：
1. 应用 39 条精确的字符串替换规则
2. 每条规则在所有 .java 文件中 replace_all
3. 保留 UTF-8 无 BOM 编码
4. 修复后输出统计

遵循 .trae/rules/prefer-python-over-powershell.md 规则
"""
import pathlib
import sys

ROOT = pathlib.Path(r"d:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-common\ydsz-common-json\src\main\java")

# 39 条精确替换规则（按损坏字符串排序，避免冲突）
REPLACEMENTS = [
    # === AsmCodecCache.java ===
    ("避免重复生成字节点/p>", "避免重复生成字节码。</p>"),
    ("为 Bean 类生成专用序列化。反序列化。", "为 Bean 类生成专用序列化器、反序列化器。"),
    ("获取或创。ASM 序列化器", "获取或创建 ASM 序列化器"),
    ("使用单。ConcurrentHashMap.get() 替代 containsKey() + get() 双次查找",
     "使用单独的 ConcurrentHashMap.get() 替代 containsKey() + get() 双次查找"),
    ("（非类型参数版本，接。Class<?>。", "（非类型参数版本，接受 Class<?>）"),
    ("预解析的序列化器（可。AsmSerializer<?>。", "预解析的序列化器（可为 AsmSerializer<?>）"),
    ("泛型辅助方法：捕。AsmSerializer", "泛型辅助方法：捕获 AsmSerializer"),
    ("获取或创。ASM 反序列化。", "获取或创建 ASM 反序列化器。"),
    ("检查是否启。ASM 优化", "检查是否启用 ASM 优化"),

    # === ZeroCopyDeserializer.java ===
    ("从池中获。ArrayList", "从池中获取 ArrayList"),
    ("从池中获。LinkedHashMap", "从池中获取 LinkedHashMap"),

    # === DeserializationProvider.java ===
    ("反序列化提供者（零拷贝优化版。", "反序列化提供者（零拷贝优化版）"),
    ("快速路。- 简单对象（。 字段）直接内联解。",
     "快速路径 - 简单对象（基本类型字段）直接内联解析"),
    ("Creator 模式支持 - 自定义构造函数反序列。",
     "Creator 模式支持 - 自定义构造函数反序列化"),
    ("检查缓存- 查找已编译的反序列化。", "检查缓存 - 查找已编译的反序列化器"),
    ("查找已编译的反序列化。", "查找已编译的反序列化器"),
    ("避免每次反序列化都重新查找策略链。", "避免每次反序列化都重新查找策略链）"),
    ("（零拷贝优化版。", "（零拷贝优化版）"),
    ("基本类型直接判断（无需缓存查找开销。", "基本类型直接判断（无需缓存查找开销）"),
    ("PRIMITIVE/OBJECT/MAP/LIST 的都。BEAN", "PRIMITIVE/OBJECT/MAP/LIST 的都不是 BEAN"),
    ("最大长度限制（防止 DoS 攻击。", "最大长度限制（防止 DoS 攻击）"),
    ("验证 JSON 深度（防止栈溢出攻击。", "验证 JSON 深度（防止栈溢出攻击）"),
    ("则根据 JSON 中的类型属性。", "则根据 JSON 中的类型属性值"),
    ("@param json JSON 字符。", "@param json JSON 字符串"),
    ("如果不支持多态返回基。", "如果不支持多态返回基类"),

    # === BeanReader.java + ObjectReader.java（共享规则） ===
    ("直接 char[] 解析，消。Map 中转", "直接 char[] 解析，消除 Map 中转"),
    ("O(1) 快速字段匹。", "O(1) 快速字段匹配"),
    ("嵌套对象递归解析，支持任意深。", "嵌套对象递归解析，支持任意深度"),
    ("集合/Map 完整支持，自动类型推。", "集合/Map 完整支持，自动类型推断"),
    ("字段读取器数。", "字段读取器数组"),
    ("复杂类型（嵌套对象、集合等。", "复杂类型（嵌套对象、集合等）"),

    # === ObjectReader.java ===
    ("字段 setter 预计算，避免运行时查。", "字段 setter 预计算，避免运行时查找"),
    ("集合/Map 完整支持，自动类型转。", "集合/Map 完整支持，自动类型转换"),
    ("获取可序列化字段（排。static/transient。", "获取可序列化字段（排除 static/transient）"),
    ("检查对象结。", "检查对象结构"),
    ("字段读取。", "字段读取器"),
    ("未知类型，跳。", "未知类型，跳过"),

    # === SerializerRegistry.java ===
    ("如果已注册返。true", "如果已注册返回 true"),
]

print("=" * 80)
print("ydsz-common-json 中文乱码批量修复")
print("=" * 80)
print(f"共 {len(REPLACEMENTS)} 条替换规则")
print()

total_replacements = 0
file_stats = {}

for f in sorted(ROOT.rglob("*.java")):
    try:
        content = f.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        print(f"[SKIP] {f.name} 编码无法识别")
        continue

    original = content
    file_replacements = 0

    for damaged, fixed in REPLACEMENTS:
        if damaged in content:
            count = content.count(damaged)
            content = content.replace(damaged, fixed)
            file_replacements += count
            total_replacements += count

    if content != original:
        # 写回，UTF-8 无 BOM
        f.write_text(content, encoding="utf-8")
        file_stats[f.name] = file_replacements
        print(f"  [FIXED] {f.name}: {file_replacements} 处替换")

print()
print(f"共修改 {len(file_stats)} 个文件，{total_replacements} 处替换")
print()
print("=" * 80)
print("修复完成")
print("=" * 80)
