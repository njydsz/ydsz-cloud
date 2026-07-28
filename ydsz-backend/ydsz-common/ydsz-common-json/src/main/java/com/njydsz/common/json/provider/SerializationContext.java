package com.njydsz.common.json.provider;

import java.util.Collections;
import java.util.Set;

import com.njydsz.common.json.asm.AsmSerializer;
import com.njdsz.common.json.naming.PropertyNamingStrategy;
import com.njydsz.common.json.writer.JSONWriter;

/**
 * 序列化上下文（合并多个 ThreadLocal 为单一实例，减少 ThreadLocal 数量）。
 *
 * <p>将 SerializationProvider 中的 11 个独立 ThreadLocal 合并为单一
 * ThreadLocal&lt;SerializationContext&gt;，降低内存开销和 ThreadLocal 泄漏风险。</p>
 *
 * <p><b>合并的字段：</b></p>
 * <ul>
 *   <li>配置类（5个）：namingStrategy、writeNulls、prettyPrint、circularRefStrategy、serializeEnumUsingOrdinal</li>
 *   <li>运行时状态（6个）：sbPool、fastWriterPool、serializingObjects、currentViewClass、
 *       cachedListSerializer、cachedListElementClass、excludedFields</li>
 * </ul>
 *
 * <p><b>使用方式：</b></p>
 * <pre><code>
 * SerializationContext ctx = SerializationContext.CONTEXT.get();
 * ctx.writeNulls = true;
 * // ... 序列化操作 ...
 * SerializationContext.CONTEXT.remove(); // 清理
 * </code></pre>
 *
 * @since 1.0.0
 * @author ydsz-team
 */
public final class SerializationContext {

    /** 单一 ThreadLocal 实例（合并 11 个 ThreadLocal） */
    static final ThreadLocal<SerializationContext> CONTEXT =
        ThreadLocal.withInitial(SerializationContext::new);

    // ==================== 配置类字段（5个） ====================

    /** 当前命名策略 */
    public PropertyNamingStrategy namingStrategy;

    /** 是否输出 null 值 */
    public boolean writeNulls;

    /** 是否格式化输出 */
    public boolean prettyPrint;

    /** 循环引用处理策略名称：REF / IGNORE / ERROR */
    public String circularRefStrategy;

    /** 枚举是否使用序号序列化 */
    public boolean serializeEnumUsingOrdinal;

    // ==================== 运行时状态字段（6个） ====================

    /** StringBuilder 池（ThreadLocal 复用，大小分级策略） */
    public StringBuilder sbPool;

    /** JSONWriter 池（ThreadLocal 复用） */
    public JSONWriter fastWriterPool;

    /** 循环引用检测 - 已序列化对象集合（使用 IdentityHashMap 保证引用比较） */
    public Set<Object> serializingObjects;

    /** 当前视图类（用于字段过滤，ThreadLocal 传递上下文） */
    public Class<?> currentViewClass;

    /** 最近使用的列表元素序列化器缓存（避免每次列表序列化都查找 ConcurrentHashMap） */
    public AsmSerializer<Object> cachedListSerializer;

    /** 最近使用的列表元素类型缓存（配合 cachedListSerializer 使用） */
    public Class<?> cachedListElementClass;

    /** 需要排除的字段名集合（用于列权限等场景的字段级过滤） */
    public Set<String> excludedFields;

    /**
     * 创建默认上下文实例。
     */
    public SerializationContext() {
        reset();
    }

    /**
     * 重置为默认值（包括运行时状态）。
     */
    public void reset() {
        // 配置类字段
        this.namingStrategy = null;
        this.writeNulls = false;
        this.prettyPrint = false;
        this.circularRefStrategy = "REF";
        this.serializeEnumUsingOrdinal = false;
        // 运行时状态字段
        this.sbPool = new StringBuilder(4096);
        this.fastWriterPool = new JSONWriter(4096);
        this.serializingObjects = Collections.newSetFromMap(new java.util.IdentityHashMap<>(64));
        this.currentViewClass = null;
        this.cachedListSerializer = null;
        this.cachedListElementClass = null;
        this.excludedFields = null;
    }

    /**
     * 仅清理运行时状态字段（保留配置），用于序列化完成后的状态清理。
     *
     * <p>清理以下字段：serializingObjects（循环引用检测集）、currentViewClass、
     * cachedListSerializer、cachedListElementClass、excludedFields。
     * sbPool 和 fastWriterPool 会重置到初始容量以便复用。</p>
     */
    public void resetRuntimeState() {
        // 清理循环引用检测集（关键：防止 IdentityHashMap 内存泄漏）
        this.serializingObjects.clear();
        // 重置视图类
        this.currentViewClass = null;
        // 清理列表序列化器缓存
        this.cachedListSerializer = null;
        this.cachedListElementClass = null;
        // 清理排除字段集合
        this.excludedFields = null;
        // 重置 StringBuilder 容量（如果过大则缩容）
        if (this.sbPool.length() > 65536) {
            this.sbPool = new StringBuilder(4096);
        } else {
            this.sbPool.setLength(0);
        }
        // 重置 JSONWriter
        this.fastWriterPool.reset();
    }

    /**
     * 从当前 SerializationProvider 的 ThreadLocal 值同步到本上下文。
     *
     * @deprecated 迁移到 SerializationContext 后，captureFromProvider 已无实际意义，
     *             仅为兼容性保留。后续版本将删除。
     */
    @Deprecated(since = "1.0.0")
    public void captureFromProvider() {
        // 迁移完成后，SerializationProvider 已经委托给 SerializationContext
        // 此方法不再有实际作用，仅为兼容性保留空实现
    }

    /**
     * 将本上下文的值应用到 SerializationProvider 的 ThreadLocal。
     *
     * @deprecated 迁移到 SerializationContext 后，applyToProvider 已无实际意义，
     *             仅为兼容性保留。后续版本将删除。
     */
    @Deprecated(since = "1.0.0")
    public void applyToProvider() {
        // 迁移完成后，SerializationProvider 已经委托给 SerializationContext
        // 此方法不再有实际作用，仅为兼容性保留空实现
    }

    /**
     * 估算当前线程的 ThreadLocal 内存占用（字节）。
     *
     * <p>合并后只需估算 SerializationContext 单实例 + 内部缓冲区的开销。</p>
     *
     * @return 估算内存占用
     * @author ydsz-team
     * @since 1.0.0
     */
    public static long estimateThreadLocalMemory() {
        long total = 0;
        // SerializationContext 本身约 80 字节（11 个字段 + 对象头）
        total += 80;
        // StringBuilder 池
        total += 4096 * 2L; // DEFAULT capacity * 2 bytes/char
        // JSONWriter 池
        total += 4096 * 2L;
        // IdentityHashMap（循环引用检测）
        total += 256;
        return total;
    }

    /**
     * 清理当前线程的 SerializationContext（防止线程池环境内存泄漏）。
     */
    public static void clear() {
        CONTEXT.remove();
    }
}
