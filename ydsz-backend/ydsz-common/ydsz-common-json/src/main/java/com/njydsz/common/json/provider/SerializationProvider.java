package com.njydsz.common.json.provider;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.json.annotation.YdszJsonClass;
import com.njydsz.common.json.annotation.JsonSerialize;
import com.njydsz.common.json.api.JsonSerializer;
import com.njydsz.common.json.asm.AsmSerializer;
import com.njydsz.common.json.cache.AsmCodecCache;
import com.njydsz.common.json.cache.BeanSerializerCache;
import com.njydsz.common.json.cache.FieldMeta;
import com.njydsz.common.json.cache.SerializerCache;
import com.njydsz.common.json.exception.JsonSerializationException;
import com.njydsz.common.json.naming.PropertyNamingStrategy;
import com.njydsz.common.json.writer.BeanSerializer;
import com.njydsz.common.json.writer.JSONWriter;

/**
 * YdszJson 序列化提供者（架构层）
 *
 * <p>架构层级：YdszJson => Engine => Provider => Parser</p>
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
 * @author ydsz-team
 * @since 1.0.0
 */
public final class SerializationProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(SerializationProvider.class);

    /** ASM 序列化降级计数器 */
    private static final AtomicLong ASM_DOWNGRADE_COUNT = new AtomicLong(0);
    /** StringBuilder 池最大容量*/
    private static final int MAX_SB_CAPACITY = 65536;

    /** 小 JSON StringBuilder 初始容量（适合简单 Bean）*/
    private static final int SMALL_SB_CAPACITY = 1024;

    /** 中 JSON StringBuilder 初始容量（适合一般 Bean）*/
    private static final int MEDIUM_SB_CAPACITY = 4096;

    /** 大 JSON StringBuilder 初始容量（适合大集合/复杂嵌套）*/
    private static final int LARGE_SB_CAPACITY = 16384;

    /**
     * 所有序列化上下文状态（含 StringBuilder 池、JSONWriter 池、循环引用检测集、
     * 视图类、列表序列化器缓存、排除字段集合、writeNulls、prettyPrint、
     * circularRefStrategy、serializeEnumUsingOrdinal 等 11 个原 ThreadLocal 字段）
     * 已合并到 {@link SerializationContext#CONTEXT} 单一 ThreadLocal 中。
     */

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
        SerializationContext.CONTEXT.get().writeNulls = writeNulls;
    }

    public static boolean isWriteNulls() {
        return SerializationContext.CONTEXT.get().writeNulls;
    }

    public static void setPrettyPrint(boolean prettyPrint) {
        SerializationContext.CONTEXT.get().prettyPrint = prettyPrint;
    }

    public static boolean isPrettyPrint() {
        return SerializationContext.CONTEXT.get().prettyPrint;
    }

    public static void setCircularReferenceStrategy(String strategyName) {
        SerializationContext.CONTEXT.get().circularRefStrategy = strategyName;
    }

    public static String getCircularReferenceStrategy() {
        return SerializationContext.CONTEXT.get().circularRefStrategy;
    }

    public static void setSerializeEnumUsingOrdinal(boolean ordinal) {
        SerializationContext.CONTEXT.get().serializeEnumUsingOrdinal = ordinal;
    }

    public static boolean isSerializeEnumUsingOrdinal() {
        return SerializationContext.CONTEXT.get().serializeEnumUsingOrdinal;
    }

    /**
     * 设置当前线程的全局日期格式。
     *
     * @param dateFormat 日期格式字符串，空字符串表示使用默认 ISO 格式
     * @since 1.0.0
     */
    public static void setDateFormat(String dateFormat) {
        SerializationContext.CONTEXT.get().dateFormat = dateFormat != null ? dateFormat : "";
    }

    /**
     * 获取当前线程的全局日期格式。
     *
     * @return 日期格式字符串，空字符串表示使用默认 ISO 格式
     * @since 1.0.0
     */
    public static String getDateFormat() {
        return SerializationContext.CONTEXT.get().dateFormat;
    }

    /**
     * 设置序列化失败时是否抛出异常。
     *
     * @param failOnError true=抛异常，false=记录日志返回 null
     * @since 1.0.0
     */
    public static void setFailOnError(boolean failOnError) {
        SerializationContext.CONTEXT.get().failOnError = failOnError;
    }

    /**
     * 获取序列化失败时是否抛出异常。
     *
     * @return true=抛异常，false=记录日志返回 null
     * @since 1.0.0
     */
    public static boolean isFailOnError() {
        return SerializationContext.CONTEXT.get().failOnError;
    }

    /**
     * 清理当前线程的 ThreadLocal 对象
     *
     * <p>在线程池环境中，应在任务完成后或线程归还前调用此方法</p>
     *
     * <p>注：11 个原 ThreadLocal 已合并到 {@link SerializationContext#CONTEXT}，
     * 调用 {@link SerializationContext#clear()} 一次即可全部清理。
     * {@code FieldMetadataLoader.NAMING_STRATEGY} 属于另一类，不在合并范围内，仍需单独清理。</p>
     */
    public static void clearThreadLocals() {
        SerializationContext.clear();
        FieldMetadataLoader.NAMING_STRATEGY.remove();
    }

    /**
     * 设置需要排除的字段名集合。
     *
     * <p>在序列化时，集合中的字段名（JSON 名称）对应的字段将被跳过，不输出到 JSON 中。
     * 适用于列权限字段过滤等场景。
     *
     * @param fieldNames 需要排除的字段名集合，null 表示清除排除
     */
    public static void setExcludedFields(Set<String> fieldNames) {
        SerializationContext.CONTEXT.get().excludedFields = fieldNames;
    }

    /**
     * 获取需要排除的字段名集合。
     *
     * @return 排除集合，null 表示不排除任何字段
     */
    public static Set<String> getExcludedFields() {
        return SerializationContext.CONTEXT.get().excludedFields;
    }

    /**
     * 判断指定字段是否被排除。
     *
     * <p>支持两种输入格式：
     * <ul>
     *   <li>纯字段名：{@code fieldName}</li>
     *   <li>JSON 键格式：{@code "fieldName":}（BeanSerializer 内部格式）</li>
     * </ul>
     *
     * @param keyOrName 字段的 JSON 键或纯名称
     * @return true 表示该字段应被排除
     */
    public static boolean isFieldExcluded(String keyOrName) {
        Set<String> excluded = SerializationContext.CONTEXT.get().excludedFields;
        if (excluded == null || keyOrName == null) {
            return false;
        }
        // 直接匹配纯字段名
        if (excluded.contains(keyOrName)) {
            return true;
        }
        // 尝试从 JSON 键格式 "fieldName": 中提取纯名称
        if (keyOrName.length() >= 3 && keyOrName.charAt(0) == '"') {
            int end = keyOrName.indexOf('"', 1);
            if (end > 0) {
                String name = keyOrName.substring(1, end);
                return excluded.contains(name);
            }
        }
        return false;
    }

    // ==================== 向后兼容的静态访问器（原 ThreadLocal 字段的替代） ====================

    /**
     * 获取当前线程的 JSONWriter 池实例（替代原 {@code FAST_WRITER_POOL.get()}）。
     *
     * @return 当前线程的 JSONWriter
     * @since 1.0.0
     */
    public static JSONWriter getFastWriterPool() {
        return SerializationContext.CONTEXT.get().fastWriterPool;
    }

    /**
     * 获取当前线程的视图类（替代原 {@code CURRENT_VIEW_CLASS.get()}）。
     *
     * @return 当前线程的视图类，null 表示无视图过滤
     * @since 1.0.0
     */
    public static Class<?> getCurrentViewClass() {
        return SerializationContext.CONTEXT.get().currentViewClass;
    }

    /**
     * 设置当前线程的视图类（替代原 {@code CURRENT_VIEW_CLASS.set(viewClass)}）。
     *
     * @param viewClass 视图类，null 表示清除视图过滤
     * @since 1.0.0
     */
    public static void setCurrentViewClass(Class<?> viewClass) {
        SerializationContext.CONTEXT.get().currentViewClass = viewClass;
    }

    /**
     * 获取当前线程的循环引用检测集合（替代原 {@code SERIALIZING_OBJECTS.get()}）。
     *
     * @return 当前线程的已序列化对象集合
     * @since 1.0.0
     */
    public static Set<Object> getSerializingObjects() {
        return SerializationContext.CONTEXT.get().serializingObjects;
    }

    /**
     * 获取当前线程的 StringBuilder 池实例（替代原 {@code SB_POOL.get()}）。
     *
     * @return 当前线程的 StringBuilder
     * @since 1.0.0
     */
    public static StringBuilder getSbPool() {
        return SerializationContext.CONTEXT.get().sbPool;
    }

    /**
     * 设置当前线程的 StringBuilder 池实例（替代原 {@code SB_POOL.set(sb)}）。
     *
     * @param sb 新的 StringBuilder 实例
     * @since 1.0.0
     */
    public static void setSbPool(StringBuilder sb) {
        SerializationContext.CONTEXT.get().sbPool = sb;
    }

    /**
     * 获取 ASM 降级总次数。
     *
     * @return ASM 序列化降级总次数
     * @since 1.0.0
     */
    public static long getAsmDowngradeCount() {
        return ASM_DOWNGRADE_COUNT.get();
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
        SerializationContext ctx = SerializationContext.CONTEXT.get();
        StringBuilder sb = ctx.sbPool;

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
            ctx.sbPool = sb;
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

        // @JsonSerialize 快速路径：如果类有 @JsonSerialize 注解，使用自定义序列化器
        JsonSerializer<Object> customSerializer = getCustomSerializer(obj.getClass());
        if (customSerializer != null) {
            return customSerializer.serialize(obj);
        }

        // @JsonValue 快速路径：如果类有 @JsonValue 标注的方法，直接序列化方法返回值
        Method jsonValueMethod = FieldMetadataLoader.findJsonValueMethod(obj.getClass());
        if (jsonValueMethod != null) {
            return recordJsonValueSerialization(obj, jsonValueMethod);
        }

        SerializationContext ctx = SerializationContext.CONTEXT.get();

        // 统一快速路径：Bean/Collection/Map 三条路径提取到 tryFastPathToWriter
        JSONWriter writer = tryFastPathToWriter(obj, ctx);
        if (writer != null) {
            return writer.toString();
        }

        // 回退路径：使用 StringBuilder + ValueWriter
        StringBuilder sb = getSizedStringBuilder(256);

        Set<Object> objects = ctx.serializingObjects;
        objects.clear();

        try {
            if (!tryBeanSerialize(obj, sb)) {
                ValueWriter.writeValue(obj, sb);
            }
        } catch (Exception e) {
            throw wrapSerializationException(obj, e);
        } finally {
            objects.clear();
        }

        return sb.toString();
    }

    /**
     * 序列化对象（带特性配置）
     *
     * <p>当 PrettyPrint 特性未启用时，复用 {@link #serialize(Object)} 的 ASM 快速路径；
     * 启用 PrettyPrint 时走格式化路径。</p>
     *
     * @param obj 对象
     * @param features 特性标志（位运算值）
     * @return JSON 字符串
     */
    public static String serialize(Object obj, long features) {
        if (obj == null) {
            return "null";
        }

        // 非 PrettyPrint 场景复用标准序列化路径（含 ASM 快速路径）
        if (!JSONWriter.Feature.PrettyPrint.isEnabled(features)) {
            return serialize(obj);
        }

        // PrettyPrint 格式化路径
        StringBuilder sb = getSizedStringBuilder(LARGE_SB_CAPACITY);

        Set<Object> objects = SerializationContext.CONTEXT.get().serializingObjects;
        objects.clear();

        try {
            ValueFormatter.formatValue(obj, sb, 0);
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

        Set<Object> objects = SerializationContext.CONTEXT.get().serializingObjects;
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

        SerializationContext ctx = SerializationContext.CONTEXT.get();
        Set<Object> objects = ctx.serializingObjects;
        objects.clear();

        Class<?> previousView = ctx.currentViewClass;
        ctx.currentViewClass = viewClass;
        try {
            ValueWriter.writeValue(obj, sb);
        } catch (Exception e) {
            throw wrapSerializationException(obj, e);
        } finally {
            ctx.currentViewClass = previousView;
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

        SerializationContext ctx = SerializationContext.CONTEXT.get();
        Set<Object> objects = ctx.serializingObjects;
        objects.clear();

        Class<?> previousView = ctx.currentViewClass;
        ctx.currentViewClass = viewClass;
        try {
            if (pretty) {
                ValueFormatter.formatValue(obj, sb, 0);
            } else {
                ValueWriter.writeValue(obj, sb);
            }
        } catch (JsonSerializationException e) {
            throw e;
        } catch (Exception e) {
            throw wrapSerializationException(obj, e);
        } finally {
            ctx.currentViewClass = previousView;
            objects.clear();
        }

        return sb.toString();
    }

    /**
     * 序列化对象为 UTF-8 字节数组（零拷贝优化版）
     *
     * <p>直接使用 {@link JSONWriter#toUtf8Bytes()} 将 char[] 转为 byte[]，
     * 跳过 String 中间层，避免双重分配（String + getBytes）。
     * 对于纯 ASCII 内容直接 1:1 拷贝，非 ASCII 回退到标准 UTF-8 编码。</p>
     *
     * @param obj 要序列化的对象
     * @return UTF-8 编码的字节数组
     * @since 1.0.0
     */
    public static byte[] serializeToBytes(Object obj) {
        if (obj == null) {
            return new byte[]{'n', 'u', 'l', 'l'};
        }

        SerializationContext ctx = SerializationContext.CONTEXT.get();

        // 统一快速路径：Bean/Collection/Map 三条路径提取到 tryFastPathToWriter
        JSONWriter writer = tryFastPathToWriter(obj, ctx);
        if (writer != null) {
            return writer.toUtf8Bytes();
        }

        // 回退路径：使用 StringBuilder + ValueWriter
        StringBuilder sb = getSizedStringBuilder(256);
        Set<Object> objects = ctx.serializingObjects;
        objects.clear();

        try {
            if (!tryBeanSerialize(obj, sb)) {
                ValueWriter.writeValue(obj, sb);
            }
        } catch (Exception e) {
            throw wrapSerializationException(obj, e);
        } finally {
            objects.clear();
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 序列化对象并直接写入 OutputStream（避免中间 byte[] 分配）。
     *
     * <p>先序列化为 JSON 字符串，再通过 OutputStream 直接写入 UTF-8 字节，
     * 对于大数据量场景可减少一次 byte[] 分配和 GC 压力。</p>
     *
     * @param obj 要序列化的对象
     * @param out 输出流
     * @since 1.4.0
     */
    public static void serializeToStream(Object obj, java.io.OutputStream out) {
        if (obj == null) {
            try {
                out.write(new byte[]{'n', 'u', 'l', 'l'});
            } catch (java.io.IOException e) {
                throw new JsonSerializationException(
                    JsonSerializationException.SERIALIZATION_ERROR,
                    "Failed to write null to OutputStream", e
                );
            }
            return;
        }

        // 优先使用零拷贝 byte[] 路径，再写入流
        byte[] bytes = serializeToBytes(obj);
        try {
            out.write(bytes);
        } catch (java.io.IOException e) {
            throw new JsonSerializationException(
                JsonSerializationException.SERIALIZATION_ERROR,
                "Failed to write to OutputStream", e
            );
        }
    }

    /**
     * 序列化对象并直接写入 Writer（避免中间 String 分配）。
     *
     * <p>先序列化为 JSON 字符串，再通过 Writer 写入字符流。
     * 对于需要输出到 FileWriter/PrintWriter 等场景可减少一次 String 分配。</p>
     *
     * @param obj 要序列化的对象
     * @param writer 字符输出流
     * @since 1.4.0
     */
    public static void serializeToWriter(Object obj, java.io.Writer writer) {
        if (obj == null) {
            try {
                writer.write("null");
            } catch (java.io.IOException e) {
                throw new JsonSerializationException(
                    JsonSerializationException.SERIALIZATION_ERROR,
                    "Failed to write null to Writer", e
                );
            }
            return;
        }

        String json = serialize(obj);
        try {
            writer.write(json);
        } catch (java.io.IOException e) {
            throw new JsonSerializationException(
                JsonSerializationException.SERIALIZATION_ERROR,
                "Failed to write to Writer", e
            );
        }
    }

    /**
     * 统一快速路径：尝试将对象序列化到 JSONWriter（Bean/Collection/Map 三条路径）。
     *
     * <p>提取自 {@link #serialize} 和 {@link #serializeToBytes} 的公共快速路径逻辑，
     * 消除约 80 行重复代码。调用方根据返回值决定使用 {@code writer.toString()} 还是
     * {@code writer.toUtf8Bytes()} 获取结果。</p>
     *
     * @param obj 要序列化的对象（非 null）
     * @param ctx 当前线程的序列化上下文
     * @return 已写入内容的 JSONWriter，或 null 表示需要回退到 StringBuilder 路径
     */
    private static JSONWriter tryFastPathToWriter(Object obj, SerializationContext ctx) {
        Class<?> clazz = obj.getClass();

        // 快速路径 1：Bean 类型直接使用 ASM 序列化器
        if (!(obj instanceof Collection) && !(obj instanceof Map) && !clazz.isArray()) {
            try {
                JSONWriter writer = ctx.fastWriterPool;
                writer.reset();
                if (AsmCodecCache.trySerialize(obj, writer)) {
                    return writer;
                }
            } catch (Exception e) {
                logAsmDowngrade(clazz, "serialization", e);
            }
            return null; // Bean 类型 ASM 失败，需回退到 StringBuilder 路径
        }

        // 快速路径 2：Collection 类型直接使用 JSONWriter
        if (obj instanceof Collection) {
            JSONWriter writer = ctx.fastWriterPool;
            writer.reset();
            Collection<?> coll = (Collection<?>) obj;
            if (!coll.isEmpty()) {
                writer.preAllocate(coll.size() * 64);
                AsmSerializer<Object> serializer = ctx.cachedListSerializer;
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
                                ctx.cachedListSerializer = serializer;
                                ctx.cachedListElementClass = first.getClass();
                            }
                        } catch (Exception e) {
                            // ASM 序列化器创建失败，回退到通用 Collection 写入
                        }
                    }
                }
                if (serializer != null) {
                    writer.writeCollectionWithSerializer(coll, serializer);
                    return writer;
                }
            }
            writer.writeCollection(coll);
            return writer;
        }

        // 快速路径 3：Map 类型直接使用 JSONWriter
        if (obj instanceof Map) {
            JSONWriter writer = ctx.fastWriterPool;
            writer.reset();
            writer.writeMap((Map<?, ?>) obj);
            return writer;
        }

        return null; // 数组等其他类型，回退到 StringBuilder 路径
    }

    /**
     * Bean 序列化快速路径（BeanSerializer 路径，不重试 ASM）。
     *
     * <p>原 {@code tryFastSerialize} 方法中包含冗余的 ASM 调用——ASM 已在
     * {@link #tryFastPathToWriter} 中尝试过，此处不再重复调用。
     * 仅保留 BeanSerializer 路径作为 ASM 降级后的快速序列化方案。</p>
     *
     * @param obj 要序列化的对象
     * @param sb StringBuilder 缓冲区
     * @return true 如果使用了 BeanSerializer 快速路径
     */
    private static boolean tryBeanSerialize(Object obj, StringBuilder sb) {
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
        YdszJsonClass classAnnotation = clazz.getAnnotation(YdszJsonClass.class);
        if (classAnnotation != null) {
            return false;
        }

        // 检查是否有视图过滤
        if (SerializationContext.CONTEXT.get().currentViewClass != null) {
            return false;
        }

        // 获取或创建 BeanSerializer
        FieldMeta[] fields = SerializerCache.getFieldMeta(clazz);
        if (fields == null) {
            fields = FieldMetadataLoader.loadFields(clazz);
            SerializerCache.putFieldMeta(clazz, fields);
        }

        // 检查是否有字段注解（使用缓存的 BeanSerializerInfo）
        BeanSerializerInfo serializerInfo = getOrCreateBeanSerializer(clazz, fields);
        if (serializerInfo.hasAnnotations) {
            return false;
        }

        // 使用 JSONWriter 进行快速序列化（复用 ThreadLocal 池）
        JSONWriter writer = SerializationContext.CONTEXT.get().fastWriterPool;
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

        /** 是否有字段注解（缓存 FieldMetadataLoader.hasFieldAnnotations 结果） */
        final boolean hasAnnotations;

        BeanSerializerInfo(FieldMeta[] validFields, int estimatedSize, boolean hasAnnotations) {
            this.validFields = validFields;
            this.estimatedSize = estimatedSize;
            this.hasAnnotations = hasAnnotations;
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

            info = new BeanSerializerInfo(validFields, estimatedSize, FieldMetadataLoader.hasFieldAnnotations(fields));
            BEAN_SERIALIZER_INFO_CACHE.put(clazz, info);
        }
        return info;
    }

    private static AsmSerializer<Object> captureSerializer(AsmSerializer<?> serializer) {
        return (AsmSerializer<Object>) serializer;
    }

    /**
     * @JsonSerialize 自定义序列化器缓存（Class -> JsonSerializer 实例）。
     */
    private static final ConcurrentHashMap<Class<?>, Object> CUSTOM_SERIALIZER_CACHE =
        new ConcurrentHashMap<>();

    /**
     * 检查类是否有 @JsonSerialize 注解并获取自定义序列化器。
     *
     * @param clazz 要检查的类
     * @return 自定义序列化器实例，或 null 如果没有
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static JsonSerializer<Object> getCustomSerializer(Class<?> clazz) {
        JsonSerialize annotation = clazz.getAnnotation(JsonSerialize.class);
        if (annotation == null || annotation.using() == Void.class) {
            return null;
        }
        Object cached = CUSTOM_SERIALIZER_CACHE.get(clazz);
        if (cached != null) {
            return (JsonSerializer<Object>) cached;
        }
        try {
            JsonSerializer<?> instance = (JsonSerializer<?>) annotation.using().getDeclaredConstructor().newInstance();
            CUSTOM_SERIALIZER_CACHE.putIfAbsent(clazz, instance);
            return (JsonSerializer) instance;
        } catch (Exception e) {
            throw new JsonSerializationException(
                JsonSerializationException.SERIALIZATION_ERROR,
                "Failed to instantiate custom serializer: " + annotation.using().getName(),
                e
            );
        }
    }

    /**
     * @JsonValue 序列化路径：调用标注了 @JsonValue 的方法，序列化其返回值。
     *
     * <p>对标 Jackson @JsonValue：方法的返回值作为整个对象的 JSON 值，
     * 跳过字段级序列化。常用于枚举自定义序列化（如返回枚举的 code 值而非 name）。</p>
     *
     * @param obj 要序列化的对象
     * @param jsonValueMethod 标注了 @JsonValue 的方法
     * @return 方法返回值的 JSON 表示
     */
    private static String recordJsonValueSerialization(Object obj, Method jsonValueMethod) {
        try {
            Object value = jsonValueMethod.invoke(obj);
            if (value == null) {
                return "null";
            }
            // 递归序列化返回值（返回值可能是 String/Number/Boolean 等简单类型）
            return serialize(value);
        } catch (Exception e) {
            throw new JsonSerializationException(
                JsonSerializationException.SERIALIZATION_ERROR,
                "@JsonValue method invocation failed for " + obj.getClass().getName()
                    + ": " + e.getMessage(),
                e
            );
        }
    }

    // ==================== 内部辅助方法 ====================

    /**
     * 包装序列化异常（消除 serialize() 和 serializeWithView() 中的重复异常处理代码）。
     *
     * @param obj 正在序列化的对象
     * @param e 原始异常
     * @return 包装后的 JsonSerializationException
     */
    private static JsonSerializationException wrapSerializationException(Object obj, Exception e) {
        if (e instanceof JsonSerializationException jse) {
            return jse;
        }
        return new JsonSerializationException(
            JsonSerializationException.SERIALIZATION_ERROR,
            "Serialization failed for " + obj.getClass().getName() + ": " + e.getMessage(),
            e
        );
    }

    /**
     * ASM 序列化降级日志记录（统一逻辑，消除 3 处重复）。
     *
     * @param clazz  正在序列化的类
     * @param phase  序列化阶段描述（如 "serialization"、"serialization (bytes)"、"fast-serialize"）
     * @param e      异常
     */
    private static void logAsmDowngrade(Class<?> clazz, String phase, Exception e) {
        long count = ASM_DOWNGRADE_COUNT.incrementAndGet();
        if (count <= 10 || count % 100 == 0) {
            LOGGER.debug("ASM " + phase + " failed for " + clazz.getName()
                    + ", falling back to reflection. Total downgrades: " + count
                    + ", error: " + e.getMessage());
        }
    }

    /**
     * ThreadLocal 快照（用于单次配置序列化的线程安全保存/恢复）。
     *
     * <p>使用 {@link SerializationContext} 合并多个 ThreadLocal 为单一实例，
     * 构造时捕获当前线程的 SerializationContext 配置字段快照，
     * 调用 {@link #restore()} 恢复原始值。避免修改全局单例。</p>
     *
     * <p>注意：仅保存/恢复配置类字段（writeNulls、prettyPrint、circularRefStrategy、
     * serializeEnumUsingOrdinal、excludedFields），不保存运行时状态字段
     * （sbPool、fastWriterPool、serializingObjects、currentViewClass、
     * cachedListSerializer、cachedListElementClass），因为运行时状态仅在单次
     * 序列化调用内有意义。</p>
     *
     * @since 1.0.0
     */
    public static final class ThreadLocalSnapshot {
        private final boolean savedWriteNulls;
        private final boolean savedPrettyPrint;
        private final String savedCircularRefStrategy;
        private final boolean savedSerializeEnumUsingOrdinal;
        private final Set<String> savedExcludedFields;
        private final String savedDateFormat;
        private final boolean savedFailOnError;

        /**
         * 捕获当前线程的 ThreadLocal 序列化参数快照。
         */
        public ThreadLocalSnapshot() {
            SerializationContext ctx = SerializationContext.CONTEXT.get();
            this.savedWriteNulls = ctx.writeNulls;
            this.savedPrettyPrint = ctx.prettyPrint;
            this.savedCircularRefStrategy = ctx.circularRefStrategy;
            this.savedSerializeEnumUsingOrdinal = ctx.serializeEnumUsingOrdinal;
            this.savedExcludedFields = ctx.excludedFields;
            this.savedDateFormat = ctx.dateFormat;
            this.savedFailOnError = ctx.failOnError;
        }

        /**
         * 恢复快照中保存的 ThreadLocal 序列化参数。
         */
        public void restore() {
            SerializationContext ctx = SerializationContext.CONTEXT.get();
            ctx.writeNulls = savedWriteNulls;
            ctx.prettyPrint = savedPrettyPrint;
            ctx.circularRefStrategy = savedCircularRefStrategy;
            ctx.serializeEnumUsingOrdinal = savedSerializeEnumUsingOrdinal;
            ctx.excludedFields = savedExcludedFields;
            ctx.dateFormat = savedDateFormat;
            ctx.failOnError = savedFailOnError;
        }
    }
}
