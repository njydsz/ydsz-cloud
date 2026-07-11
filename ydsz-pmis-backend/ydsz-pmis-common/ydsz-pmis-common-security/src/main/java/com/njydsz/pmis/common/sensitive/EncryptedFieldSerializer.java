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
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class EncryptedFieldSerializer extends JsonSerializer<String> implements ContextualSerializer {

    /** 密钥引用 */
    private String keyRef;

    /** 加密算法 */
    private EncryptedField.Algorithm algorithm;

    /**
     * 默认构造方法（Jackson 反射使用）
     */
    public EncryptedFieldSerializer() {
    }

    /**
     * 构造方法，指定密钥引用与算法
     *
     * @param keyRef     密钥引用，为 null 时使用 "default"
     * @param algorithm  加密算法，为 null 时使用 AES_GCM
     */
    public EncryptedFieldSerializer(String keyRef, EncryptedField.Algorithm algorithm) {
        this.keyRef = keyRef == null ? "default" : keyRef;
        this.algorithm = algorithm == null ? EncryptedField.Algorithm.AES_GCM : algorithm;
    }

    /**
     * 根据字段上的 {@link EncryptedField} 注解创建对应的序列化器
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
        EncryptedField ann = property.getAnnotation(EncryptedField.class);
        if (ann == null) ann = property.getContextAnnotation(EncryptedField.class);
        if (ann == null) {
            return prov.findValueSerializer(property.getType(), property);
        }
        return new EncryptedFieldSerializer(ann.keyRef(), ann.algorithm());
    }

    /**
     * 序列化：将明文字段值按指定算法加密后输出
     *
     * @param value       明文值
     * @param gen         JSON 生成器
     * @param serializers 序列化上下文
     * @throws IOException 写入异常
     */
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

        // P2-14: 审计日志记录
        DataAccessAuditService auditService = DataAccessAuditService.getInstance();
        if (auditService != null) {
            auditService.recordEncryption(keyRef, algorithm.name());
        }

        gen.writeString(cipher);
    }
}
