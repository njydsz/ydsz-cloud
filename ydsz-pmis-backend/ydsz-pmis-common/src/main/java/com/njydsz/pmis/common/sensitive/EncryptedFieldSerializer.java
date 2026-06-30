package com.njydsz.pmis.common.sensitive;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import com.njydsz.pmis.common.util.CryptoUtil;

import java.io.IOException;

/**
 * 加密字段序列化器
 *
 * <p>输出密文 base64 字符串; 解密由调用方按需通过 {@link com.njydsz.pmis.common.util.CryptoUtil} 完成。
 */
public class EncryptedFieldSerializer extends JsonSerializer<String> implements ContextualSerializer {

    private String keyRef;
    private EncryptedField.Algorithm algorithm;

    public EncryptedFieldSerializer() {
    }

    public EncryptedFieldSerializer(String keyRef, EncryptedField.Algorithm algorithm) {
        this.keyRef = keyRef == null ? "default" : keyRef;
        this.algorithm = algorithm == null ? EncryptedField.Algorithm.AES_GCM : algorithm;
    }

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider prov, BeanProperty property)
            throws JsonMappingException {
        if (property == null) return this;
        EncryptedField ann = property.getAnnotation(EncryptedField.class);
        if (ann == null) ann = property.getContextAnnotation(EncryptedField.class);
        if (ann == null) {
            return prov.findValueSerializer(property.getType(), property);
        }
        return new EncryptedFieldSerializer(ann.keyRef(), ann.algorithm());
    }

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        String cipher;
        if (algorithm == EncryptedField.Algorithm.SM4_GCM) {
            cipher = CryptoUtil.sm4GcmEncrypt(value, EncryptedFieldKeyRegistry.get(keyRef));
        } else {
            cipher = CryptoUtil.aesGcmEncrypt(value, EncryptedFieldKeyRegistry.get(keyRef));
        }
        gen.writeString(cipher);
    }
}
