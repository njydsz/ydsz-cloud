package com.njydsz.pmis.common.json.cache;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import com.njydsz.pmis.common.json.asm.AsmSerializer;
import com.njydsz.pmis.common.json.naming.PropertyNamingStrategy;
import com.njydsz.pmis.common.json.writer.JSONWriter;

/**
 * Json 绾跨▼涓婁笅鏂?鈥?鍚堝苟澶氫釜 ThreadLocal 涓哄崟涓€瀹炰緥銆?
 *
 * <p>鍘熸灦鏋勫湪 {@code YdszSerializationProvider} 涓娇鐢ㄤ簡 9+ 涓?ThreadLocal锛?
 * 鍦?200 绾跨▼鐨勭嚎绋嬫睜涓害鍗犵敤 50KB/绾跨▼ = ~10MB 鍐呭瓨銆?
 * 鏈被灏嗘墍鏈?per-thread 鐘舵€佸悎骞朵负鍗曚竴 ThreadLocal锛屽噺灏戝唴瀛樼鐗囧拰 ThreadLocal
 * 鍝堝笇鏌ユ壘寮€閿€銆?/p>
 *
 * <p><b>鍚堝苟鐨?ThreadLocal锛?/b></p>
 * <ul>
 *   <li>StringBuilder锛堝師 SB_POOL锛?/li>
 *   <li>JSONWriter锛堝師 FAST_WRITER_POOL锛?/li>
 *   <li>Set&lt;Object&gt; 寰幆寮曠敤妫€娴嬮泦锛堝師 SERIALIZING_OBJECTS锛?/li>
 *   <li>PropertyNamingStrategy锛堝師 NAMING_STRATEGY锛?/li>
 *   <li>Class&lt;?&gt; 瑙嗗浘绫伙紙鍘?CURRENT_VIEW_CLASS锛?/li>
 *   <li>boolean writeNulls锛堝師 WRITE_NULLS锛?/li>
 *   <li>boolean prettyPrint锛堝師 PRETTY_PRINT锛?/li>
 *   <li>String circularReferenceStrategy锛堝師 CIRCULAR_REFERENCE_STRATEGY锛?/li>
 *   <li>boolean serializeEnumUsingOrdinal锛堝師 SERIALIZE_ENUM_USING_ORDINAL锛?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
public final class JsonContext {

    /** 榛樿 StringBuilder 鍒濆瀹归噺 */
    private static final int DEFAULT_SB_CAPACITY = 4096;

    /** 榛樿 JSONWriter 鍒濆瀹归噺 */
    private static final int DEFAULT_WRITER_CAPACITY = 4096;

    /** ThreadLocal 鍞竴瀹炰緥 */
    private static final ThreadLocal<JsonContext> CONTEXT =
        ThreadLocal.withInitial(JsonContext::new);

    /** 澶嶇敤鐨?StringBuilder */
    public final StringBuilder stringBuilder;

    /** 澶嶇敤鐨?JSONWriter */
    public final JSONWriter jsonWriter;

    /** 寰幆寮曠敤妫€娴嬮泦鍚堬紙浣跨敤 IdentityHashMap 淇濊瘉寮曠敤姣旇緝锛?*/
    public final Set<Object> serializingObjects;

    /** 褰撳墠鍛藉悕绛栫暐 */
    public PropertyNamingStrategy namingStrategy = PropertyNamingStrategy.LOWER_CAMEL_CASE;

    /** 褰撳墠瑙嗗浘绫伙紙鐢ㄤ簬瀛楁杩囨护锛?*/
    public Class<?> currentViewClass = null;

    /** 鏄惁杈撳嚭 null 鍊?*/
    public boolean writeNulls = false;

    /** 鏄惁鏍煎紡鍖栬緭鍑?*/
    public boolean prettyPrint = false;

    /** 寰幆寮曠敤澶勭悊绛栫暐锛歊EF / IGNORE / ERROR */
    public String circularReferenceStrategy = "REF";

    /** 鏋氫妇鏄惁浣跨敤搴忓彿搴忓垪鍖?*/
    public boolean serializeEnumUsingOrdinal = false;

    /** 列表元素序列化器缓存（避免每次列表序列化都查找 ConcurrentHashMap） */
    public AsmSerializer<Object> cachedListSerializer = null;

    /** 列表元素类型缓存（配合 cachedListSerializer 使用） */
    public Class<?> cachedListElementClass = null;

    private JsonContext() {
        this.stringBuilder = new StringBuilder(DEFAULT_SB_CAPACITY);
        this.jsonWriter = new JSONWriter(DEFAULT_WRITER_CAPACITY);
        this.serializingObjects = Collections.newSetFromMap(new IdentityHashMap<>(64));
    }

    /**
     * 鑾峰彇褰撳墠绾跨▼鐨?JsonContext銆?
     *
     * @return 绾跨▼涓婁笅鏂囧疄渚?
     */
    public static JsonContext get() {
        return CONTEXT.get();
    }

    /**
     * 娓呯悊褰撳墠绾跨▼鐨?JsonContext锛堥槻姝㈢嚎绋嬫睜鐜鍐呭瓨娉勬紡锛夈€?
     */
    public static void clear() {
        CONTEXT.remove();
    }

    /**
     * 浼扮畻褰撳墠绾跨▼鐨?ThreadLocal 鍐呭瓨鍗犵敤锛堝瓧鑺傦級銆?
     *
     * @return 浼扮畻鍐呭瓨鍗犵敤
     */
    public static long estimateThreadLocalMemory() {
        JsonContext ctx = get();
        long total = 0;
        if (ctx.stringBuilder != null) {
            total += ctx.stringBuilder.capacity() * 2L; // char = 2 bytes
        }
        if (ctx.jsonWriter != null && ctx.jsonWriter.buf != null) {
            total += ctx.jsonWriter.buf.length * 2L;
        }
        total += 256; // IdentityHashMap + 鍏朵粬瀛楁浼扮畻
        return total;
    }
}
