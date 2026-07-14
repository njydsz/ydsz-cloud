package com.njydsz.pmis.common.json.cache;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import com.njydsz.pmis.common.json.naming.PropertyNamingStrategy;
import com.njydsz.pmis.common.json.writer.JSONWriter;

/**
 * YdszJson 线程上下文 — 合并多个 ThreadLocal 为单一实例。
 *
 * <p>原架构在 {@code YdszSerializationProvider} 中使用了 9+ 个 ThreadLocal，
 * 在 200 线程的线程池中约占用 50KB/线程 = ~10MB 内存。
 * 本类将所有 per-thread 状态合并为单一 ThreadLocal，减少内存碎片和 ThreadLocal
 * 哈希查找开销。</p>
 *
 * <p><b>合并的 ThreadLocal：</b></p>
 * <ul>
 *   <li>StringBuilder（原 SB_POOL）</li>
 *   <li>JSONWriter（原 FAST_WRITER_POOL）</li>
 *   <li>Set&lt;Object&gt; 循环引用检测集（原 SERIALIZING_OBJECTS）</li>
 *   <li>PropertyNamingStrategy（原 NAMING_STRATEGY）</li>
 *   <li>Class&lt;?&gt; 视图类（原 CURRENT_VIEW_CLASS）</li>
 *   <li>boolean writeNulls（原 WRITE_NULLS）</li>
 *   <li>boolean prettyPrint（原 PRETTY_PRINT）</li>
 *   <li>String circularReferenceStrategy（原 CIRCULAR_REFERENCE_STRATEGY）</li>
 *   <li>boolean serializeEnumUsingOrdinal（原 SERIALIZE_ENUM_USING_ORDINAL）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
public final class YdszJsonContext {

    /** 默认 StringBuilder 初始容量 */
    private static final int DEFAULT_SB_CAPACITY = 4096;

    /** 默认 JSONWriter 初始容量 */
    private static final int DEFAULT_WRITER_CAPACITY = 4096;

    /** ThreadLocal 唯一实例 */
    private static final ThreadLocal<YdszJsonContext> CONTEXT =
        ThreadLocal.withInitial(YdszJsonContext::new);

    /** 复用的 StringBuilder */
    public final StringBuilder stringBuilder;

    /** 复用的 JSONWriter */
    public final JSONWriter jsonWriter;

    /** 循环引用检测集合（使用 IdentityHashMap 保证引用比较） */
    public final Set<Object> serializingObjects;

    /** 当前命名策略 */
    public PropertyNamingStrategy namingStrategy = PropertyNamingStrategy.LOWER_CAMEL_CASE;

    /** 当前视图类（用于字段过滤） */
    public Class<?> currentViewClass = null;

    /** 是否输出 null 值 */
    public boolean writeNulls = false;

    /** 是否格式化输出 */
    public boolean prettyPrint = false;

    /** 循环引用处理策略：REF / IGNORE / ERROR */
    public String circularReferenceStrategy = "REF";

    /** 枚举是否使用序号序列化 */
    public boolean serializeEnumUsingOrdinal = false;

    private YdszJsonContext() {
        this.stringBuilder = new StringBuilder(DEFAULT_SB_CAPACITY);
        this.jsonWriter = new JSONWriter(DEFAULT_WRITER_CAPACITY);
        this.serializingObjects = Collections.newSetFromMap(new IdentityHashMap<>(64));
    }

    /**
     * 获取当前线程的 YdszJsonContext。
     *
     * @return 线程上下文实例
     */
    public static YdszJsonContext get() {
        return CONTEXT.get();
    }

    /**
     * 清理当前线程的 YdszJsonContext（防止线程池环境内存泄漏）。
     */
    public static void clear() {
        CONTEXT.remove();
    }

    /**
     * 估算当前线程的 ThreadLocal 内存占用（字节）。
     *
     * @return 估算内存占用
     */
    public static long estimateThreadLocalMemory() {
        YdszJsonContext ctx = get();
        long total = 0;
        if (ctx.stringBuilder != null) {
            total += ctx.stringBuilder.capacity() * 2L; // char = 2 bytes
        }
        if (ctx.jsonWriter != null && ctx.jsonWriter.buf != null) {
            total += ctx.jsonWriter.buf.length * 2L;
        }
        total += 256; // IdentityHashMap + 其他字段估算
        return total;
    }
}
