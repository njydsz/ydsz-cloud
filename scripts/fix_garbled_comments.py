#!/usr/bin/env python3
"""Fix garbled Chinese comments in SerializationProvider.java."""
import pathlib

file_path = pathlib.Path(r'd:/Code/ydsz/ydsz-pmis/ydsz-pmis-backend/ydsz-pmis-common/ydsz-pmis-common-json/src/main/java/com/njydsz/pmis/common/json/provider/SerializationProvider.java')
content = file_path.read_text(encoding='utf-8')

replacements = [
    ('精确容量预分。- 基于对象结构预估 JSON 大小，避免StringBuilder 扩容', '精确容量预分配 - 基于对象结构预估 JSON 大小，避免 StringBuilder 扩容'),
    ('快速数字编。- 直接写入字符数组，避免方法调用和边界检。', '快速数字编码 - 直接写入字符数组，避免方法调用和边界检查'),
    ('UTF-8 编码优化 - 针对 ASCII 字符集优。', 'UTF-8 编码优化 - 针对 ASCII 字符集优化'),
    ('热路径内。- 减少虚方法调用和方法调用。', '热路径内联 - 减少虚方法调用和方法调用开销'),
    ('StringBuilder 池最大容。', 'StringBuilder 池最大容量'),
    ('。JSON 。StringBuilder 初始容量（适合简。Bean。', '小 JSON StringBuilder 初始容量（适合简单 Bean）'),
    ('。JSON 。StringBuilder 初始容量（适合一。Bean。', '中 JSON StringBuilder 初始容量（适合一般 Bean）'),
    ('。JSON 。StringBuilder 初始容量（适合大集。复杂嵌套。', '大 JSON StringBuilder 初始容量（适合大集合/复杂嵌套）'),
    ('优化策略。/p>', '优化策略：</p>'),
    ('默认使用 MEDIUM_SB_CAPACITY。096），适合大多数场。', '默认使用 MEDIUM_SB_CAPACITY（4096），适合大多数场景'),
    ('序列化完成后，如果容量超。MAX_SB_CAPACITY。5536），缩容。MEDIUM_SB_CAPACITY', '序列化完成后，如果容量超过 MAX_SB_CAPACITY（65536），缩容到 MEDIUM_SB_CAPACITY'),
    ('FastJSON2 JSONWriter 池（ThreadLocal 复用。', 'JSONWriter 池（ThreadLocal 复用）'),
    ('循环引用检。- 已序列化对象集合（使。IdentityHashMap 保证引用比较。', '循环引用检测 - 已序列化对象集合（使用 IdentityHashMap 保证引用比较）'),
    ('当前视图类（用于字段过滤，ThreadLocal 传递上下文。', '当前视图类（用于字段过滤，ThreadLocal 传递上下文）'),
    ('避免每次列表序列化都查。ConcurrentHashMap。', '避免每次列表序列化都查找 ConcurrentHashMap）'),
    ('配。CACHED_LIST_SERIALIZER 使用。', '配合 CACHED_LIST_SERIALIZER 使用）'),
    ('是否输出 null 值（ThreadLocal。', '是否输出 null 值（ThreadLocal）'),
    ('是否格式化输出（ThreadLocal。', '是否格式化输出（ThreadLocal）'),
    ('枚举是否使用序号序列化（ThreadLocal。', '枚举是否使用序号序列化（ThreadLocal）'),
    ('清理当前线程。ThreadLocal 对象', '清理当前线程的 ThreadLocal 对象'),
    ('获取适合指定预估大小。StringBuilder（大小分级策略）', '获取适合指定预估大小的 StringBuilder（大小分级策略）'),
    ('根据预估。JSON 大小选择合适容量的 StringBuilder，避免：', '根据预估的 JSON 大小选择合适容量的 StringBuilder，避免：'),
    ('。JSON 使用于StringBuilder 浪费内存', '小 JSON 使用大 StringBuilder 浪费内存'),
    ('。JSON 使用于StringBuilder 导致多次扩容', '大 JSON 使用小 StringBuilder 导致多次扩容'),
    ('预估 。SMALL_SB_CAPACITY(1024)：小 JSON，适合简。Bean', '预估 < SMALL_SB_CAPACITY(1024)：小 JSON，适合简单 Bean'),
    ('预估 。MEDIUM_SB_CAPACITY(4096)：中 JSON，适合一。Bean', '预估 < MEDIUM_SB_CAPACITY(4096)：中 JSON，适合一般 Bean'),
    ('预估 。LARGE_SB_CAPACITY(16384)：大 JSON，适合大集。复杂嵌套', '预估 < LARGE_SB_CAPACITY(16384)：大 JSON，适合大集合/复杂嵌套'),
    ('预估 > LARGE_SB_CAPACITY：超。JSON，按需分配', '预估 > LARGE_SB_CAPACITY：超大 JSON，按需分配'),
    ('@param estimatedSize 预估。JSON 输出大小', '@param estimatedSize 预估的 JSON 输出大小'),
    ('@return 适合大小。StringBuilder', '@return 适合大小的 StringBuilder'),
    ('缩容保护：如果池。StringBuilder 过大', '缩容保护：如果池中 StringBuilder 过大'),
    ('扩容保护：如果预估大小超过当前容量，预分。', '扩容保护：如果预估大小超过当前容量，预分配'),
    ('序列化对。', '序列化对象'),
    ('快速路。：Bean 类型直接使用 ASM 序列化器，跳。StringBuilder 中转', '快速路径：Bean 类型直接使用 ASM 序列化器，跳过 StringBuilder 中转'),
    ('快速路。：Collection 类型直接使用 JSONWriter，跳。StringBuilder 中转', '快速路径：Collection 类型直接使用 JSONWriter，跳过 StringBuilder 中转'),
    ('优化：使。ThreadLocal 缓存的序列化器', '优化：使用 ThreadLocal 缓存的序列化器'),
    ('快速路。：Map 类型直接使用 JSONWriter', '快速路径：Map 类型直接使用 JSONWriter'),
    ('格式化序列化（带缩进。', '格式化序列化（带缩进）'),
    ('@param obj 要序列化的对。', '@param obj 要序列化的对象'),
    ('@param viewClass 视图。', '@param viewClass 视图类'),
    ('@return JSON 字符。', '@return JSON 字符串'),
    ('格式化输出使用较大预估大。', '格式化输出使用较大预估大小'),
    ('@param pretty 是否格式。', '@param pretty 是否格式化'),
    ('无视图过。', '无视图过滤'),
    ('@return true 如果使用了快速路。', '@return true 如果使用了快速路径'),
    ('检查是否可以使用快速路。', '检查是否可以使用快速路径'),
    ('优先使用 ASM 序列化器（直。getter 调用，无反射开销。', '优先使用 ASM 序列化器（直接 getter 调用，无反射开销）'),
    ('获取或创。BeanSerializer', '获取或创建 BeanSerializer'),
    ('有效字段数组（跳。shouldSkip 的字段）', '有效字段数组（跳过 shouldSkip 的字段）'),
    ('预估。JSON 大小', '预估的 JSON 大小'),
    ('获取或创。BeanSerializerInfo（FastJSON2 架构优化。', '获取或创建 BeanSerializerInfo（架构优化）'),
    ('@return JSON 字符。\n     */\n    public static String serialize(Object obj, long features)', '@return JSON 字符串\n     */\n    public static String serialize(Object obj, long features)'),
]

count = 0
for old, new in replacements:
    if old in content:
        content = content.replace(old, new)
        count += 1
    else:
        print(f'NOT FOUND: {old[:50]}...')

file_path.write_text(content, encoding='utf-8')
print(f'Done - {count} garbled comments fixed')
