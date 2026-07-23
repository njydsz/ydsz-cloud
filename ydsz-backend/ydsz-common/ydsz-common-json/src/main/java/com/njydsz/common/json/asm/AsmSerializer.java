package com.njydsz.common.json.asm;

import com.njydsz.common.json.writer.JSONWriter;

/**
 * ASM 生成的序列化器接口
 *
 * <p>为每个 Bean 类生成专用序列化器，消除 MethodHandle 反射开销</p>
 *
 * <p><b>性能优势：</b></p>
 * <ul>
 *   <li>直接字段访问，无反射开销（~1-2ns vs MethodHandle ~10-15ns）</li>
 *   <li>内联优化，JIT 更容易优化</li>
 *   <li>类型特化，避免运行时类型检查</li>
 * </ul>
 *
 * @author YdszJson Team
 */
public interface AsmSerializer<T> {

    /**
     * 序列化对象
     *
     * @param obj 要序列化的对象
     * @param writer JSON 写入器
     */
    void serialize(T obj, JSONWriter writer);

    /**
     * 内联序列化对象（跳过 preAllocate，直接在已有 buf/pos 上操作）
     *
     * <p>用于列表序列化场景，外层已预分配足够容量，
     * 内部每个元素无需重复调用 preAllocate 和读写 buf/pos</p>
     *
     * <p>默认实现委托给 {@link #serialize}，ASM 生成的类会覆盖此方法</p>
     *
     * @param obj 要序列化的对象
     * @param writer JSON 写入器
     */
    default void serializeInline(T obj, JSONWriter writer) {
        serialize(obj, writer);
    }
}
