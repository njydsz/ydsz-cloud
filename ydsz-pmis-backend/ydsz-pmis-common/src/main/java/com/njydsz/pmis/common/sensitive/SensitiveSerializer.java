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

    private SensitiveStrategy strategy;
    private int prefixKeep;
    private int suffixKeep;

    public SensitiveSerializer() {
    }

    public SensitiveSerializer(SensitiveStrategy strategy, int prefixKeep, int suffixKeep) {
        this.strategy = strategy == null ? SensitiveStrategy.NONE : strategy;
        this.prefixKeep = prefixKeep;
        this.suffixKeep = suffixKeep;
    }

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

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
        String masked = SensitiveUtil.desensitize(value, strategy, prefixKeep, suffixKeep);
        gen.writeString(masked == null ? "" : masked);
    }
}
