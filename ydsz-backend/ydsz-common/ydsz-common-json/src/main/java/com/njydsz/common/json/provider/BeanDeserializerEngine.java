package com.njydsz.common.json.provider;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.njydsz.common.json.annotation.YdszJsonBuilder;
import com.njydsz.common.json.asm.AsmDeserializer;
import com.njydsz.common.json.bytecode.ZeroCopyDeserializer;
import com.njydsz.common.json.cache.AsmCodecCache;
import com.njydsz.common.json.parser.YdszJsonParser;
import com.njydsz.common.json.util.JsonTypeUtils;
import com.njydsz.common.json.reader.BeanReader;
import com.njydsz.common.json.reader.JSONReader;

/**
 * Bean 反序列化策略引擎。
 *
 * <p>负责 Bean 对象的反序列化策略选择与执行，内部实现了多级降级的反序列化路径，
 * 按性能从高到低依次尝试：
 *
 * <h3>反序列化路径（优先级从高到低）</h3>
 * <ol>
 *   <li><b>ASM 字节码路径</b>：通过 {@link AsmCodecCache} 动态生成反序列化器，
 *       直接操作字段偏移量，无反射开销</li>
 *   <li><b>BeanReader 路径</b>：针对简单 Bean（字段全为基本类型）的轻量级反射读取</li>
 *   <li><b>@YdszJsonCreator 路径</b>：通过注解标记的构造函数创建实例</li>
 *   <li><b>Builder 模式路径</b>：通过 {@code @YdszJsonBuilder} 或自动检测内部 Builder</li>
 *   <li><b>ZeroCopyDeserializer 路径</b>：零拷贝 char[] 直接解析</li>
 *   <li><b>降级路径</b>：解析为 Map 或 List 返回</li>
 * </ol>
 *
 * <h3>列表反序列化</h3>
 * <p>列表场景额外提供基于 ASM 和 ZeroCopy 的批量反序列化，
 * 通过预估容量和跳过异常元素保证吞吐量。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see AsmCodecCache
 * @see BeanReader
 * @see CreatorResolver
 * @see BuilderResolver
 * @see ZeroCopyDeserializer
 */
final class BeanDeserializerEngine {

    private BeanDeserializerEngine() {
        throw new UnsupportedOperationException();
    }

    /**
     * 零拷贝 Bean 反序列化（返回 Object）。
     *
     * <p>委托给 {@link #deserializeBeanZeroCopy(String, Class)} 的便捷方法。
     *
     * @param json  JSON 字符串
     * @param clazz 目标类
     * @return 反序列化后的实例
     */
    static Object deserializeBeanZeroCopyAsObject(String json, Class<?> clazz) {
        return deserializeBeanZeroCopy(json, clazz);
    }

    /**
     * 零拷贝 Bean 反序列化（泛型版）。
     *
     * <p>按多级降级策略依次尝试 ASM → BeanReader → Creator → Builder → ZeroCopy → Map 降级。
     * 每条路径失败后自动回退到下一条，确保最终能返回结果。
     *
     * @param json  JSON 字符串
     * @param clazz 目标类
     * @param <T>   目标类型
     * @return 反序列化后的实例
     */
    static <T> T deserializeBeanZeroCopy(String json, Class<T> clazz) {
        // Record 类反序列化路径：使用 canonical constructor
        if (clazz.isRecord()) {
            return deserializeRecord(json, clazz);
        }

        // ASM 优化路径：使用字节码生成的反序列化器
        String trimmed = json.strip();
        if (trimmed.startsWith("{") &&
            !clazz.isAssignableFrom(List.class) &&
            !clazz.isAssignableFrom(Map.class) &&
            !clazz.isArray() &&
            !clazz.isInterface()) {
            try {
                AsmDeserializer<T> asmDeserializer =
                    AsmCodecCache.getOrCreateDeserializer(clazz);
                if (asmDeserializer != null) {
                    JSONReader reader =
                        new JSONReader(json);
                    return asmDeserializer.deserialize(reader);
                }
            } catch (Exception e) {
                // ASM 生成失败，回退到 BeanReader
            }
        }

        // BeanReader 路径：仅对简单 Bean 使用（无嵌套对象）
        if (trimmed.startsWith("{") &&
            !clazz.isAssignableFrom(List.class) &&
            !clazz.isAssignableFrom(Map.class) &&
            !clazz.isArray() &&
            !clazz.isInterface() &&
            isSimpleBean(clazz)) {
            try {
                JSONReader reader =
                    new JSONReader(json);
                BeanReader<T> beanReader = BeanReader.getOrCreate(clazz);
                return beanReader.readObject(reader);
            } catch (Exception e) {
                // 回退到原有逻辑
            }
        }

        // 原有逻辑：@YdszJsonCreator、Builder 模式支持
        Constructor<?> creatorConstructor = CreatorResolver.findCreatorConstructor(clazz);

        if (creatorConstructor != null) {
            return clazz.cast(CreatorResolver.deserializeWithCreator(json, creatorConstructor));
        }

        YdszJsonBuilder builderAnnotation = clazz.getAnnotation(YdszJsonBuilder.class);
        if (builderAnnotation != null && builderAnnotation.enable()) {
            return BuilderResolver.deserializeWithBuilder(json, clazz, builderAnnotation);
        }

        Class<?> innerBuilderClass = BuilderResolver.findInnerBuilderClass(clazz);
        if (innerBuilderClass != null) {
            YdszJsonBuilder innerAnnotation = innerBuilderClass.getAnnotation(YdszJsonBuilder.class);
            if (innerAnnotation == null) {
                innerAnnotation = BuilderResolver.createDefaultBuilderAnnotation();
            }
            return BuilderResolver.deserializeWithInnerBuilder(json, clazz, innerBuilderClass, innerAnnotation);
        }

        try {
            ZeroCopyDeserializer.BeanDeserializer deserializer =
                ZeroCopyDeserializer.getDeserializer(clazz);
            return clazz.cast(deserializer.deserialize(json));
        } catch (Exception e) {
            if (trimmed.startsWith("[")) {
                return clazz.cast(YdszJsonParser.parseArray(json));
            } else {
                return clazz.cast(YdszJsonParser.parseObject(json));
            }
        }
    }

    /**
     * 快速反序列化 Bean 列表。
     *
     * <p>优先使用 ASM 反序列化器（批量解析性能最佳），失败时降级为 ZeroCopy 路径。
     *
     * @param json          JSON 数组字符串
     * @param elementClass  列表元素类型
     * @param <E>           元素类型
     * @return 反序列化后的列表
     */
    static <E> List<E> deserializeBeanListFast(String json, Class<E> elementClass) {
        // Record 类列表反序列化：逐个使用 canonical constructor
        if (elementClass.isRecord()) {
            List<Object> rawList = YdszJsonParser.parseArray(json);
            List<E> result = new ArrayList<>(rawList.size());
            for (Object item : rawList) {
                if (item == null) {
                    result.add(null);
                } else {
                    result.add(deserializeRecord(SerializationProvider.serialize(item), elementClass));
                }
            }
            return result;
        }

        // 优先使用 ASM 反序列化器
        AsmDeserializer<E> asmDeserializer = null;
        try {
            asmDeserializer = AsmCodecCache.getOrCreateDeserializer(elementClass);
        } catch (Exception ignored) {}

        if (asmDeserializer != null) {
            return deserializeBeanListWithAsm(json, elementClass, asmDeserializer);
        }

        // 回退到 ZeroCopyDeserializer
        return deserializeBeanListWithZeroCopy(json, elementClass);
    }

    /**
     * 使用 ASM 反序列化器批量解析 JSON 数组。
     *
     * <p>使用 {@link JSONReader} 池化读取器，预估列表容量减少 ArrayList 扩容。
     * 单个元素解析失败时跳过并填充 null，不中断整体解析。
     *
     * @param json            JSON 数组字符串
     * @param elementClass    元素类型
     * @param asmDeserializer ASM 反序列化器
     * @param <E>             元素类型
     * @return 反序列化后的列表
     */
    static <E> List<E> deserializeBeanListWithAsm(String json, Class<E> elementClass,
            AsmDeserializer<E> asmDeserializer) {
        JSONReader reader =
            JSONReader.getPooledReader(json);

        try {
            reader.skipWhitespace();
            if (reader.isEnd() || reader.readChar() != '[') {
                return new ArrayList<>();
            }

            // 预估列表大小（基于 JSON 字符串长度），减少 ArrayList 扩容
            int estimatedSize = Math.max(10, json.length() / 80);
            List<E> result = new ArrayList<>(estimatedSize);

            while (true) {
                reader.skipWhitespace();
                if (reader.isEnd()) break;

                char c = reader.peekChar();
                if (c == ']') {
                    reader.readChar();
                    break;
                }
                if (c == ',') {
                    reader.readChar();
                    continue;
                }

                if (reader.isNull()) {
                    reader.readNull();
                    result.add(null);
                    continue;
                }

                try {
                    E element = asmDeserializer.deserialize(reader);
                    result.add(element);
                } catch (Exception e) {
                    reader.skipValue();
                    result.add(null);
                }
            }

            return result;
        } finally {
            JSONReader.returnPooledReader(reader);
        }
    }

    /**
     * 使用 ZeroCopyDeserializer 批量解析 JSON 数组。
     *
     * <p>直接操作 char[] 避免字符串拷贝，通过大括号深度跟踪定位每个对象边界。
     * ASM 不可用时的降级路径。
     *
     * @param json          JSON 数组字符串
     * @param elementClass  元素类型
     * @param <E>           元素类型
     * @return 反序列化后的列表
     */
    static <E> List<E> deserializeBeanListWithZeroCopy(String json, Class<E> elementClass) {
        char[] chars = json.toCharArray();
        int len = chars.length;
        int pos = 0;

        while (pos < len && chars[pos] <= ' ') pos++;
        if (pos >= len || chars[pos] != '[') {
            return new ArrayList<>();
        }
        pos++;

        List<E> result = new ArrayList<>();
        ZeroCopyDeserializer.BeanDeserializer deserializer = null;
        try {
            deserializer = ZeroCopyDeserializer.getDeserializer(elementClass);
        } catch (Exception ignored) {}

        while (pos < len) {
            while (pos < len && chars[pos] <= ' ') pos++;
            if (pos >= len) break;

            char c = chars[pos];
            if (c == ']') break;
            if (c == ',') { pos++; continue; }

            if (c == 'n' && pos + 4 <= len && chars[pos+1] == 'u' && chars[pos+2] == 'l' && chars[pos+3] == 'l') {
                result.add(null);
                pos += 4;
                continue;
            }

            if (deserializer != null && c == '{') {
                try {
                    E element = elementClass.cast(deserializer.deserialize(chars, pos, len - pos));
                    result.add(element);

                    int depth = 0;
                    while (pos < len) {
                        char ch = chars[pos];
                        if (ch == '{') depth++;
                        else if (ch == '}') {
                            depth--;
                            if (depth == 0) { pos++; break; }
                        } else if (ch == '"') {
                            pos = skipStringValue(chars, pos, len);
                            continue;
                        }
                        pos++;
                    }
                    continue;
                } catch (Exception ignored) {}
            }

            int depth = 0;
            while (pos < len) {
                char ch = chars[pos];
                if (ch == '{' || ch == '[') depth++;
                else if (ch == '}' || ch == ']') {
                    depth--;
                    if (depth <= 0) { pos++; break; }
                } else if (ch == '"') {
                    pos = skipStringValue(chars, pos, len);
                    continue;
                } else if (ch == ',' && depth == 0) {
                    break;
                }
                pos++;
            }
            result.add(null);
        }

        return result;
    }

    /**
     * 零拷贝解析 JSON 数组为 Object 列表。
     *
     * <p>委托给 {@link ZeroCopyDeserializer#parseArrayChars}，失败时降级为 {@link YdszJsonParser#parseArray}。
     *
     * @param json          JSON 数组字符串
     * @param elementClass  元素类型（用于 ZeroCopy 类型推断）
     * @return 解析后的列表
     */
    static List<Object> deserializeArrayZeroCopy(String json, Class<?> elementClass) {
        try {
            char[] chars = json.toCharArray();
            return ZeroCopyDeserializer.parseArrayChars(chars, 0, chars.length, elementClass);
        } catch (Exception e) {
            return YdszJsonParser.parseArray(json);
        }
    }

    /**
     * 安全跳过 JSON 字符串值（从开引号到闭引号）。
     *
     * <p>正确处理转义序列，包括 {@code \\"} （转义引号）和 {@code \\\\} （转义反斜杠），
     * 避免字符串内的 {@code {} 或 {@code "} 干扰深度跟踪。
     *
     * @param chars JSON 字符数组
     * @param startPos 开始位置（指向开引号）
     * @param len 字符数组总长度
     * @return 闭引号后的下一个位置
     */
    private static int skipStringValue(char[] chars, int startPos, int len) {
        int pos = startPos + 1; // 跳过开引号
        while (pos < len) {
            if (chars[pos] == '\\') {
                pos += 2; // 跳过转义符和被转义的字符
            } else if (chars[pos] == '"') {
                return pos + 1; // 返回闭引号后的位置
            } else {
                pos++;
            }
        }
        return pos;
    }

    /**
     * 反序列化 Record 类。
     *
     * <p>Record 类不可变，使用 canonical constructor 创建实例。
     * 先解析 JSON 为 Map，再按组件顺序提取值并调用 canonical constructor。
     *
     * @param json JSON 字符串
     * @param clazz Record 类
     * @param <T> 目标类型
     * @return 反序列化后的 Record 实例
     */
    @SuppressWarnings("unchecked")
    static <T> T deserializeRecord(String json, Class<T> clazz) {
        Map<String, Object> map = YdszJsonParser.parseObject(json);
        RecordComponent[] components = clazz.getRecordComponents();
        Class<?>[] paramTypes = new Class<?>[components.length];
        Object[] paramValues = new Object[components.length];

        for (int i = 0; i < components.length; i++) {
            paramTypes[i] = components[i].getType();
            String jsonName = components[i].getName();
            Object value = map.get(jsonName);
            paramValues[i] = convertRecordValue(value, paramTypes[i]);
        }

        try {
            Constructor<?> canonical = clazz.getDeclaredConstructor(paramTypes);
            canonical.setAccessible(true);
            return (T) canonical.newInstance(paramValues);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize Record: " + clazz.getName(), e);
        }
    }

    /**
     * 将 Map 中解析出的值转换为 Record 组件类型。
     *
     * @param value 解析值
     * @param targetType 目标类型
     * @return 转换后的值
     */
    private static Object convertRecordValue(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }
        if (targetType.isInstance(value)) {
            return value;
        }
        // 数字类型转换
        if (value instanceof Number num) {
            if (targetType == int.class || targetType == Integer.class) return num.intValue();
            if (targetType == long.class || targetType == Long.class) return num.longValue();
            if (targetType == double.class || targetType == Double.class) return num.doubleValue();
            if (targetType == float.class || targetType == Float.class) return num.floatValue();
            if (targetType == short.class || targetType == Short.class) return num.shortValue();
            if (targetType == byte.class || targetType == Byte.class) return num.byteValue();
        }
        // String → 其他类型
        if (value instanceof String str) {
            if (targetType == int.class || targetType == Integer.class) return Integer.parseInt(str);
            if (targetType == long.class || targetType == Long.class) return Long.parseLong(str);
            if (targetType == double.class || targetType == Double.class) return Double.parseDouble(str);
            if (targetType == float.class || targetType == Float.class) return Float.parseFloat(str);
            if (targetType == boolean.class || targetType == Boolean.class) return Boolean.parseBoolean(str);
        }
        return value;
    }

    /**
     * 判断一个类是否为「简单 Bean」。
     *
     * <p>简单 Bean 的所有非 static、非 transient 字段均为基本类型或其包装类、String。
     * 简单 Bean 可使用高性能的 {@link BeanReader} 路径，避免递归嵌套解析。
     *
     * @param clazz 待判断的类
     * @return 是简单 Bean 返回 true
     */
    static boolean isSimpleBean(Class<?> clazz) {
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            int mods = field.getModifiers();
            if (Modifier.isStatic(mods) || Modifier.isTransient(mods)) {
                continue;
            }
            Class<?> type = field.getType();
            if (!isSimpleType(type)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断一个类型是否为基本类型或其包装类、String。
     *
     * <p>委托给 {@link com.njydsz.common.json.util.JsonTypeUtils} 统一实现。</p>
     *
     * @param type 待判断的类型
     * @return 是基本类型返回 true
     */
    static boolean isSimpleType(Class<?> type) {
        return JsonTypeUtils.isSimpleType(type);
    }
}
