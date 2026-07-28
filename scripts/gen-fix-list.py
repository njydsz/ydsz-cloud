# -*- coding: utf-8 -*-
"""
生成精确修复清单：只标记真正的乱码（截断词、误替换标点、缺失括号），
排除合法的句末句号。

判断规则：
- 句号前的中文词不完整（如「匹」「深」「推」「创」「捕」「启」「消」「获」「返」「排」「接」）
  → 缺字，需补全
- 句号替换了逗号（「。支持」「。本类」「。这种」等）
  → 应改回逗号
- 句号替换了右括号「）」（「（零拷贝优化版。」「（防止 DoS 攻击。」等）
  → 应改回「）」
- 句号出现在中文+大写字母之间（「创。ASM」「单。ConcurrentHashMap」等）
  → 应删除句号或改回逗号
"""
import pathlib
import re

ROOT = pathlib.Path(r"d:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-common\ydsz-common-json\src\main\java")

# 已知的截断词模式（句号前是单字，明显缺字）
# 格式：(损坏模式, 修复后)
TRUNCATION_RULES = [
    # AsmCodecCache.java
    ("避免重复生成字节点/p>", "避免重复生成字节码。</p>"),
    ("为 Bean 类生成专用序列化。反序列化。", "为 Bean 类生成专用序列化器、反序列化器。"),
    ("获取或创。ASM 序列化器", "获取或创建 ASM 序列化器"),
    ("使用单。ConcurrentHashMap.get() 替代 containsKey() + get() 双次查找", "使用单独的 ConcurrentHashMap.get() 替代 containsKey() + get() 双次查找"),
    ("（非类型参数版本，接。Class<?>。", "（非类型参数版本，接受 Class<?>）"),
    ("预解析的序列化器（可。AsmSerializer<?>。", "预解析的序列化器（可为 AsmSerializer<?>）"),
    ("泛型辅助方法：捕。AsmSerializer", "泛型辅助方法：捕获 AsmSerializer"),
    ("获取或创。ASM 反序列化。", "获取或创建 ASM 反序列化器。"),
    ("检查是否启。ASM 优化", "检查是否启用 ASM 优化"),

    # ZeroCopyDeserializer.java
    ("从池中获。ArrayList", "从池中获取 ArrayList"),
    ("从池中获。LinkedHashMap", "从池中获取 LinkedHashMap"),

    # DeserializationProvider.java
    ("反序列化提供者（零拷贝优化版。", "反序列化提供者（零拷贝优化版）"),
    ("快速路。- 简单对象（。 字段）直接内联解。", "快速路径 - 简单对象（基本类型字段）直接内联解析"),
    ("Creator 模式支持 - 自定义构造函数反序列。", "Creator 模式支持 - 自定义构造函数反序列化"),
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

    # BeanReader.java
    ("直接 char[] 解析，消。Map 中转", "直接 char[] 解析，消除 Map 中转"),
    ("O(1) 快速字段匹。", "O(1) 快速字段匹配"),
    ("嵌套对象递归解析，支持任意深。", "嵌套对象递归解析，支持任意深度"),
    ("集合/Map 完整支持，自动类型推。", "集合/Map 完整支持，自动类型推断"),
    ("字段读取器数。", "字段读取器数组"),
    ("复杂类型（嵌套对象、集合等。", "复杂类型（嵌套对象、集合等）"),

    # ObjectReader.java
    ("字段 setter 预计算，避免运行时查。", "字段 setter 预计算，避免运行时查找"),
    ("嵌套对象递归解析，支持任意深。", "嵌套对象递归解析，支持任意深度"),
    ("集合/Map 完整支持，自动类型转。", "集合/Map 完整支持，自动类型转换"),
    ("获取可序列化字段（排。static/transient。", "获取可序列化字段（排除 static/transient）"),
    ("检查对象结。", "检查对象结构"),
    ("字段读取。", "字段读取器"),
    ("未知类型，跳。", "未知类型，跳过"),

    # SerializerRegistry.java
    ("如果已注册返。true", "如果已注册返回 true"),
]

print("=" * 80)
print("精确修复清单 - 仅包含确定需要修复的乱码")
print("=" * 80)
print(f"共 {len(TRUNCATION_RULES)} 条精确替换规则")
print()

# 统计每条规则在哪些文件命中
for damaged, fixed in TRUNCATION_RULES:
    hit_files = []
    for f in sorted(ROOT.rglob("*.java")):
        try:
            content = f.read_text(encoding="utf-8")
            if damaged in content:
                hit_files.append(f.name)
        except UnicodeDecodeError:
            pass
    if hit_files:
        print(f"  [{', '.join(hit_files)}]")
        print(f"    损坏: {damaged}")
        print(f"    修复: {fixed}")
        print()
    else:
        print(f"  [未命中] {damaged}")
        print()

print("=" * 80)
print("清单生成完成")
print("=" * 80)
