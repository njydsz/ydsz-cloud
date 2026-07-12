package com.njydsz.pmis.common.sensitive;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;

import java.io.IOException;

/**
 * 脱敏序列化器
 *
 * <p>读取字段上 {@code @Sensitive} 注解，按策略脱敏后输出。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class SensitiveSerializer extends JsonSerializer<String> implements ContextualSerializer {

    /** 脱敏策略 */
    private SensitiveStrategy strategy;
    /** 前置保留长度 */
    private int prefixKeep;
    /** 后置保留长度 */
    private int suffixKeep;

    /**
     * 默认构造方法（Jackson 反射使用）
     */
    public SensitiveSerializer() {
    }

    /**
     * 构造方法，指定脱敏策略与前后保留长度
     *
     * @param strategy   脱敏策略，为 null 时使用 NONE
     * @param prefixKeep 前置保留长度
     * @param suffixKeep 后置保留长度
     */
    public SensitiveSerializer(SensitiveStrategy strategy, int prefixKeep, int suffixKeep) {
        this.strategy = strategy == null ? SensitiveStrategy.NONE : strategy;
        this.prefixKeep = prefixKeep;
        this.suffixKeep = suffixKeep;
    }

    /**
     * 根据字段上的 {@link Sensitive} 注解创建对应的序列化器
     *
     * @param prov     序列化上下文
     * @param property 字段属性
     * @return 序列化器
     * @throws JsonMappingException 序列化映射异常
     */
    @Override
    public JsonSerializer<?> createContextual(SerializerProvider prov, BeanProperty property)
            throws JsonMappingException {
        if (property == null) return this;
        Sensitive ann = property.getAnnotation(Sensitive.class);
        if (ann == null) {
            ann = property.getContextAnnotation(Sensitive.class);
        }
        if (ann == null) {
            return prov.findValueSerializer(property.getType(), property);
        }
        return new SensitiveSerializer(ann.value(), ann.prefixKeep(), ann.suffixKeep());
    }

    /**
     * 序列化：将原值按策略脱敏后输出
     *
     * @param value       原始值
     * @param gen         JSON 生成器
     * @param serializers 序列化上下文
     * @throws IOException 写入异常
     */
    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
        String masked = SensitiveUtil.desensitize(value, strategy, prefixKeep, suffixKeep);
        gen.writeString(masked == null ? "" : masked);
    }
}
