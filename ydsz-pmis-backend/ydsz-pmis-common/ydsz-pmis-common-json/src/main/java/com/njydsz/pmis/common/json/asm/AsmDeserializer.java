package com.njydsz.pmis.common.json.asm;

import com.njydsz.pmis.common.json.reader.JSONReader;

/**
 * ASM 生成的反序列化器接口
 * 
 * <p>为每个 Bean 类生成专用反序列化器，消除 MethodHandle 反射开销</p>
 * 
 * <p><b>性能优势：</b></p>
 * <ul>
 *   <li>直接 setter 调用，无反射开销（~1-2ns vs MethodHandle ~10-15ns）</li>
 *   <li>内联优化，JIT 更容易优化</li>
 *   <li>类型特化，避免运行时类型检查</li>
 * </ul>
 * 
 * @author Json Team
 */
public interface AsmDeserializer<T> {
    
    /**
     * 反序列化对象
     * 
     * @param reader JSON 读取器
     * @return 反序列化后的对象
     */
    T deserialize(JSONReader reader);
}
