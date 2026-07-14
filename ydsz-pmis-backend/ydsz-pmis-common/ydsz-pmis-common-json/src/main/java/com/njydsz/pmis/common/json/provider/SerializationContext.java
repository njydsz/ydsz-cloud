package com.njydsz.pmis.common.json.provider;

import com.njydsz.pmis.common.json.naming.PropertyNamingStrategy;

/**
 * 序列化上下文（合并多个 ThreadLocal 为单一实例，减少 ThreadLocal 数量）。
 *
 * <p>将 SerializationProvider 中的多个独立 ThreadLocal（writeNulls、prettyPrint、
 * namingStrategy、circularRefStrategy、serializeEnumUsingOrdinal）合并为
 * 单一 ThreadLocal&lt;SerializationContext&gt;，降低内存开销和 ThreadLocal 泄漏风险。</p>
 *
 * <p>使用方式：</p>
 * <pre><code>
 * SerializationContext ctx = SerializationContext.CONTEXT.get();
 * ctx.writeNulls = true;
 * // ... 序列化操作 ...
 * SerializationContext.CONTEXT.remove(); // 清理
 * </code></pre>
 *
 * @since 1.4.0
 */
public final class SerializationContext {

    /** 单一 ThreadLocal 实例（合并 5+ 个 ThreadLocal） */
    static final ThreadLocal<SerializationContext> CONTEXT =
        ThreadLocal.withInitial(SerializationContext::new);

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

    /**
     * 创建默认上下文实例。
     */
    public SerializationContext() {
        this.namingStrategy = null;
        this.writeNulls = false;
        this.prettyPrint = false;
        this.circularRefStrategy = "REF";
        this.serializeEnumUsingOrdinal = false;
    }

    /**
     * 重置为默认值。
     */
    public void reset() {
        this.namingStrategy = null;
        this.writeNulls = false;
        this.prettyPrint = false;
        this.circularRefStrategy = "REF";
        this.serializeEnumUsingOrdinal = false;
    }

    /**
     * 从当前 SerializationProvider 的 ThreadLocal 值同步到本上下文。
     */
    public void captureFromProvider() {
        this.namingStrategy = SerializationProvider.getNamingStrategy();
        this.writeNulls = SerializationProvider.isWriteNulls();
        this.prettyPrint = SerializationProvider.isPrettyPrint();
        this.circularRefStrategy = SerializationProvider.getCircularReferenceStrategy();
        this.serializeEnumUsingOrdinal = SerializationProvider.isSerializeEnumUsingOrdinal();
    }

    /**
     * 将本上下文的值应用到 SerializationProvider 的 ThreadLocal。
     */
    public void applyToProvider() {
        SerializationProvider.setNamingStrategy(namingStrategy);
        SerializationProvider.setWriteNulls(writeNulls);
        SerializationProvider.setPrettyPrint(prettyPrint);
        SerializationProvider.setCircularReferenceStrategy(circularRefStrategy);
        SerializationProvider.setSerializeEnumUsingOrdinal(serializeEnumUsingOrdinal);
    }

    /**
     * 估算当前线程的 ThreadLocal 内存占用（字节）。
     *
     * <p>包括 SerializationContext 本身 + SerializationProvider 中的
     * StringBuilder 池、JSONWriter 池、IdentityHashMap 等开销。</p>
     *
     * @return 估算内存占用
     * @since 1.4.0
     */
    public static long estimateThreadLocalMemory() {
        long total = 0;
        // SerializationContext 本身约 48 字节（5 个字段 + 对象头）
        total += 48;
        // SerializationProvider 中的 StringBuilder 池
        total += 4096 * 2L; // DEFAULT capacity * 2 bytes/char
        // SerializationProvider 中的 JSONWriter 池
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
