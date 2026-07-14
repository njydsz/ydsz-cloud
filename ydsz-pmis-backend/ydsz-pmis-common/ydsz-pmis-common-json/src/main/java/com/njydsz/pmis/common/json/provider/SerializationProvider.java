package com.njydsz.pmis.common.json.provider;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.njydsz.pmis.common.json.annotation.JsonClass;
import com.njydsz.pmis.common.json.asm.AsmSerializer;
import com.njydsz.pmis.common.json.cache.AsmCodecCache;
import com.njydsz.pmis.common.json.cache.BeanSerializerCache;
import com.njydsz.pmis.common.json.cache.FieldMeta;
import com.njydsz.pmis.common.json.cache.SerializerCache;
import com.njydsz.pmis.common.json.exception.JsonSerializationException;
import com.njydsz.pmis.common.json.naming.PropertyNamingStrategy;
import com.njydsz.pmis.common.json.writer.BeanSerializer;
import com.njydsz.pmis.common.json.writer.JSONWriter;

/**
 * Json 序列化提供者（架构层）
 *
 * <p>架构层级：Json => Engine => Provider => Parser</p>
 *
 * <p><b>ThreadLocal 清理机制：</b></p>
 * <ul>
 *   <li>序列化完成后自动清理循环引用检测集。</li>
 *   <li>使用 try-finally 确保异常时也清理</li>
 * </ul>
 *
 * <p><b>FastJSON2 深度优化技术：</b></p>
 * <ul>
 *   <li>精确容量预分配 - 基于对象结构预估 JSON 大小，避免 StringBuilder 扩容</li>
 *   <li>快速数字编码 - 直接写入字符数组，避免方法调用和边界检查</li>
 *   <li>UTF-8 编码优化 - 针对 ASCII 字符集优化</li>
 *   <li>热路径内联 - 减少虚方法调用和方法调用开销</li>
 * </ul>
 *
 * @since 1.3.0
 * @since 1.3.0
 */
public final class SerializationProvider {

    /** StringBuilder 池最大容量*/
    private static final int MAX_SB_CAPACITY = 65536;

    /** 小 JSON StringBuilder 初始容量（适合简单 Bean）*/
    private static final int SMALL_SB_CAPACITY = 1024;

    /** 中 JSON StringBuilder 初始容量（适合一般 Bean）*/
    private static final int MEDIUM_SB_CAPACITY = 4096;

    /** 大 JSON StringBuilder 初始容量（适合大集合/复杂嵌套）*/
    private static final int LARGE_SB_CAPACITY = 16384;

    /**
     * StringBuilder 池（ThreadLocal 复用，大小分级策略）
     *
     * <p>优化策略：</p>
     * <ul>
     *   <li>默认使用 MEDIUM_SB_CAPACITY（4096），适合大多数场景</li>
     *   <li>序列化完成后，如果容量超过 MAX_SB_CAPACITY（65536），缩容到 MEDIUM_SB_CAPACITY</li>
     *   <li>避免偶尔序列化大对象后，线程池中长期持有大缓冲区导致内存浪费</li>
     * </ul>
     */
    private static final ThreadLocal<StringBuilder> SB_POOL =
        ThreadLocal.withInitial(() -> new StringBuilder(MEDIUM_SB_CAPACITY));

    /** JSONWriter 池（ThreadLocal 复用）*/
    static final ThreadLocal<JSONWriter> FAST_WRITER_POOL =
        ThreadLocal.withInitial(() -> new JSONWriter(4096));

    /** 循环引用检测 - 已序列化对象集合（使用 IdentityHashMap 保证引用比较）*/
    static final ThreadLocal<Set<Object>> SERIALIZING_OBJECTS =
        ThreadLocal.withInitial(() -> Collections.newSetFromMap(new IdentityHashMap<>(64)));

    /** 当前视图类（用于字段过滤，ThreadLocal 传递上下文）*/
    static final ThreadLocal<Class<?>> CURRENT_VIEW_CLASS = ThreadLocal.withInitial(() -> null);

    /** 最近使用的列表元素序列化器缓存（ThreadLocal，避免每次列表序列化都查找 ConcurrentHashMap）*/
    private static final ThreadLocal<AsmSerializer<Object>> CACHED_LIST_SERIALIZER =
        ThreadLocal.withInitial(() -> null);

    /** 最近使用的列表元素类型缓存（配合 CACHED_LIST_SERIALIZER 使用）*/
    private static final ThreadLocal<Class<?>> CACHED_LIST_ELEMENT_CLASS =
        ThreadLocal.withInitial(() -> null);

    /** 是否输出 null 值（ThreadLocal）*/
    private static final ThreadLocal<Boolean> WRITE_NULLS =
        ThreadLocal.withInitial(() -> false);

    /** 是否格式化输出（ThreadLocal）*/
    private static final ThreadLocal<Boolean> PRETTY_PRINT =
        ThreadLocal.withInitial(() -> false);

    /** 循环引用处理策略名称（ThreadLocal）：REF / IGNORE / ERROR */
    private static final ThreadLocal<String> CIRCULAR_REFERENCE_STRATEGY =
        ThreadLocal.withInitial(() -> "REF");

    /** 枚举是否使用序号序列化（ThreadLocal）*/
    private static final ThreadLocal<Boolean> SERIALIZE_ENUM_USING_ORDINAL =
        ThreadLocal.withInitial(() -> false);

    /** Bean 序列化信息缓存*/
    private static final ConcurrentMap<Class<?>, BeanSerializerInfo> BEAN_SERIALIZER_INFO_CACHE = new ConcurrentHashMap<>(1024);

    private SerializationProvider() {
        throw new UnsupportedOperationException();
    }

    public static void setNamingStrategy(PropertyNamingStrategy strategy) {
        FieldMetadataLoader.NAMING_STRATEGY.set(strategy);
    }

    public static PropertyNamingStrategy getNamingStrategy() {
        return FieldMetadataLoader.NAMING_STRATEGY.get();
    }

    public static void setWriteNulls(boolean writeNulls) {
        WRITE_NULLS.set(writeNulls);
    }

    public static boolean isWriteNulls() {
        return WRITE_NULLS.get();
    }

    public static void setPrettyPrint(boolean prettyPrint) {
        PRETTY_PRINT.set(prettyPrint);
    }

    public static boolean isPrettyPrint() {
        return PRETTY_PRINT.get();
    }

    public static void setCircularReferenceStrategy(String strategyName) {
        CIRCULAR_REFERENCE_STRATEGY.set(strategyName);
    }

    public static String getCircularReferenceStrategy() {
        return CIRCULAR_REFERENCE_STRATEGY.get();
    }

    public static void setSerializeEnumUsingOrdinal(boolean ordinal) {
        SERIALIZE_ENUM_USING_ORDINAL.set(ordinal);
    }

    public static boolean isSerializeEnumUsingOrdinal() {
        return SERIALIZE_ENUM_USING_ORDINAL.get();
    }

    /**
     * 清理当前线程的 ThreadLocal 对象
     *
     * <p>在线程池环境中，应在任务完成后或线程归还前调用此方法</p>
     */
    public static void clearThreadLocals() {
        SB_POOL.remove();
        SERIALIZING_OBJECTS.remove();
        FieldMetadataLoader.NAMING_STRATEGY.remove();
        CURRENT_VIEW_CLASS.remove();
    }

    /**
     * 获取适合指定预估大小的 StringBuilder（大小分级策略）
     *
     * <p>根据预估的 JSON 大小选择合适容量的 StringBuilder，避免：
     * <ul>
     *   <li>小 JSON 使用大 StringBuilder 浪费内存</li>
     *   <li>大 JSON 使用小 StringBuilder 导致多次扩容</li>
     * </ul>
     * 分级阈值：
     * <ul>
     *   <li>预估 < SMALL_SB_CAPACITY(1024)：小 JSON，适合简单 Bean</li>
     *   <li>预估 < MEDIUM_SB_CAPACITY(4096)：中 JSON，适合一般 Bean</li>
     *   <li>预估 < LARGE_SB_CAPACITY(16384)：大 JSON，适合大集合/复杂嵌套</li>
     *   <li>预估 > LARGE_SB_CAPACITY：超大 JSON，按需分配</li>
     * </ul>
     * </p>
     *
     * @param estimatedSize 预估的 JSON 输出大小
     * @return 适合大小的 StringBuilder
     */
    private static StringBuilder getSizedStringBuilder(int estimatedSize) {
        StringBuilder sb = SB_POOL.get();

        // 缩容保护：如果池中 StringBuilder 过大，根据预估大小缩容到合适的级别
        if (sb.capacity() > MAX_SB_CAPACITY) {
            int targetCapacity;
            if (estimatedSize <= SMALL_SB_CAPACITY) {
                targetCapacity = SMALL_SB_CAPACITY;
            } else if (estimatedSize <= MEDIUM_SB_CAPACITY) {
                targetCapacity = MEDIUM_SB_CAPACITY;
            } else if (estimatedSize <= LARGE_SB_CAPACITY) {
                targetCapacity = LARGE_SB_CAPACITY;
            } else {
                targetCapacity = MEDIUM_SB_CAPACITY; // 超大 JSON 缩容到中等，下次按需扩容
            }
            sb = new StringBuilder(targetCapacity);
            SB_POOL.set(sb);
        }

        // 扩容保护：如果预估大小超过当前容量，预分配
        if (estimatedSize > sb.capacity()) {
            sb.ensureCapacity(estimatedSize);
        }

        sb.setLength(0);
        return sb;
    }

    /**
     * 序列化对象
     */
    public static String serialize(Object obj) {
        if (obj == null) {
            return "null";
        }

        Class<?> clazz = obj.getClass();

        // 快速路径：Bean 类型直接使用 ASM 序列化器，跳过 StringBuilder 中转
        if (!(obj instanceof Collection) && !(obj instanceof Map) && !clazz.isArray()) {
            try {
                JSONWriter writer = FAST_WRITER_POOL.get();
                writer.reset();
                if (AsmCodecCache.trySerialize(obj, writer)) {
                    return writer.toString();
                }
            } catch (Exception e) {
            }
        }

        // 快速路径：Collection 类型直接使用 JSONWriter，跳过 StringBuilder 中转
        if (obj instanceof Collection) {
            JSONWriter writer = FAST_WRITER_POOL.get();
            writer.reset();
            Collection<?> coll = (Collection<?>) obj;
            if (!coll.isEmpty()) {
                writer.preAllocate(coll.size() * 64);
                // 优化：使用 ThreadLocal 缓存的序列化器，避免每次查找 ConcurrentHashMap
                AsmSerializer<Object> serializer = CACHED_LIST_SERIALIZER.get();
                if (serializer == null) {
                    Object first = null;
                    if (coll instanceof List) {
                        first = ((List<?>) coll).get(0);
                    } else {
                        first = coll.iterator().next();
                    }
                    if (first != null) {
                        try {
                            AsmSerializer<?> rawSerializer = AsmCodecCache.getOrCreateSerializerForType(first.getClass());
                            if (rawSerializer != null) {
                                serializer = captureSerializer(rawSerializer);
                                CACHED_LIST_SERIALIZER.set(serializer);
                                CACHED_LIST_ELEMENT_CLASS.set(first.getClass());
                            }
                        } catch (Exception e) {
                        }
                    }
                }
                if (serializer != null) {
                    writer.writeCollectionWithSerializer(coll, serializer);
                    return writer.toString();
                }
            }
            writer.writeCollection(coll);
            return writer.toString();
        }

        // 快速路径：Map 类型直接使用 JSONWriter
        if (obj instanceof Map) {
            JSONWriter writer = FAST_WRITER_POOL.get();
            writer.reset();
            writer.writeMap((Map<?, ?>) obj);
            return writer.toString();
        }

        // 使用大小分级策略获取 StringBuilder
        StringBuilder sb = getSizedStringBuilder(256);

        Set<Object> objects = SERIALIZING_OBJECTS.get();
        objects.clear();

        try {
            if (!tryFastSerialize(obj, sb)) {
                ValueWriter.writeValue(obj, sb);
            }
        } catch (JsonSerializationException e) {
            throw e;
        } catch (Exception e) {
            throw new JsonSerializationException(
                JsonSerializationException.SERIALIZATION_ERROR,
                "Serialization failed for " + obj.getClass().getName() + ": " + e.getMessage(),
                e
            );
        } finally {
            objects.clear();
        }

        return sb.toString();
    }

    /**
     * 序列化对象（带特性配置）
     *
     * @param obj 对象
     * @param features 特性标志（位运算值）
     * @return JSON 字符串
     */
    public static String serialize(Object obj, long features) {
        if (obj == null) {
            return "null";
        }

        // 使用大小分级策略获取 StringBuilder
        StringBuilder sb = getSizedStringBuilder(256);

        Set<Object> objects = SERIALIZING_OBJECTS.get();
        objects.clear();

        try {
            if (JSONWriter.Feature.PrettyPrint.isEnabled(features)) {
                ValueFormatter.formatValue(obj, sb, 0);
            } else {
                ValueWriter.writeValue(obj, sb);
            }
        } catch (JsonSerializationException e) {
            throw e;
        } catch (Exception e) {
            throw new JsonSerializationException(
                JsonSerializationException.SERIALIZATION_ERROR,
                "Serialization failed for " + obj.getClass().getName() + ": " + e.getMessage(),
                e
            );
        } finally {
            objects.clear();
        }

        return sb.toString();
    }

    /**
     * 格式化序列化（带缩进）
     */
    public static String format(Object obj) {
        if (obj == null) {
            return "null";
        }

        // 格式化输出通常更大，使用较大的预估大小
        StringBuilder sb = getSizedStringBuilder(LARGE_SB_CAPACITY);

        Set<Object> objects = SERIALIZING_OBJECTS.get();
        objects.clear();

        try {
            ValueFormatter.formatValue(obj, sb, 0);
        } finally {
            objects.clear();
        }

        return sb.toString();
    }

    /**
     * 序列化对象（带视图过滤）
     *
     * @param obj 要序列化的对象
     * @param viewClass 视图类
     * @return JSON 字符串
     */
    public static String serializeWithView(Object obj, Class<?> viewClass) {
        if (obj == null) {
            return "null";
        }

        StringBuilder sb = getSizedStringBuilder(256);

        Set<Object> objects = SERIALIZING_OBJECTS.get();
        objects.clear();

        Class<?> previousView = CURRENT_VIEW_CLASS.get();
        CURRENT_VIEW_CLASS.set(viewClass);
        try {
            ValueWriter.writeValue(obj, sb);
        } catch (JsonSerializationException e) {
            throw e;
        } catch (Exception e) {
            throw new JsonSerializationException(
                "Failed to serialize object with view: " + obj.getClass().getName(), e
            );
        } finally {
            CURRENT_VIEW_CLASS.set(previousView);
            objects.clear();
        }

        return sb.toString();
    }

    /**
     * 序列化对象（带视图过滤和格式化）
     *
     * @param obj 要序列化的对象
     * @param viewClass 视图类
     * @param pretty 是否格式化
     * @return JSON 字符串
     */
    public static String serializeWithView(Object obj, Class<?> viewClass, boolean pretty) {
        if (obj == null) {
            return "null";
        }

        // 格式化输出使用较大预估大小
        StringBuilder sb = getSizedStringBuilder(pretty ? LARGE_SB_CAPACITY : 256);

        Set<Object> objects = SERIALIZING_OBJECTS.get();
        objects.clear();

        Class<?> previousView = CURRENT_VIEW_CLASS.get();
        CURRENT_VIEW_CLASS.set(viewClass);
        try {
            if (pretty) {
                ValueFormatter.formatValue(obj, sb, 0);
            } else {
                ValueWriter.writeValue(obj, sb);
            }
        } catch (JsonSerializationException e) {
            throw e;
        } catch (Exception e) {
            throw new JsonSerializationException(
                "Failed to serialize object with view: " + obj.getClass().getName(), e
            );
        } finally {
            CURRENT_VIEW_CLASS.set(previousView);
            objects.clear();
        }

        return sb.toString();
    }

    /**
     * 序列化对象（FastJSON2 快速路径）
     *
     * <p>当满足以下条件时使用快速路径：</p>
     * <ul>
     *   <li>无类级别注解</li>
     *   <li>无字段级别注入</li>
     *   <li>无视图过滤</li>
     * </ul>
     *
     * @return true 如果使用了快速路径
     */
    private static boolean tryFastSerialize(Object obj, StringBuilder sb) {
        if (obj == null) {
            return false;
        }

        Class<?> clazz = obj.getClass();

        // 排除集合、Map、数组类型
        if (obj instanceof Collection ||
            obj instanceof Map ||
            clazz.isArray()) {
            return false;
        }

        // 检查是否可以使用快速路径
        JsonClass classAnnotation = clazz.getAnnotation(JsonClass.class);
        if (classAnnotation != null) {
            return false;
        }

        // 检查是否有视图过滤
        if (CURRENT_VIEW_CLASS.get() != null) {
            return false;
        }

        // 优先使用 ASM 序列化器（直接 getter 调用，无反射开销）
        try {
            JSONWriter writer = FAST_WRITER_POOL.get();
            writer.reset();
            if (AsmCodecCache.trySerialize(obj, writer)) {
                sb.append(writer.toString());
                return true;
            }
        } catch (Exception e) {
        }

        // 获取或创建 BeanSerializer
        FieldMeta[] fields = SerializerCache.getFieldMeta(clazz);
        if (fields == null) {
            fields = FieldMetadataLoader.loadFields(clazz);
            SerializerCache.putFieldMeta(clazz, fields);
        }

        // 检查是否有字段注解
        if (FieldMetadataLoader.hasFieldAnnotations(fields)) {
            return false;
        }

        // 使用 FastJSON2 JSONWriter 进行快速序列化（复用ThreadLocal 池）
        JSONWriter writer = FAST_WRITER_POOL.get();
        writer.reset();

        BeanSerializer beanSerializer =
            BeanSerializerCache.getOrCreate(clazz, fields);

        beanSerializer.write(obj, writer);
        sb.append(writer.toString());
        return true;
    }

    /**
     * Bean 序列化信息（预计算）
     */
    static final class BeanSerializerInfo {
        /** 有效字段数组（跳过 shouldSkip 的字段） */
        final FieldMeta[] validFields;

        /** 预估的 JSON 大小 */
        final int estimatedSize;

        BeanSerializerInfo(FieldMeta[] validFields, int estimatedSize) {
            this.validFields = validFields;
            this.estimatedSize = estimatedSize;
        }
    }

    /**
     * 获取或创建 BeanSerializerInfo（架构优化）
     */
    static BeanSerializerInfo getOrCreateBeanSerializer(Class<?> clazz, FieldMeta[] fields) {
        BeanSerializerInfo info = BEAN_SERIALIZER_INFO_CACHE.get(clazz);
        if (info == null) {
            // 计算有效字段
            int count = 0;
            for (FieldMeta field : fields) {
                if (!field.shouldSkip()) {
                    count++;
                }
            }

            FieldMeta[] validFields = new FieldMeta[count];
            int idx = 0;
            int estimatedSize = 2; // {}

            for (FieldMeta field : fields) {
                if (field.shouldSkip()) {
                    continue;
                }
                validFields[idx++] = field;
                estimatedSize += field.jsonKeyLen + 16;
            }

            info = new BeanSerializerInfo(validFields, estimatedSize);
            BEAN_SERIALIZER_INFO_CACHE.put(clazz, info);
        }
        return info;
    }

    private static AsmSerializer<Object> captureSerializer(AsmSerializer<?> serializer) {
        return (AsmSerializer<Object>) serializer;
    }

    /**
     * ThreadLocal 快照（用于单次配置序列化的线程安全保存/恢复）。
     *
     * <p>构造时捕获当前线程所有 ThreadLocal 序列化参数，
     * 调用 {@link #restore()} 恢复原始值。避免修改全局单例。</p>
     *
     * @since 1.4.0
     */
    public static final class ThreadLocalSnapshot {
        private final PropertyNamingStrategy namingStrategy;
        private final boolean writeNulls;
        private final boolean prettyPrint;
        private final String circularRefStrategy;
        private final boolean serializeEnumUsingOrdinal;

        /**
         * 捕获当前线程的 ThreadLocal 序列化参数快照。
         */
        public ThreadLocalSnapshot() {
            this.namingStrategy = SerializationProvider.getNamingStrategy();
            this.writeNulls = SerializationProvider.isWriteNulls();
            this.prettyPrint = SerializationProvider.isPrettyPrint();
            this.circularRefStrategy = SerializationProvider.getCircularReferenceStrategy();
            this.serializeEnumUsingOrdinal = SerializationProvider.isSerializeEnumUsingOrdinal();
        }

        /**
         * 恢复快照中保存的 ThreadLocal 序列化参数。
         */
        public void restore() {
            SerializationProvider.setNamingStrategy(namingStrategy);
            SerializationProvider.setWriteNulls(writeNulls);
            SerializationProvider.setPrettyPrint(prettyPrint);
            SerializationProvider.setCircularReferenceStrategy(circularRefStrategy);
            SerializationProvider.setSerializeEnumUsingOrdinal(serializeEnumUsingOrdinal);
        }
    }
}
