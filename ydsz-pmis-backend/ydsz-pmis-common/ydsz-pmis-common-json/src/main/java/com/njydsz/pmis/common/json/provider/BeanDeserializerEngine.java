package com.njydsz.pmis.common.json.provider;

import com.njydsz.pmis.common.json.annotation.YdszJsonBuilder;
import com.njydsz.pmis.common.json.asm.AsmDeserializer;
import com.njydsz.pmis.common.json.bytecode.ZeroCopyDeserializer;
import com.njydsz.pmis.common.json.cache.AsmCodecCache;
import com.njydsz.pmis.common.json.parser.YdszJsonParser;
import com.njydsz.pmis.common.json.reader.BeanReader;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.njydsz.pmis.common.json.reader.JSONReader;
import com.njydsz.pmis.common.json.reader.JSONReader.getPooledReader;
import com.njydsz.pmis.common.json.reader.JSONReader.returnPooledReader;

/**
 * Bean 反序列化策略引擎
 *
 * <p>负责 Bean 对象的反序列化策略选择与执行，包括零拷贝、ASM、
 * BeanReader 等多种优化路径。</p>
 *
 * @author Marvin Lee
 * @version 3.5.0
 */
final class BeanDeserializerEngine {

    private BeanDeserializerEngine() {
        throw new UnsupportedOperationException();
    }

    static Object deserializeBeanZeroCopyAsObject(String json, Class<?> clazz) {
        return deserializeBeanZeroCopy(json, clazz);
    }

    static <T> T deserializeBeanZeroCopy(String json, Class<T> clazz) {
        // ASM 优化路径：使用字节码生成的反序列化器
        if (json.trim().startsWith("{") &&
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
        if (json.trim().startsWith("{") &&
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
            json = json.trim();
            if (json.startsWith("[")) {
                return clazz.cast(YdszJsonParser.parseArray(json));
            } else {
                return clazz.cast(YdszJsonParser.parseObject(json));
            }
        }
    }

    static <E> List<E> deserializeBeanListFast(String json, Class<E> elementClass) {
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

    static <E> List<E> deserializeBeanListWithAsm(String json, Class<E> elementClass,
            AsmDeserializer<E> asmDeserializer) {
        JSONReader reader =
            getPooledReader(json);

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
            returnPooledReader(reader);
        }
    }

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
                            pos++;
                            while (pos < len && chars[pos] != '"') {
                                if (chars[pos] == '\\') pos++;
                                pos++;
                            }
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
                    pos++;
                    while (pos < len && chars[pos] != '"') {
                        if (chars[pos] == '\\') pos++;
                        pos++;
                    }
                } else if (ch == ',' && depth == 0) {
                    break;
                }
                pos++;
            }
            result.add(null);
        }

        return result;
    }

    static List<Object> deserializeArrayZeroCopy(String json, Class<?> elementClass) {
        try {
            char[] chars = json.toCharArray();
            return ZeroCopyDeserializer.parseArrayChars(chars, 0, chars.length, elementClass);
        } catch (Exception e) {
            return YdszJsonParser.parseArray(json);
        }
    }

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

    static boolean isSimpleType(Class<?> type) {
        return type.isPrimitive() ||
               type == String.class ||
               type == Integer.class || type == int.class ||
               type == Long.class || type == long.class ||
               type == Double.class || type == double.class ||
               type == Float.class || type == float.class ||
               type == Boolean.class || type == boolean.class ||
               type == Short.class || type == short.class ||
               type == Byte.class || type == byte.class ||
               type == Character.class || type == char.class;
    }
}
