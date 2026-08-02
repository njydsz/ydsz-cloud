package com.njydsz.common.json.asm;

import static org.objectweb.asm.Opcodes.*;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.reflect.*;
import java.lang.reflect.ParameterizedType;
import java.security.CodeSource;
import java.security.SecureClassLoader;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import com.njydsz.common.json.annotation.JsonAlias;
import com.njydsz.common.json.annotation.JsonFormat;
import com.njydsz.common.json.util.JsonTypeUtils;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.objectweb.asm.*;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.cache.AsmCodecCache;
import com.njydsz.common.json.reader.BeanReader;
import com.njydsz.common.json.reader.JSONReader;
import com.njydsz.common.json.writer.JSONWriter;

/**
 * ASM Bean 序列化器/反序列化器生成器
 * 
 * <p>为每个 Bean 类生成专用的序列化器和反序列化器，消除 MethodHandle 反射开销</p>
 * 
 * <p><b>类型代码体系：</b></p>
 * <ul>
 *   <li>1: String</li>
 *   <li>2: int/Integer</li>
 *   <li>3: long/Long</li>
 *   <li>4: double/Double</li>
 *   <li>5: float/Float</li>
 *   <li>6: boolean/Boolean</li>
 *   <li>7: short/Short</li>
 *   <li>8: byte/Byte</li>
 *   <li>9: char/Character</li>
 *   <li>10: LocalDateTime</li>
 *   <li>11: LocalDate</li>
 *   <li>12: Date</li>
 *   <li>13: Collection</li>
 *   <li>14: Map</li>
 *   <li>15: 嵌套 Bean</li>
 * </ul>
 * 
 * @author ydsz-team
 * @since 1.0.0
 */
public final class AsmBeanCodecGenerator {

    /** ASM ClassWriter 配置 */
    /**
     * ASM ClassWriter 标志位。
     * COMPUTE_FRAMES（不含 COMPUTE_MAXS）：让 ASM 生成 StackMapTable（Java 7+ 必需）。
     * 已知限制：ASM 9.8 在特定字节码模式下 COMPUTE_FRAMES 触发 NegativeArraySizeException，
     * 导致 ASM 生成静默降级为反射。修复方案：升级 ASM 版本或重构 emitFieldSerializationLoop 跳过帧计算。
     * 当前 visitMaxs 设为 0 让 ASM 全自算。
     */
    private static final int ASM_FLAGS = ClassWriter.COMPUTE_FRAMES;

    /**
     * 字段缓存信息（用于 ASM 生成类中缓存嵌套序列化器/反序列化器）
     */
    private static final class FieldCacheInfo {
        final String fieldName;
        final Class<?> cachedType;

        FieldCacheInfo(String fieldName, Class<?> cachedType) {
            this.fieldName = fieldName;
            this.cachedType = cachedType;
        }
    }

    /**
     * 序列化嵌套对象（静态方法，供 ASM 生成的字节码调用）
     * 
     * <p>优先使用 ASM 序列化器，避免 String 转换开销</p>
     */
    public static void serializeNested(JSONWriter writer, Object obj) {
        if (obj == null) {
            writer.write("null");
            return;
        }

        try {
            Class<?> objClass = obj.getClass();

            if (obj instanceof Collection) {
                writer.writeCollection((Collection<?>) obj);
                return;
            } else if (obj instanceof Map) {
                writer.writeMap((Map<?, ?>) obj);
                return;
            } else if (obj instanceof LocalDateTime) {
                writer.writeString(((LocalDateTime) obj).toString());
                return;
            } else if (obj instanceof LocalDate) {
                writer.writeString(((LocalDate) obj).toString());
                return;
            } else if (obj instanceof Date) {
                writer.writeString(((Date) obj).toInstant().toString());
                return;
            }

            AsmSerializer<?> asmSerializer =
                AsmCodecCache.getOrCreateSerializer(objClass);

            if (asmSerializer != null) {
                AsmCodecCache.trySerialize(obj, writer);
            } else {
                String json = YdszJson.toJson(obj);
                writer.write(json);
            }
        } catch (Exception e) {
            log.warn("序列化嵌套对象失败, 类型: {}, 错误: {}", obj.getClass().getName(), e.getMessage());
            writer.write("null");
        }
    }

    /**
     * 反序列化嵌套对象（静态方法，供 ASM 生成的字节码调用）
     * 
     * <p>优先使用 ASM 反序列化器，回退到 BeanReader</p>
     */
    public static Object deserializeNestedField(JSONReader reader, Class<?> beanClass) {
        try {
            AsmDeserializer<?> asmDeserializer =
                AsmCodecCache.getOrCreateDeserializer(beanClass);
            if (asmDeserializer != null) {
                return asmDeserializer.deserialize(reader);
            }
        } catch (Exception e) {
            log.warn("ASM反序列化失败, 类型: {}, 回退到BeanReader, 错误: {}", beanClass.getName(), e.getMessage());
        }

        try {
            BeanReader<?> beanReader =
                BeanReader.getOrCreate(beanClass);
            return beanReader.readObject(reader);
        } catch (Exception e) {
            reader.skipValue();
            return null;
        }
    }

    /**
     * 安全获取序列化器（供 ASM 生成类的构造函数调用，避免构造函数异常导致整个类加载失败）
     *
     * @param clazz 目标类型
     * @return 序列化器实例，获取失败返回 null
     */
    public static AsmSerializer<?> safeGetSerializer(Class<?> clazz) {
        try {
            return AsmCodecCache.getOrCreateSerializer(clazz);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 安全获取反序列化器（供 ASM 生成类的构造函数调用，避免构造函数异常导致整个类加载失败）
     *
     * @param clazz 目标类型
     * @return 反序列化器实例，获取失败返回 null
     */
    public static AsmDeserializer<?> safeGetDeserializer(Class<?> clazz) {
        try {
            return AsmCodecCache.getOrCreateDeserializer(clazz);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 序列化嵌套对象到缓冲区（合并 setPosition + serializeNested + getPosition）
     *
     * <p>消除 ASM 序列化器中 setPosition 和 getPosition 的额外方法调用开销</p>
     *
     * @param writer JSON 写入器
     * @param obj 嵌套对象
     * @param pos 当前写入位置
     * @return 写入后的新位置
     */
    public static int serializeNestedToBuf(JSONWriter writer, Object obj, int pos) {
        writer.setPosition(pos);
        serializeNested(writer, obj);
        return writer.getPosition();
    }

    /**
     * 使用预解析序列化器序列化嵌套对象到缓冲区（合并 setPosition + serialize + getPosition）
     *
     * <p>消除 ASM 序列化器中 setPosition 和 getPosition 的额外方法调用开销</p>
     *
     * @param serializer 预解析的序列化器
     * @param obj 嵌套对象
     * @param writer JSON 写入器
     * @param pos 当前写入位置
     * @return 写入后的新位置
     */
    public static int serializeWithSerializerToBuf(
            AsmSerializer<Object> serializer,
            Object obj, JSONWriter writer, int pos) {
        writer.setPosition(pos);
        serializer.serialize(obj, writer);
        return writer.getPosition();
    }

    /**
     * 使用预解析反序列化器反序列化列表字段（供 ASM 生成的字节码调用）
     *
     * <p>当元素反序列化器已在构造函数中预解析时，直接传入避免运行时 ConcurrentHashMap 查找</p>
     *
     * @param reader JSON 读取器
     * @param elementClass 元素类型
     * @param preResolvedDeserializer 预解析的元素反序列化器（可为 null）
     * @return 反序列化后的列表
     */
    
    public static List<?> deserializeListFieldWithDeserializer(JSONReader reader, Class<?> elementClass,
            AsmDeserializer<?> preResolvedDeserializer) {
        reader.skipWhitespace();
        if (reader.isEnd() || reader.readChar() != '[') {
            return new ArrayList<>();
        }

        List<Object> result = new ArrayList<>();

        AsmDeserializer<?> asmDeserializer = null;
        if (preResolvedDeserializer != null) {
            asmDeserializer = preResolvedDeserializer;
        } else if (!isSimpleType(elementClass)) {
            try {
                asmDeserializer = AsmCodecCache.getOrCreateDeserializerForType(elementClass);
            } catch (Exception ignored) {}
        }

        while (true) {
            reader.skipWhitespace();
            if (reader.isEnd()) break;

            char c = reader.peekChar();
            if (c == ']') { reader.readChar(); break; }
            if (c == ',') { reader.readChar(); continue; }

            if (reader.isNull()) {
                reader.readNull();
                result.add(null);
                continue;
            }

            try {
                if (elementClass == String.class) {
                    result.add(reader.readString());
                } else if (elementClass == Integer.class || elementClass == int.class) {
                    result.add(reader.readInt());
                } else if (elementClass == Long.class || elementClass == long.class) {
                    result.add(reader.readLong());
                } else if (elementClass == Double.class || elementClass == double.class) {
                    result.add(reader.readDouble());
                } else if (elementClass == Float.class || elementClass == float.class) {
                    result.add(reader.readFloat());
                } else if (elementClass == Boolean.class || elementClass == boolean.class) {
                    result.add(reader.readBoolean());
                } else if (asmDeserializer != null) {
                    result.add(asmDeserializer.deserialize(reader));
                } else {
                    reader.skipValue();
                    result.add(null);
                }
            } catch (Exception e) {
                reader.skipValue();
                result.add(null);
            }
        }

        return result;
    }

    /**
     * 反序列化列表字段（静态方法，供 ASM 生成的字节码调用）
     * 
     * <p>优先使用 ASM 反序列化器处理元素类型</p>
     */
    public static List<?> deserializeListField(JSONReader reader, Class<?> elementClass) {
        return deserializeListFieldWithDeserializer(reader, elementClass, null);
    }

    /**
     * 生成序列化器（缓存嵌套类型序列化器为实例字段，消除运行时 ConcurrentHashMap 查找）
     */
    public static <T> Class<? extends AsmSerializer<T>> generateSerializer(Class<T> beanType) throws Exception {
        String className = beanType.getName() + "_ASM_Serializer";
        String classInternalName = className.replace('.', '/');
        String beanInternalName = beanType.getName().replace('.', '/');

        ClassWriter cw = new ClassWriter(ASM_FLAGS);
        cw.visit(V1_8, ACC_PUBLIC | ACC_FINAL, classInternalName, null, 
                 "java/lang/Object", new String[]{"com/njydsz/common/json/asm/AsmSerializer"});

        Field[] fields = getSerializableFields(beanType);

        List<FieldCacheInfo> cachedFields = new ArrayList<>();
        for (Field field : fields) {
            int typeCode = getTypeCode(field.getType());
            if (typeCode == 13) {
                Class<?> elementType = getListElementType(field);
                String cachedFieldName = "_list_" + field.getName();
                cw.visitField(ACC_PRIVATE, cachedFieldName, 
                    "Lcom/njydsz/common/json/asm/AsmSerializer;", null, null).visitEnd();
                cachedFields.add(new FieldCacheInfo(cachedFieldName, elementType));
            } else if (typeCode == 15) {
                Class<?> nestedType = field.getType();
                String cachedFieldName = "_nested_" + field.getName();
                cw.visitField(ACC_PRIVATE, cachedFieldName, 
                    "Lcom/njydsz/common/json/asm/AsmSerializer;", null, null).visitEnd();
                cachedFields.add(new FieldCacheInfo(cachedFieldName, nestedType));
            }
        }

        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);

        for (FieldCacheInfo info : cachedFields) {
            mv.visitVarInsn(ALOAD, 0);
            mv.visitLdcInsn(Type.getType(getTypeDescriptorForClass(info.cachedType)));
            mv.visitMethodInsn(INVOKESTATIC, "com/njydsz/common/json/asm/AsmBeanCodecGenerator", 
                "safeGetSerializer", "(Ljava/lang/Class;)Lcom/njydsz/common/json/asm/AsmSerializer;", false);
            mv.visitFieldInsn(PUTFIELD, classInternalName, info.fieldName, 
                "Lcom/njydsz/common/json/asm/AsmSerializer;");
        }

        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        mv = cw.visitMethod(ACC_PUBLIC, "serialize", 
                           "(Ljava/lang/Object;Lcom/njydsz/common/json/writer/JSONWriter;)V", null, null);
        mv.visitCode();

        int estimatedSize = 2;
        for (Field field : fields) {
            int typeCode = getTypeCode(field.getType());
            estimatedSize += field.getName().length() + 4;
            switch (typeCode) {
                case 1: estimatedSize += 32; break;
                case 2: case 7: case 8: case 9: estimatedSize += 12; break;
                case 3: estimatedSize += 22; break;
                case 4: estimatedSize += 24; break;
                case 5: estimatedSize += 16; break;
                case 6: estimatedSize += 5; break;
                case 10: case 11: case 12: estimatedSize += 32; break;
                case 13: estimatedSize += 256; break;
                case 14: estimatedSize += 128; break;
                default: estimatedSize += 128; break;
            }
        }

        // 直接缓冲区访问模式：消除 write() 方法调用的 externalSb 检查和 ensureCapacity 检查开销
        // 本地变量槽位：0=this, 1=obj, 2=writer, 3=buf, 4=pos, 5=temp

        // writer.preAllocate(estimatedSize) — 一次性容量预分配，后续所有 ensureCapacity 检查均为 no-op
        mv.visitVarInsn(ALOAD, 2);
        mv.visitLdcInsn(estimatedSize);
        mv.visitMethodInsn(INVOKEVIRTUAL, "com/njydsz/common/json/writer/JSONWriter", "preAllocate", "(I)V", false);

        // char[] buf = writer.buf — 直接字段访问，消除 getBuffer() 方法调用开销
        mv.visitVarInsn(ALOAD, 2);
        mv.visitFieldInsn(GETFIELD, "com/njydsz/common/json/writer/JSONWriter", "buf", "[C");
        mv.visitVarInsn(ASTORE, 3);

        // int pos = writer.pos — 直接字段访问，消除 getPosition() 方法调用开销
        mv.visitVarInsn(ALOAD, 2);
        mv.visitFieldInsn(GETFIELD, "com/njydsz/common/json/writer/JSONWriter", "pos", "I");
        mv.visitVarInsn(ISTORE, 4);

        // buf[pos++] = '{' — 直接写入结构字符，消除 writer.write("{") 方法调用
        emitWriteCharDirect(mv, '{');

        emitFieldSerializationLoop(mv, fields, classInternalName, beanInternalName, beanType);

        // buf[pos++] = '}' — 直接写入结构字符
        emitWriteCharDirect(mv, '}');

        // writer.pos = pos — 直接字段写入，消除 setPosition() 方法调用开销
        mv.visitVarInsn(ALOAD, 2);
        mv.visitVarInsn(ILOAD, 4);
        mv.visitFieldInsn(PUTFIELD, "com/njydsz/common/json/writer/JSONWriter", "pos", "I");

        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        // 生成 serializeInline 方法：跳过 preAllocate，直接在已有 buf/pos 上操作
        // 用于列表序列化场景，外层已预分配足够容量
        // 实现方式：直接读取 buf/pos，写入字段，写回 pos，跳过 preAllocate
        MethodVisitor mvInline = cw.visitMethod(ACC_PUBLIC, "serializeInline",
                           "(Ljava/lang/Object;Lcom/njydsz/common/json/writer/JSONWriter;)V", null, null);
        mvInline.visitCode();

        // char[] buf = writer.buf
        mvInline.visitVarInsn(ALOAD, 2);
        mvInline.visitFieldInsn(GETFIELD, "com/njydsz/common/json/writer/JSONWriter", "buf", "[C");
        mvInline.visitVarInsn(ASTORE, 3);

        // int pos = writer.pos
        mvInline.visitVarInsn(ALOAD, 2);
        mvInline.visitFieldInsn(GETFIELD, "com/njydsz/common/json/writer/JSONWriter", "pos", "I");
        mvInline.visitVarInsn(ISTORE, 4);

        // buf[pos++] = '{'
        emitWriteCharDirect(mvInline, '{');

        emitFieldSerializationLoop(mvInline, fields, classInternalName, beanInternalName, beanType);

        // buf[pos++] = '}'
        emitWriteCharDirect(mvInline, '}');

        // writer.pos = pos
        mvInline.visitVarInsn(ALOAD, 2);
        mvInline.visitVarInsn(ILOAD, 4);
        mvInline.visitFieldInsn(PUTFIELD, "com/njydsz/common/json/writer/JSONWriter", "pos", "I");

        mvInline.visitInsn(RETURN);
        mvInline.visitMaxs(8, 8);
        mvInline.visitEnd();

        cw.visitEnd();

        byte[] bytecode = cw.toByteArray();
        return defineClass(className, bytecode, beanType);
    }

    /**
     * 直接写入单个字符到缓冲区：buf[pos++] = c
     *
     * <p>消除 writer.write(String) 方法调用的 externalSb 检查和 ensureCapacity 检查开销</p>
     */
    /**
     * Emit field serialization bytecode for all fields (shared by serialize() and serializeInline()).
     *
     * <p>Extracts the common field iteration loop, eliminating ~400 lines of duplication
     * between serialize() and serializeInline() methods. Both methods generate identical
     * field serialization bytecode, differing only in setup (preAllocate vs direct buf/pos access).</p>
     *
     * <p>Local variable slot layout: 0=this, 1=obj, 2=writer, 3=buf, 4=pos, 5+=temp</p>
     *
     * @param mv MethodVisitor to emit bytecode to
     * @param fields serializable fields array
     * @param classInternalName ASM internal name of the generated serializer class
     * @param beanInternalName ASM internal name of the bean type
     * @param beanType the bean class
     */
    private static void emitFieldSerializationLoop(MethodVisitor mv, Field[] fields,
            String classInternalName, String beanInternalName, Class<?> beanType) {
            int fieldCount = 0;
            for (int i = 0; i < fields.length; i++) {
                Field field = fields[i];
                String getterName = beanType.isRecord() ? getRecordAccessorName(field) : getGetterName(field);
                Method getter = findMethod(beanType, getterName);
                if (getter == null) continue;
            
                int typeCode = getTypeCode(field.getType());
            
                String jsonKey = (fieldCount == 0) ? 
                    ("\"" + field.getName() + "\":") : 
                    (",\"" + field.getName() + "\":");
                fieldCount++;
                int keyLen = jsonKey.length();

                Label endField = new Label();

                // 直接写入字段名：jsonKey.getChars(0, keyLen, buf, pos); pos += keyLen;
                // 消除 writer.write(jsonKey) 方法调用开销
                emitWriteStringGetChars(mv, jsonKey, keyLen);

                // 获取字段值
                mv.visitVarInsn(ALOAD, 1);
                mv.visitTypeInsn(CHECKCAST, beanInternalName);
                mv.visitMethodInsn(INVOKEVIRTUAL, beanInternalName, getterName, 
                                 "()" + getTypeDescriptor(field.getType()), false);

                // 根据类型码直接写入缓冲区
                if (typeCode == 1) {
                    // String 字段：null 检查 → 无转义时内联快速写入，有转义时委托 writeStringToBuf
                    Label notNullLabel = new Label();
                    mv.visitInsn(DUP);
                    mv.visitJumpInsn(IFNONNULL, notNullLabel);
                    mv.visitInsn(POP);
                    emitWriteNullDirect(mv);
                    mv.visitJumpInsn(GOTO, endField);
                    mv.visitLabel(notNullLabel);
                    mv.visitVarInsn(ASTORE, 5);

                    // 检查是否需要转义（1 次方法调用 vs 原来 4 次方法调用的 sync/re-read）
                    mv.visitVarInsn(ALOAD, 5);
                    mv.visitMethodInsn(INVOKESTATIC, "com/njydsz/common/json/writer/JSONWriter",
                        "needsEscape", "(Ljava/lang/String;)Z", false);

                    Label slowPath = new Label();
                    mv.visitJumpInsn(IFNE, slowPath);

                    // 快速路径（无需转义）：直接内联写入缓冲区，零 sync/re-read 开销
                    // len = str.length()
                    mv.visitVarInsn(ALOAD, 5);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()I", false);
                    mv.visitVarInsn(ISTORE, 6);

                    // buf[pos++] = '"'
                    emitWriteCharDirect(mv, '"');

                    // str.getChars(0, len, buf, pos)
                    mv.visitVarInsn(ALOAD, 5);
                    mv.visitInsn(ICONST_0);
                    mv.visitVarInsn(ILOAD, 6);
                    mv.visitVarInsn(ALOAD, 3);
                    mv.visitVarInsn(ILOAD, 4);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "getChars", "(II[CI)V", false);

                    // pos += len
                    mv.visitVarInsn(ILOAD, 4);
                    mv.visitVarInsn(ILOAD, 6);
                    mv.visitInsn(IADD);
                    mv.visitVarInsn(ISTORE, 4);

                    // buf[pos++] = '"'
                    emitWriteCharDirect(mv, '"');

                    mv.visitJumpInsn(GOTO, endField);

                    // 慢速路径（需要转义）：委托 writeStringToBuf（2 次方法调用 vs 原来 4 次）
                    mv.visitLabel(slowPath);
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitVarInsn(ALOAD, 5);
                    mv.visitVarInsn(ILOAD, 4);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "com/njydsz/common/json/writer/JSONWriter",
                        "writeStringToBuf", "(Ljava/lang/String;I)I", false);
                    mv.visitVarInsn(ISTORE, 4);
                    emitReadBufFromWriter(mv);
                
                } else if (typeCode == 2) {
                    // int/Integer 字段：null 检查 → 非空则 NumberUtils.writeInt 直接写入缓冲区
                    if (field.getType() == Integer.class) {
                        Label notNullLabel = new Label();
                        mv.visitInsn(DUP);
                        mv.visitJumpInsn(IFNONNULL, notNullLabel);
                        mv.visitInsn(POP);
                        emitWriteNullDirect(mv);
                        mv.visitJumpInsn(GOTO, endField);
                        mv.visitLabel(notNullLabel);
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
                    }
                    // pos += NumberUtils.writeInt(value, buf, pos)
                    emitWriteIntDirect(mv);
                
                } else if (typeCode == 3) {
                    // long/Long 字段：null 检查 → 非空则 NumberUtils.writeLong 直接写入缓冲区
                    if (field.getType() == Long.class) {
                        Label notNullLabel = new Label();
                        mv.visitInsn(DUP);
                        mv.visitJumpInsn(IFNONNULL, notNullLabel);
                        mv.visitInsn(POP);
                        emitWriteNullDirect(mv);
                        mv.visitJumpInsn(GOTO, endField);
                        mv.visitLabel(notNullLabel);
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false);
                    }
                    // pos += NumberUtils.writeLong(value, buf, pos)
                    emitWriteLongDirect(mv);
                
                } else if (typeCode == 4) {
                    // double/Double 字段：null 检查 → 委托 writeDoubleToBuf（2 次调用 vs 原来 4 次）
                    if (field.getType() == Double.class) {
                        Label notNullLabel = new Label();
                        mv.visitInsn(DUP);
                        mv.visitJumpInsn(IFNONNULL, notNullLabel);
                        mv.visitInsn(POP);
                        emitWriteNullDirect(mv);
                        mv.visitJumpInsn(GOTO, endField);
                        mv.visitLabel(notNullLabel);
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false);
                    }
                    mv.visitVarInsn(DSTORE, 6);
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitVarInsn(DLOAD, 6);
                    mv.visitVarInsn(ILOAD, 4);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "com/njydsz/common/json/writer/JSONWriter",
                        "writeDoubleToBuf", "(DI)I", false);
                    mv.visitVarInsn(ISTORE, 4);
                    emitReadBufFromWriter(mv);
                
                } else if (typeCode == 5) {
                    // float/Float 字段：null 检查 → 委托 writeFloatToBuf（2 次调用 vs 原来 4 次）
                    if (field.getType() == Float.class) {
                        Label notNullLabel = new Label();
                        mv.visitInsn(DUP);
                        mv.visitJumpInsn(IFNONNULL, notNullLabel);
                        mv.visitInsn(POP);
                        emitWriteNullDirect(mv);
                        mv.visitJumpInsn(GOTO, endField);
                        mv.visitLabel(notNullLabel);
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F", false);
                    }
                    mv.visitVarInsn(FSTORE, 6);
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitVarInsn(FLOAD, 6);
                    mv.visitVarInsn(ILOAD, 4);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "com/njydsz/common/json/writer/JSONWriter",
                        "writeFloatToBuf", "(FI)I", false);
                    mv.visitVarInsn(ISTORE, 4);
                    emitReadBufFromWriter(mv);
                
                } else if (typeCode == 6) {
                    // boolean/Boolean 字段：null 检查 → 非空则直接写入 "true"/"false" 到缓冲区
                    if (field.getType() == Boolean.class) {
                        Label notNullLabel = new Label();
                        mv.visitInsn(DUP);
                        mv.visitJumpInsn(IFNONNULL, notNullLabel);
                        mv.visitInsn(POP);
                        emitWriteNullDirect(mv);
                        mv.visitJumpInsn(GOTO, endField);
                        mv.visitLabel(notNullLabel);
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
                    }
                    Label trueLabel = new Label();
                    Label boolEndLabel = new Label();
                    mv.visitJumpInsn(IFNE, trueLabel);
                    // false: 直接写入 "false" 到缓冲区
                    emitWriteStringGetChars(mv, "false", 5);
                    mv.visitJumpInsn(GOTO, boolEndLabel);
                    mv.visitLabel(trueLabel);
                    // true: 直接写入 "true" 到缓冲区
                    emitWriteStringGetChars(mv, "true", 4);
                    mv.visitLabel(boolEndLabel);
                
                } else if (typeCode >= 7 && typeCode <= 9) {
                    // short/byte/char 字段：null 检查 → NumberUtils.writeInt 直接写入
                    if (field.getType() == Short.class) {
                        Label notNullLabel = new Label();
                        mv.visitInsn(DUP);
                        mv.visitJumpInsn(IFNONNULL, notNullLabel);
                        mv.visitInsn(POP);
                        emitWriteNullDirect(mv);
                        mv.visitJumpInsn(GOTO, endField);
                        mv.visitLabel(notNullLabel);
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Short", "shortValue", "()S", false);
                    } else if (field.getType() == Byte.class) {
                        Label notNullLabel = new Label();
                        mv.visitInsn(DUP);
                        mv.visitJumpInsn(IFNONNULL, notNullLabel);
                        mv.visitInsn(POP);
                        emitWriteNullDirect(mv);
                        mv.visitJumpInsn(GOTO, endField);
                        mv.visitLabel(notNullLabel);
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Byte", "byteValue", "()B", false);
                    } else if (field.getType() == Character.class) {
                        Label notNullLabel = new Label();
                        mv.visitInsn(DUP);
                        mv.visitJumpInsn(IFNONNULL, notNullLabel);
                        mv.visitInsn(POP);
                        emitWriteNullDirect(mv);
                        mv.visitJumpInsn(GOTO, endField);
                        mv.visitLabel(notNullLabel);
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Character", "charValue", "()C", false);
                    }
                    emitWriteIntDirect(mv);

                } else if (typeCode == 10 || typeCode == 11) {
                    // LocalDateTime/LocalDate 字段：null 检查 → 委托 writeStringToBuf（2 次调用 vs 原来 4 次）
                    mv.visitVarInsn(ASTORE, 5);
                    Label notNullLabel = new Label();
                    Label endNull = new Label();
                    mv.visitVarInsn(ALOAD, 5);
                    mv.visitJumpInsn(IFNONNULL, notNullLabel);
                    emitWriteNullDirect(mv);
                    mv.visitJumpInsn(GOTO, endNull);
                    mv.visitLabel(notNullLabel);
                    String dateInternalName = (typeCode == 10) ? "java/time/LocalDateTime" : "java/time/LocalDate";
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitVarInsn(ALOAD, 5);
                    // @JsonFormat 支持：检查是否有格式化模式
                    JsonFormat formatAnnotation = field.getAnnotation(JsonFormat.class);
                    String datePattern = (formatAnnotation != null && !formatAnnotation.pattern().isEmpty()) ? formatAnnotation.pattern() : null;
                    if (datePattern != null) {
                        mv.visitLdcInsn(datePattern);
                        mv.visitMethodInsn(INVOKESTATIC, "com/njydsz/common/json/asm/AsmBeanCodecGenerator",
                            "formatDate", "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/String;", false);
                    } else {
                        mv.visitMethodInsn(INVOKEVIRTUAL, dateInternalName, "toString", "()Ljava/lang/String;", false);
                    }
                    mv.visitVarInsn(ILOAD, 4);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "com/njydsz/common/json/writer/JSONWriter",
                        "writeStringToBuf", "(Ljava/lang/String;I)I", false);
                    mv.visitVarInsn(ISTORE, 4);
                    emitReadBufFromWriter(mv);
                    mv.visitLabel(endNull);

                } else if (typeCode == 12) {
                    // Date 字段：null 检查 → 委托 writeStringToBuf（2 次调用 vs 原来 4 次）
                    mv.visitVarInsn(ASTORE, 5);
                    Label notNullLabel = new Label();
                    Label endNull = new Label();
                    mv.visitVarInsn(ALOAD, 5);
                    mv.visitJumpInsn(IFNONNULL, notNullLabel);
                    emitWriteNullDirect(mv);
                    mv.visitJumpInsn(GOTO, endNull);
                    mv.visitLabel(notNullLabel);
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitVarInsn(ALOAD, 5);
                    // @JsonFormat 支持：检查是否有格式化模式
                    JsonFormat dateFmtAnn = field.getAnnotation(JsonFormat.class);
                    String datePattern2 = (dateFmtAnn != null && !dateFmtAnn.pattern().isEmpty()) ? dateFmtAnn.pattern() : null;
                    if (datePattern2 != null) {
                        mv.visitLdcInsn(datePattern2);
                        mv.visitMethodInsn(INVOKESTATIC, "com/njydsz/common/json/asm/AsmBeanCodecGenerator",
                            "formatDate", "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/String;", false);
                    } else {
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/Date", "toInstant", "()Ljava/time/Instant;", false);
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/time/Instant", "toString", "()Ljava/lang/String;", false);
                    }
                    mv.visitVarInsn(ILOAD, 4);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "com/njydsz/common/json/writer/JSONWriter",
                        "writeStringToBuf", "(Ljava/lang/String;I)I", false);
                    mv.visitVarInsn(ISTORE, 4);
                    emitReadBufFromWriter(mv);
                    mv.visitLabel(endNull);

                } else if (typeCode == 13) {
                    // Collection 字段：null 检查 → 委托 writeCollectionToBuf/writeCollectionWithSerializerToBuf
                    mv.visitVarInsn(ASTORE, 5);
                    Label notNullLabel = new Label();
                    Label endNull = new Label();
                    mv.visitVarInsn(ALOAD, 5);
                    mv.visitJumpInsn(IFNONNULL, notNullLabel);
                    emitWriteNullDirect(mv);
                    mv.visitJumpInsn(GOTO, endNull);
                    mv.visitLabel(notNullLabel);

                    String cachedFieldName = "_list_" + field.getName();
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(GETFIELD, classInternalName, cachedFieldName,
                        "Lcom/njydsz/common/json/asm/AsmSerializer;");
                    Label hasSerializerLabel = new Label();
                    mv.visitJumpInsn(IFNONNULL, hasSerializerLabel);

                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitVarInsn(ALOAD, 5);
                    mv.visitVarInsn(ILOAD, 4);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "com/njydsz/common/json/writer/JSONWriter",
                        "writeCollectionToBuf", "(Ljava/util/Collection;I)I", false);
                    mv.visitVarInsn(ISTORE, 4);
                    emitReadBufFromWriter(mv);
                    mv.visitJumpInsn(GOTO, endNull);

                    mv.visitLabel(hasSerializerLabel);
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitVarInsn(ALOAD, 5);
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(GETFIELD, classInternalName, cachedFieldName,
                        "Lcom/njydsz/common/json/asm/AsmSerializer;");
                    mv.visitVarInsn(ILOAD, 4);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "com/njydsz/common/json/writer/JSONWriter",
                        "writeCollectionWithSerializerToBuf", "(Ljava/util/Collection;Lcom/njydsz/common/json/asm/AsmSerializer;I)I", false);
                    mv.visitVarInsn(ISTORE, 4);
                    emitReadBufFromWriter(mv);

                    mv.visitLabel(endNull);

                } else if (typeCode == 14) {
                    // Map 字段：null 检查 → 委托 writeMapToBuf（2 次调用 vs 原来 4 次）
                    mv.visitVarInsn(ASTORE, 5);
                    Label notNullLabel = new Label();
                    Label endNull = new Label();
                    mv.visitVarInsn(ALOAD, 5);
                    mv.visitJumpInsn(IFNONNULL, notNullLabel);
                    emitWriteNullDirect(mv);
                    mv.visitJumpInsn(GOTO, endNull);
                    mv.visitLabel(notNullLabel);
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitVarInsn(ALOAD, 5);
                    mv.visitVarInsn(ILOAD, 4);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "com/njydsz/common/json/writer/JSONWriter",
                        "writeMapToBuf", "(Ljava/util/Map;I)I", false);
                    mv.visitVarInsn(ISTORE, 4);
                    emitReadBufFromWriter(mv);
                    mv.visitLabel(endNull);

                } else {
                    // 嵌套 Bean 字段：null 检查 → 委托 serializeNestedToBuf/serializeWithSerializerToBuf
                    mv.visitVarInsn(ASTORE, 5);

                    Label notNullLabel = new Label();
                    Label endNull = new Label();

                    mv.visitVarInsn(ALOAD, 5);
                    mv.visitJumpInsn(IFNONNULL, notNullLabel);

                    emitWriteNullDirect(mv);
                    mv.visitJumpInsn(GOTO, endNull);

                    mv.visitLabel(notNullLabel);

                    String cachedFieldName = "_nested_" + field.getName();
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(GETFIELD, classInternalName, cachedFieldName,
                        "Lcom/njydsz/common/json/asm/AsmSerializer;");
                    Label hasSerializerLabel = new Label();
                    mv.visitJumpInsn(IFNONNULL, hasSerializerLabel);

                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitVarInsn(ALOAD, 5);
                    mv.visitVarInsn(ILOAD, 4);
                    mv.visitMethodInsn(INVOKESTATIC, "com/njydsz/common/json/asm/AsmBeanCodecGenerator",
                        "serializeNestedToBuf", "(Lcom/njydsz/common/json/writer/JSONWriter;Ljava/lang/Object;I)I", false);
                    mv.visitVarInsn(ISTORE, 4);
                    emitReadBufFromWriter(mv);
                    mv.visitJumpInsn(GOTO, endNull);

                    mv.visitLabel(hasSerializerLabel);
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(GETFIELD, classInternalName, cachedFieldName,
                        "Lcom/njydsz/common/json/asm/AsmSerializer;");
                    mv.visitVarInsn(ALOAD, 5);
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitVarInsn(ILOAD, 4);
                    mv.visitMethodInsn(INVOKESTATIC, "com/njydsz/common/json/asm/AsmBeanCodecGenerator",
                        "serializeWithSerializerToBuf", "(Lcom/njydsz/common/json/asm/AsmSerializer;Ljava/lang/Object;Lcom/njydsz/common/json/writer/JSONWriter;I)I", false);
                    mv.visitVarInsn(ISTORE, 4);
                    emitReadBufFromWriter(mv);

                    mv.visitLabel(endNull);
                }
            
                mv.visitLabel(endField);
            }
    }


    private static void emitWriteCharDirect(MethodVisitor mv, char c) {
        mv.visitVarInsn(ALOAD, 3);
        mv.visitVarInsn(ILOAD, 4);
        if (c <= 127 && c >= -128) {
            mv.visitIntInsn(BIPUSH, (int) c);
        } else {
            mv.visitIntInsn(SIPUSH, (int) c);
        }
        mv.visitInsn(CASTORE);
        mv.visitIincInsn(4, 1);
    }

    /**
     * 直接写入字符串到缓冲区：str.getChars(0, len, buf, pos); pos += len;
     *
     * <p>用于字段名和常量字符串（如 "true"/"false"）的直接写入，
     * 消除 writer.write(String) 方法调用开销</p>
     */
    private static void emitWriteStringGetChars(MethodVisitor mv, String str, int len) {
        mv.visitLdcInsn(str);
        mv.visitInsn(ICONST_0);
        emitIntConstant(mv, len);
        mv.visitVarInsn(ALOAD, 3);
        mv.visitVarInsn(ILOAD, 4);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "getChars", "(II[CI)V", false);
        mv.visitIincInsn(4, len);
    }

    /**
     * 直接写入 "null" 到缓冲区：buf[pos++]='n'; buf[pos++]='u'; buf[pos++]='l'; buf[pos++]='l';
     *
     * <p>消除 writer.write("null") 方法调用开销</p>
     */
    private static void emitWriteNullDirect(MethodVisitor mv) {
        emitWriteCharDirect(mv, 'n');
        emitWriteCharDirect(mv, 'u');
        emitWriteCharDirect(mv, 'l');
        emitWriteCharDirect(mv, 'l');
    }

    /**
     * 从 writer 重新读取 buf：buf = writer.buf
     *
     * <p>当 pos 已通过方法返回值更新时，只需重新读取 buf 即可</p>
     * <p>使用直接字段访问，消除 getBuffer() 方法调用开销</p>
     */
    private static void emitReadBufFromWriter(MethodVisitor mv) {
        mv.visitVarInsn(ALOAD, 2);
        mv.visitFieldInsn(GETFIELD, "com/njydsz/common/json/writer/JSONWriter", "buf", "[C");
        mv.visitVarInsn(ASTORE, 3);
    }

    /**
     * 直接写入 int 值到缓冲区：pos += NumberUtils.writeInt(value, buf, pos)
     *
     * <p>消除 writer.writeInt() 方法调用的 externalSb 检查和 ensureCapacity 检查开销</p>
     *
     * <p>调用前栈状态：[int_value]</p>
     * <p>调用后栈状态：[]</p>
     */
    private static void emitWriteIntDirect(MethodVisitor mv) {
        // 栈: [int_value] — 先存储到临时变量
        mv.visitVarInsn(ISTORE, 6);
        // pos += NumberUtils.writeInt(value, buf, pos)
        mv.visitVarInsn(ILOAD, 6);     // int_value
        mv.visitVarInsn(ALOAD, 3);     // buf
        mv.visitVarInsn(ILOAD, 4);     // pos
        mv.visitMethodInsn(INVOKESTATIC, "com/njydsz/common/json/number/NumberUtils", "writeInt", "(I[CI)I", false);
        mv.visitInsn(IADD);
        mv.visitVarInsn(ISTORE, 4);    // pos = pos + result
    }

    /**
     * 直接写入 long 值到缓冲区：pos += NumberUtils.writeLong(value, buf, pos)
     *
     * <p>消除 writer.writeLong() 方法调用的 externalSb 检查和 ensureCapacity 检查开销</p>
     *
     * <p>调用前栈状态：[long_value]（占2个槽位）</p>
     * <p>调用后栈状态：[]</p>
     */
    private static void emitWriteLongDirect(MethodVisitor mv) {
        // 栈: [long_value] — 先存储到临时变量（long 占2个槽位：6和7）
        mv.visitVarInsn(LSTORE, 6);
        // pos += NumberUtils.writeLong(value, buf, pos)
        mv.visitVarInsn(LLOAD, 6);     // long_value
        mv.visitVarInsn(ALOAD, 3);     // buf
        mv.visitVarInsn(ILOAD, 4);     // pos
        mv.visitMethodInsn(INVOKESTATIC, "com/njydsz/common/json/number/NumberUtils", "writeLong", "(J[CI)I", false);
        mv.visitInsn(IADD);
        mv.visitVarInsn(ISTORE, 4);    // pos = pos + result
    }

    /**
     * 发射整数常量到栈上（使用最优字节码指令）
     */
    private static void emitIntConstant(MethodVisitor mv, int value) {
        if (value >= -1 && value <= 5) {
            mv.visitInsn(ICONST_0 + value);
        } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            mv.visitIntInsn(BIPUSH, value);
        } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            mv.visitIntInsn(SIPUSH, value);
        } else {
            mv.visitLdcInsn(value);
        }
    }

    /**
     * 生成反序列化器（缓存嵌套类型反序列化器为实例字段，消除运行时 ConcurrentHashMap 查找）
     * 
     * <p>支持所有类型：基本类型、日期类型、集合、Map、嵌套 Bean</p>
     */
    public static <T> Class<? extends AsmDeserializer<T>> generateDeserializer(Class<T> beanType) throws Exception {
        // Record 类不支持 ASM 反序列化（无 no-arg 构造器和 setter），回退到反射路径
        if (beanType.isRecord()) {
            throw new UnsupportedOperationException("Record classes are not supported by ASM deserializer, use reflection path instead: " + beanType.getName());
        }
        String className = beanType.getName() + "_ASM_Deserializer";
        String classInternalName = className.replace('.', '/');
        String beanInternalName = beanType.getName().replace('.', '/');

        ClassWriter cw = new ClassWriter(ASM_FLAGS);
        cw.visit(V1_8, ACC_PUBLIC | ACC_FINAL, classInternalName, null, 
                 "java/lang/Object", new String[]{"com/njydsz/common/json/asm/AsmDeserializer"});

        Field[] fields = getSerializableFields(beanType);

        List<FieldCacheInfo> cachedDeserFields = new ArrayList<>();
        for (Field field : fields) {
            int typeCode = getTypeCode(field.getType());
            if (typeCode == 13) {
                Class<?> elementType = getListElementType(field);
                String cachedFieldName = "_list_deser_" + field.getName();
                cw.visitField(ACC_PRIVATE, cachedFieldName, 
                    "Lcom/njydsz/common/json/asm/AsmDeserializer;", null, null).visitEnd();
                cachedDeserFields.add(new FieldCacheInfo(cachedFieldName, elementType));
            } else if (typeCode == 15) {
                Class<?> nestedType = field.getType();
                String cachedFieldName = "_nested_deser_" + field.getName();
                cw.visitField(ACC_PRIVATE, cachedFieldName, 
                    "Lcom/njydsz/common/json/asm/AsmDeserializer;", null, null).visitEnd();
                cachedDeserFields.add(new FieldCacheInfo(cachedFieldName, nestedType));
            }
        }

        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);

        for (FieldCacheInfo info : cachedDeserFields) {
            mv.visitVarInsn(ALOAD, 0);
            mv.visitLdcInsn(Type.getType(getTypeDescriptorForClass(info.cachedType)));
            mv.visitMethodInsn(INVOKESTATIC, "com/njydsz/common/json/asm/AsmBeanCodecGenerator", 
                "safeGetDeserializer", "(Ljava/lang/Class;)Lcom/njydsz/common/json/asm/AsmDeserializer;", false);
            mv.visitFieldInsn(PUTFIELD, classInternalName, info.fieldName, 
                "Lcom/njydsz/common/json/asm/AsmDeserializer;");
        }

        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        mv = cw.visitMethod(ACC_PUBLIC, "deserialize", 
                           "(Lcom/njydsz/common/json/reader/JSONReader;)Ljava/lang/Object;", null, null);
        mv.visitCode();

        mv.visitTypeInsn(NEW, beanInternalName);
        mv.visitInsn(DUP);
        mv.visitMethodInsn(INVOKESPECIAL, beanInternalName, "<init>", "()V", false);
        mv.visitVarInsn(ASTORE, 2);

        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKEVIRTUAL, "com/njydsz/common/json/reader/JSONReader", "skipWhitespace", "()V", false);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKEVIRTUAL, "com/njydsz/common/json/reader/JSONReader", "readChar", "()C", false);
        mv.visitInsn(POP);
        
        Label loopStart = new Label();
        Label loopEnd = new Label();
        
        mv.visitLabel(loopStart);
        
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKEVIRTUAL, "com/njydsz/common/json/reader/JSONReader", "readFieldNameHash", "()J", false);
        mv.visitVarInsn(LSTORE, 3);
        
        mv.visitVarInsn(LLOAD, 3);
        mv.visitInsn(LCONST_0);
        mv.visitInsn(LCMP);
        mv.visitJumpInsn(IFEQ, loopEnd);
        
        Label nextField = new Label();
        
        for (Field field : fields) {
            String setterName = getSetterName(field);
            Method setter = findSetter(beanType, setterName, field.getType());
            if (setter == null) continue;

            String fieldName = field.getName();
            int typeCode = getTypeCode(field.getType());
            
            Label fieldMatched = new Label();
            
            long fieldHash = JSONReader.fnv1aHash(fieldName);
            mv.visitVarInsn(LLOAD, 3);
            mv.visitLdcInsn(fieldHash);
            mv.visitInsn(LCMP);
            mv.visitJumpInsn(IFEQ, fieldMatched);
            // @JsonAlias support: check alias hashes
            JsonAlias aliasAnnotation = field.getAnnotation(JsonAlias.class);
            if (aliasAnnotation != null) {
                for (String alias : aliasAnnotation.value()) {
                    long aliasHash = JSONReader.fnv1aHash(alias);
                    mv.visitVarInsn(LLOAD, 3);
                    mv.visitLdcInsn(aliasHash);
                    mv.visitInsn(LCMP);
                    mv.visitJumpInsn(IFEQ, fieldMatched);
                }
            }
            mv.visitJumpInsn(GOTO, nextField);
            
            mv.visitLabel(fieldMatched);
            
            if (typeCode == 1) {
                mv.visitVarInsn(ALOAD, 2);
                mv.visitVarInsn(ALOAD, 1);
                mv.visitMethodInsn(INVOKEVIRTUAL, "com/njydsz/common/json/reader/JSONReader", "readStringDirect", "()Ljava/lang/String;", false);
                mv.visitMethodInsn(INVOKEVIRTUAL, beanInternalName, setterName, "(Ljava/lang/String;)V", false);
                
            } else if (typeCode == 2) {
                if (field.getType() == Integer.class) {
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "com/njydsz/common/json/reader/JSONReader", "readInt", "()I", false);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, beanInternalName, setterName, "(Ljava/lang/Integer;)V", false);
                } else {
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "com/njydsz/common/json/reader/JSONReader", "readInt", "()I", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, beanInternalName, setterName, "(I)V", false);
                }
                
            } else if (typeCode == 3) {
                if (field.getType() == Long.class) {
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "com/njydsz/common/json/reader/JSONReader", "readLong", "()J", false);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, beanInternalName, setterName, "(Ljava/lang/Long;)V", false);
                } else {
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "com/njydsz/common/json/reader/JSONReader", "readLong", "()J", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, beanInternalName, setterName, "(J)V", false);
                }
                
            } else if (typeCode == 4) {
                if (field.getType() == Double.class) {
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "com/njydsz/common/json/reader/JSONReader", "readDouble", "()D", false);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, beanInternalName, setterName, "(Ljava/lang/Double;)V", false);
                } else {
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "com/njydsz/common/json/reader/JSONReader", "readDouble", "()D", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, beanInternalName, setterName, "(D)V", false);
                }
                
            } else if (typeCode == 5) {
                if (field.getType() == Float.class) {
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "com/njydsz/common/json/reader/JSONReader", "readFloat", "()F", false);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, beanInternalName, setterName, "(Ljava/lang/Float;)V", false);
                } else {
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "com/njydsz/common/json/reader/JSONReader", "readFloat", "()F", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, beanInternalName, setterName, "(F)V", false);
                }
                
            } else if (typeCode == 6) {
                if (field.getType() == Boolean.class) {
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "com/njydsz/common/json/reader/JSONReader", "readBoolean", "()Z", false);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, beanInternalName, setterName, "(Ljava/lang/Boolean;)V", false);
                } else {
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "com/njydsz/common/json/reader/JSONReader", "readBoolean", "()Z", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, beanInternalName, setterName, "(Z)V", false);
                }

            } else if (typeCode == 10) {
                mv.visitVarInsn(ALOAD, 2);
                mv.visitVarInsn(ALOAD, 1);
                mv.visitMethodInsn(INVOKEVIRTUAL, "com/njydsz/common/json/reader/JSONReader", "readStringDirect", "()Ljava/lang/String;", false);
                mv.visitMethodInsn(INVOKESTATIC, "java/time/LocalDateTime", "parse", "(Ljava/lang/CharSequence;)Ljava/time/LocalDateTime;", false);
                mv.visitMethodInsn(INVOKEVIRTUAL, beanInternalName, setterName, "(Ljava/time/LocalDateTime;)V", false);

            } else if (typeCode == 11) {
                mv.visitVarInsn(ALOAD, 2);
                mv.visitVarInsn(ALOAD, 1);
                mv.visitMethodInsn(INVOKEVIRTUAL, "com/njydsz/common/json/reader/JSONReader", "readStringDirect", "()Ljava/lang/String;", false);
                mv.visitMethodInsn(INVOKESTATIC, "java/time/LocalDate", "parse", "(Ljava/lang/CharSequence;)Ljava/time/LocalDate;", false);
                mv.visitMethodInsn(INVOKEVIRTUAL, beanInternalName, setterName, "(Ljava/time/LocalDate;)V", false);

            } else if (typeCode == 12) {
                mv.visitVarInsn(ALOAD, 2);
                mv.visitVarInsn(ALOAD, 1);
                mv.visitMethodInsn(INVOKEVIRTUAL, "com/njydsz/common/json/reader/JSONReader", "readStringDirect", "()Ljava/lang/String;", false);
                mv.visitMethodInsn(INVOKESTATIC, "com/njydsz/common/json/asm/AsmBeanCodecGenerator", 
                    "parseDate", "(Ljava/lang/String;)Ljava/util/Date;", false);
                mv.visitMethodInsn(INVOKEVIRTUAL, beanInternalName, setterName, "(Ljava/util/Date;)V", false);

            } else if (typeCode == 13) {
                Class<?> elementType = getListElementType(field);
                String cachedDeserFieldName = "_list_deser_" + field.getName();
                mv.visitVarInsn(ALOAD, 2);
                mv.visitVarInsn(ALOAD, 1);
                mv.visitLdcInsn(Type.getType(getTypeDescriptorForClass(elementType)));
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, classInternalName, cachedDeserFieldName, 
                    "Lcom/njydsz/common/json/asm/AsmDeserializer;");
                mv.visitMethodInsn(INVOKESTATIC, "com/njydsz/common/json/asm/AsmBeanCodecGenerator", 
                    "deserializeListFieldWithDeserializer", 
                    "(Lcom/njydsz/common/json/reader/JSONReader;Ljava/lang/Class;Lcom/njydsz/common/json/asm/AsmDeserializer;)Ljava/util/List;", false);
                mv.visitMethodInsn(INVOKEVIRTUAL, beanInternalName, setterName, "(Ljava/util/List;)V", false);

            } else if (typeCode == 14) {
                mv.visitVarInsn(ALOAD, 2);
                mv.visitVarInsn(ALOAD, 1);
                mv.visitMethodInsn(INVOKEVIRTUAL, "com/njydsz/common/json/reader/JSONReader", "readObjectMap", "()Ljava/util/Map;", false);
                mv.visitMethodInsn(INVOKEVIRTUAL, beanInternalName, setterName, "(Ljava/util/Map;)V", false);

            } else if (typeCode == 15) {
                Class<?> nestedType = field.getType();
                String nestedTypeInternalName = nestedType.getName().replace('.', '/');
                String cachedDeserFieldName = "_nested_deser_" + field.getName();

                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, classInternalName, cachedDeserFieldName, 
                    "Lcom/njydsz/common/json/asm/AsmDeserializer;");
                Label hasDeserLabel = new Label();
                Label endDeserLabel = new Label();
                mv.visitJumpInsn(IFNONNULL, hasDeserLabel);

                mv.visitVarInsn(ALOAD, 2);
                mv.visitVarInsn(ALOAD, 1);
                mv.visitLdcInsn(Type.getType(getTypeDescriptorForClass(nestedType)));
                mv.visitMethodInsn(INVOKESTATIC, "com/njydsz/common/json/asm/AsmBeanCodecGenerator", 
                    "deserializeNestedField", "(Lcom/njydsz/common/json/reader/JSONReader;Ljava/lang/Class;)Ljava/lang/Object;", false);
                mv.visitTypeInsn(CHECKCAST, nestedTypeInternalName);
                mv.visitMethodInsn(INVOKEVIRTUAL, beanInternalName, setterName, 
                    "(" + getTypeDescriptor(nestedType) + ")V", false);
                mv.visitJumpInsn(GOTO, endDeserLabel);

                mv.visitLabel(hasDeserLabel);
                mv.visitVarInsn(ALOAD, 2);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, classInternalName, cachedDeserFieldName, 
                    "Lcom/njydsz/common/json/asm/AsmDeserializer;");
                mv.visitVarInsn(ALOAD, 1);
                mv.visitMethodInsn(INVOKEINTERFACE, "com/njydsz/common/json/asm/AsmDeserializer",
                    "deserialize", "(Lcom/njydsz/common/json/reader/JSONReader;)Ljava/lang/Object;", true);
                mv.visitTypeInsn(CHECKCAST, nestedTypeInternalName);
                mv.visitMethodInsn(INVOKEVIRTUAL, beanInternalName, setterName, 
                    "(" + getTypeDescriptor(nestedType) + ")V", false);

                mv.visitLabel(endDeserLabel);
            }
            
            mv.visitJumpInsn(GOTO, loopStart);
            
            mv.visitLabel(nextField);
        }
        
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKEVIRTUAL, "com/njydsz/common/json/reader/JSONReader", "skipValue", "()V", false);
        mv.visitJumpInsn(GOTO, loopStart);
        
        mv.visitLabel(loopEnd);

        mv.visitVarInsn(ALOAD, 2);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        cw.visitEnd();

        byte[] bytecode = cw.toByteArray();
        return defineDeserializerClass(className, bytecode, beanType);
    }

    /**
     * DateTimeFormatter 缓存（按 pattern 缓存，避免每次调用 DateTimeFormatter.ofPattern）。
     *
     * <p>对标 Jackson 的 DateTimeFormatter 缓存机制，消除重复 pattern 编译开销。</p>
     */
    private static final ConcurrentHashMap<String, DateTimeFormatter> FORMATTER_CACHE =
        new ConcurrentHashMap<>();

    /**
     * 获取或创建缓存的 DateTimeFormatter。
     *
     * @param pattern 日期格式模式
     * @return 缓存的 DateTimeFormatter 实例
     */
    private static DateTimeFormatter getCachedFormatter(String pattern) {
        return FORMATTER_CACHE.computeIfAbsent(pattern, DateTimeFormatter::ofPattern);
    }

    /**
     * 使用指定格式模式格式化日期对象（供 ASM 生成的字节码调用）
     *
     * @param dateValue 日期对象（LocalDateTime/LocalDate/Date）
     * @param pattern 格式模式（如 "yyyy-MM-dd HH:mm:ss"），null 或空时使用 toString()
     * @return 格式化后的字符串
     */
    public static String formatDate(Object dateValue, String pattern) {
        if (dateValue == null) {
            return null;
        }
        if (pattern == null || pattern.isEmpty()) {
            return dateValue.toString();
        }
        try {
            DateTimeFormatter formatter = getCachedFormatter(pattern);
            if (dateValue instanceof LocalDateTime ldt) {
                return ldt.format(formatter);
            } else if (dateValue instanceof LocalDate ld) {
                return ld.format(formatter);
            } else if (dateValue instanceof Date d) {
                return d.toInstant().atZone(ZoneId.systemDefault())
                    .toLocalDateTime().format(formatter);
            }
        } catch (Exception e) {
            log.warn("日期格式化失败, pattern: {}, 错误: {}", pattern, e.getMessage());
        }
        return dateValue.toString();
    }

    /**
     * 解析日期字符串（静态方法，供 ASM 生成的字节码调用）
     */
    public static Date parseDate(String dateStr) {
        try {
            return Date.from(Instant.parse(dateStr));
        } catch (Exception e) {
            try {
                DateTimeFormatter formatter = getCachedFormatter("yyyy-MM-dd'T'HH:mm:ss");
                LocalDateTime ldt = LocalDateTime.parse(dateStr, formatter);
                return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
            } catch (Exception e2) {
                try {
                    DateTimeFormatter formatter = getCachedFormatter("yyyy-MM-dd");
                    LocalDate ld = LocalDate.parse(dateStr, formatter);
                    return Date.from(ld.atStartOfDay(ZoneId.systemDefault()).toInstant());
                } catch (Exception e3) {
                    log.warn("日期解析失败: {}", dateStr);
                    return null;
                }
            }
        }
    }

    private static Field[] getSerializableFields(Class<?> clazz) {
        // Record 类：使用 RecordComponent 的 backing field
        if (clazz.isRecord()) {
            RecordComponent[] components = clazz.getRecordComponents();
            List<Field> result = new ArrayList<>(components.length);
            for (RecordComponent rc : components) {
                try {
                    Field f = clazz.getDeclaredField(rc.getName());
                    result.add(f);
                } catch (NoSuchFieldException e) {
                    // backing field 不存在，跳过
                }
            }
            return result.toArray(new Field[0]);
        }
        Field[] allFields = clazz.getDeclaredFields();
        List<Field> result = new ArrayList<>();
        for (Field f : allFields) {
            int mods = f.getModifiers();
            if (!Modifier.isStatic(mods) && !Modifier.isTransient(mods)) {
                result.add(f);
            }
        }
        return result.toArray(new Field[0]);
    }

    private static String getGetterName(Field field) {
        Class<?> type = field.getType();
        if (type == boolean.class || type == Boolean.class) {
            String name = field.getName();
            if (name.startsWith("is")) {
                return name;
            }
            return "is" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
        }
        String name = field.getName();
        return "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /**
     * 获取 Record 组件的访问器方法名（与组件名相同）
     */
    private static String getRecordAccessorName(Field field) {
        return field.getName();
    }

    private static String getSetterName(Field field) {
        String name = field.getName();
        return "set" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static Method findMethod(Class<?> beanType, String name) {
        for (Method m : beanType.getDeclaredMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == 0) {
                return m;
            }
        }
        return null;
    }

    private static Method findSetter(Class<?> beanType, String name, Class<?> fieldType) {
        for (Method m : beanType.getDeclaredMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == 1 &&
                m.getParameterTypes()[0].isAssignableFrom(fieldType)) {
                return m;
            }
        }
        return null;
    }

    private static String getTypeDescriptor(Class<?> type) {
        if (type == void.class) return "V";
        if (type == boolean.class) return "Z";
        if (type == byte.class) return "B";
        if (type == short.class) return "S";
        if (type == int.class) return "I";
        if (type == long.class) return "J";
        if (type == float.class) return "F";
        if (type == double.class) return "D";
        return "L" + type.getName().replace('.', '/') + ";";
    }

    /**
     * 获取类型的 ASM 描述符（用于 visitLdcInsn 加载 Class 对象）
     */
    private static String getTypeDescriptorForClass(Class<?> type) {
        if (type.isPrimitive()) {
            if (type == int.class) return "Ljava/lang/Integer;";
            if (type == long.class) return "Ljava/lang/Long;";
            if (type == double.class) return "Ljava/lang/Double;";
            if (type == float.class) return "Ljava/lang/Float;";
            if (type == boolean.class) return "Ljava/lang/Boolean;";
            if (type == short.class) return "Ljava/lang/Short;";
            if (type == byte.class) return "Ljava/lang/Byte;";
            if (type == char.class) return "Ljava/lang/Character;";
        }
        return "L" + type.getName().replace('.', '/') + ";";
    }

    private static int getTypeCode(Class<?> type) {
        if (type == String.class) return 1;
        if (type == int.class || type == Integer.class) return 2;
        if (type == long.class || type == Long.class) return 3;
        if (type == double.class || type == Double.class) return 4;
        if (type == float.class || type == Float.class) return 5;
        if (type == boolean.class || type == Boolean.class) return 6;
        if (type == short.class || type == Short.class) return 7;
        if (type == byte.class || type == Byte.class) return 8;
        if (type == char.class || type == Character.class) return 9;
        if (type == LocalDateTime.class) return 10;
        if (type == LocalDate.class) return 11;
        if (type == Date.class) return 12;
        if (Collection.class.isAssignableFrom(type)) return 13;
        if (Map.class.isAssignableFrom(type)) return 14;
        return 15;
    }

    /**
     * 获取 List 字段的元素类型
     */
    private static Class<?> getListElementType(Field field) {
        java.lang.reflect.Type genericType = field.getGenericType(); // FQN-OK: name conflict with org.objectweb.asm.Type
        if (genericType instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) genericType;
            java.lang.reflect.Type[] typeArgs = pt.getActualTypeArguments(); // FQN-OK: name conflict with org.objectweb.asm.Type
            if (typeArgs.length > 0 && typeArgs[0] instanceof Class) {
                return (Class<?>) typeArgs[0];
            }
        }
        return Object.class;
    }

    /**
     * 检查是否为简单类型
     *
     * <p>委托给 {@link com.njydsz.common.json.util.JsonTypeUtils} 统一实现。</p>
     */
    private static boolean isSimpleType(Class<?> type) {
        return JsonTypeUtils.isSimpleType(type);
    }

    /**
     * ASM 动态生成类前缀（已废弃：实际生成类名形如 {@code <beanType.getName()>_ASM_Serializer}，
     * 与宿主 Bean 同包；保留此常量仅为兼容历史引用，{@link SecureAsmClassLoader#defineInternal}
     * 已改用后缀校验）
     */
    private static final String ASM_CLASS_PREFIX = "generated.";

    /** ASM 序列化器类名后缀（类名形如 {@code <beanType>_ASM_Serializer}） */
    private static final String ASM_CLASS_SUFFIX_SER = "_ASM_Serializer";

    /** ASM 反序列化器类名后缀 */
    private static final String ASM_CLASS_SUFFIX_DESER = "_ASM_Deserializer";

    /**
     * 动态生成类数量上限，超过此阈值降级到反射模式
     */
    private static final int ASM_CLASS_THRESHOLD = 10000;

    /**
     * Metaspace 使用率告警阈值（80%）
     */
    private static final double METASPACE_WARN_THRESHOLD = 0.8;

    /**
     * 已生成的动态类总数
     */
    private static final AtomicInteger GENERATED_CLASS_COUNT = new AtomicInteger(0);

    /**
     * 是否已降级到反射模式
     */
    private static volatile boolean degradedToReflection = false;

    /**
     * 按源 ClassLoader 分组的 SecureClassLoader 缓存
     * 每个父 ClassLoader 对应一个 SecureClassLoader，避免元空间碎片化
     */
    private static final ConcurrentHashMap<ClassLoader, SecureClassLoader> CLASSLOADER_CACHE =
            new ConcurrentHashMap<>();

    /**
     * 获取或创建指定父 ClassLoader 对应的 SecureClassLoader
     *
     * @param parentClassLoader 父 ClassLoader
     * @return SecureClassLoader 实例
     */
    private static SecureClassLoader getOrCreateSecureClassLoader(ClassLoader parentClassLoader) {
        return CLASSLOADER_CACHE.computeIfAbsent(
                parentClassLoader != null ? parentClassLoader : getPlatformClassLoader(),
                k -> new SecureAsmClassLoader(k));
    }

    /**
     * 获取 Platform ClassLoader（Java 9+）
     */
    private static ClassLoader getPlatformClassLoader() {
        try {
            return (ClassLoader) ClassLoader.class.getMethod("getPlatformClassLoader").invoke(null);
        } catch (Exception e) {
            return ClassLoader.getSystemClassLoader();
        }
    }

    /**
     * ASM 降级阈值分级
     */
    private static final int ASM_WARN_THRESHOLD = 5000;
    private static final int ASM_DEGRADE_THRESHOLD = 8000;

    /**
     * ASM 降级级别
     */
    public enum AsmLevel {
        /** ASM 字节码生成（最优性能） */
        ASM,
        /** ASM 生效但接近阈值，输出告警 */
        ASM_WARN,
        /** 接近阈值，仅对新类降级到反射 */
        DEGRADED,
        /** 完全降级到反射模式 */
        REFLECTION
    }

    /**
     * 获取当前 ASM 降级级别。
     *
     * @return 降级级别
     */
    public static AsmLevel getAsmLevel() {
        if (degradedToReflection) {
            return AsmLevel.REFLECTION;
        }
        int count = GENERATED_CLASS_COUNT.get();
        if (count >= ASM_CLASS_THRESHOLD) {
            return AsmLevel.REFLECTION;
        }
        if (count >= ASM_DEGRADE_THRESHOLD) {
            return AsmLevel.DEGRADED;
        }
        if (count >= ASM_WARN_THRESHOLD) {
            return AsmLevel.ASM_WARN;
        }
        return AsmLevel.ASM;
    }

    /**
     * 获取 ASM 统计信息。
     *
     * @return 统计信息字符串
     */
    public static String getAsmStats() {
        return String.format(
            "ASM Level: %s, Generated: %d/%d, Degraded: %b",
            getAsmLevel(), GENERATED_CLASS_COUNT.get(), ASM_CLASS_THRESHOLD, degradedToReflection);
    }

    /**
     * 检查是否允许继续生成 ASM 类
     *
     * @return true 如果允许生成，false 如果应降级到反射
     */
    private static boolean allowGenerate() {
        if (degradedToReflection) {
            return false;
        }

        int count = GENERATED_CLASS_COUNT.incrementAndGet();
        if (count > ASM_CLASS_THRESHOLD) {
            degradedToReflection = true;
            log.warn("ASM 动态生成类数量 {} 超过阈值 {}，降级到反射模式", count, ASM_CLASS_THRESHOLD);
            return false;
        }

        checkMetaspaceUsage();
        return true;
    }

    /**
     * 检查 Metaspace 使用情况
     */
    private static void checkMetaspaceUsage() {
        try {
            for (MemoryPoolMXBean bean : ManagementFactory.getMemoryPoolMXBeans()) {
                if ("Metaspace".equals(bean.getName())) {
                    MemoryUsage usage = bean.getUsage();
                    long max = usage.getMax();
                    long used = usage.getUsed();
                    if (max > 0) {
                        double ratio = (double) used / max;
                        if (ratio > METASPACE_WARN_THRESHOLD && !metaspaceWarned) {
                            metaspaceWarned = true;
                            log.warn("Metaspace 使用率过高: {}/{} ({:.1f}%)，" +
                                    "建议增加 -XX:MaxMetaspaceSize 或检查动态类生成",
                                    formatBytes(used), formatBytes(max), ratio * 100);
                        }
                    }
                    break;
                }
            }
        } catch (Exception e) {
            // 忽略 Metaspace 检查异常
        }
    }

    private static volatile boolean metaspaceWarned = false;

    /**
     * 格式化字节数
     */
    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    /**
     * 基于 SecureClassLoader 的 ASM 类加载器
     * 每个实例与一个父 ClassLoader 绑定，支持独立的类定义空间
     */
    private static final class SecureAsmClassLoader extends SecureClassLoader {
        private SecureAsmClassLoader(ClassLoader parent) {
            super(parent);
        }

        Class<?> defineInternal(String name, byte[] b, int off, int len, CodeSource cs) {
            // 仅允许 ASM 生成的类名后缀，防止任意类名 defineClass 注入。
            // 类名形如 <beanType.getName()>_ASM_Serializer / _ASM_Deserializer，与宿主 Bean 同包，
            // 便于直接访问 package-private 成员（对齐 fastjson2 同包生成策略）。
            if (!name.endsWith(ASM_CLASS_SUFFIX_SER) && !name.endsWith(ASM_CLASS_SUFFIX_DESER)) {
                throw new SecurityException("ASM class name must end with " + ASM_CLASS_SUFFIX_SER
                        + " or " + ASM_CLASS_SUFFIX_DESER + ", got: " + name);
            }
            return super.defineClass(name, b, off, len, cs);
        }
    }

    private static final Logger log = LoggerFactory.getLogger(AsmBeanCodecGenerator.class);

    private static <T> Class<? extends AsmSerializer<T>> defineClass(String className, byte[] bytecode, Class<T> beanType) {
        if (!allowGenerate()) {
            throw new RuntimeException("ASM class generation disabled (Metaspace high or class threshold exceeded): " + className);
        }
        try {
            SecureClassLoader loader = getOrCreateSecureClassLoader(beanType.getClassLoader());
            Class<?> clazz = ((SecureAsmClassLoader) loader).defineInternal(className, bytecode, 0, bytecode.length, null);
            return captureSerializerClass(clazz);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to define ASM class: " + className, e);
        }
    }

    private static <T> Class<? extends AsmSerializer<T>> captureSerializerClass(Class<?> clazz) {
        return (Class<? extends AsmSerializer<T>>) clazz;
    }

    private static <T> Class<? extends AsmDeserializer<T>> defineDeserializerClass(String className, byte[] bytecode, Class<T> beanType) {
        if (!allowGenerate()) {
            throw new RuntimeException("ASM class generation disabled (Metaspace high or class threshold exceeded): " + className);
        }
        try {
            SecureClassLoader loader = getOrCreateSecureClassLoader(beanType.getClassLoader());
            Class<?> clazz = ((SecureAsmClassLoader) loader).defineInternal(className, bytecode, 0, bytecode.length, null);
            return captureDeserializerClass(clazz);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to define ASM class: " + className, e);
        }
    }

    private static <T> Class<? extends AsmDeserializer<T>> captureDeserializerClass(Class<?> clazz) {
        return (Class<? extends AsmDeserializer<T>>) clazz;
    }

    /**
     * 检查 ASM 是否可用（未降级到反射模式）
     *
     * @return true 如果 ASM 模式可用
     */
    public static boolean isAsmAvailable() {
        return !degradedToReflection && GENERATED_CLASS_COUNT.get() < ASM_CLASS_THRESHOLD;
    }

    /**
     * 获取当前动态生成类数量
     *
     * @return 已生成的 ASM 类总数
     */
    public static int getGeneratedClassCount() {
        return GENERATED_CLASS_COUNT.get();
    }

    /**
     * 重置 ASM 状态（用于测试）
     */
    static void resetForTest() {
        degradedToReflection = false;
        GENERATED_CLASS_COUNT.set(0);
        CLASSLOADER_CACHE.clear();
        metaspaceWarned = false;
    }

    /**
     * 生成 ASM 序列化器（非类型参数版本，接受 Class<?>）
     */
    public static Class<? extends AsmSerializer<?>> generateSerializerForType(Class<?> beanType) throws Exception {
        return generateSerializer(beanType);
    }

    /**
     * 生成 ASM 反序列化器（非类型参数版本，接受 Class<?>）
     */
    public static Class<? extends AsmDeserializer<?>> generateDeserializerForType(Class<?> beanType) throws Exception {
        return generateDeserializer(beanType);
    }
}
