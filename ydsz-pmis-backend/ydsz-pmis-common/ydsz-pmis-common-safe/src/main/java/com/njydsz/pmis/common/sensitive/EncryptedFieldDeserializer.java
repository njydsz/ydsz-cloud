package com.njydsz.pmis.common.sensitive;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.njydsz.pmis.common.util.CryptoUtil;

import java.io.IOException;

/**
 * P2-14: 加密字段反序列化器
 *
 * <p>与 {@link EncryptedFieldSerializer} 配对使用，在 Jackson 反序列化时自动解密字段值。
 * 当字段标注了 {@link EncryptedField @EncryptedField} 且使用
 * {@code @JsonDeserialize(using = EncryptedFieldDeserializer.class)} 时，
 * 反序列化会自动将密文还原为明文。
 *
 * <p>使用方式：
 * <pre>
 *   {@code @EncryptedField(keyRef = "pmis.crypto.aes-key")}
 *   {@code @JsonDeserialize(using = EncryptedFieldDeserializer.class)}
 *   private String idCard;
 * </pre>
 *
 * <p>安全说明：
 * <ul>
 *   <li>解密失败时抛出 {@link SecurityException}，不返回明文</li>
 *   <li>解密操作会被 {@link DataAccessAuditService} 记录审计日志（如已配置）</li>
 *   <li>密钥通过 {@link EncryptedFieldKeyRegistry} 解析</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 2.1.0
 */
public class EncryptedFieldDeserializer extends JsonDeserializer<String> implements ContextualDeserializer {

    /** 密钥引用 */
    private String keyRef;

    /** 加密算法 */
    private EncryptedField.Algorithm algorithm;

    /**
     * 默认构造方法（Jackson 反射使用）
     */
    public EncryptedFieldDeserializer() {
    }

    /**
     * 构造方法，指定密钥引用与算法
     *
     * @param keyRef    密钥引用，为 null 时使用 "default"
     * @param algorithm 加密算法，为 null 时使用 AES_GCM
     */
    public EncryptedFieldDeserializer(String keyRef, EncryptedField.Algorithm algorithm) {
        this.keyRef = keyRef == null ? "default" : keyRef;
        this.algorithm = algorithm == null ? EncryptedField.Algorithm.AES_GCM : algorithm;
    }

    /**
     * 根据字段上的 {@link EncryptedField} 注解创建对应的反序列化器
     *
     * @param ctxt     反序列化上下文
     * @param property 字段属性
     * @return 反序列化器
     * @throws JsonMappingException 反序列化映射异常
     */
    @Override
    public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property)
            throws JsonMappingException {
        if (property == null) return this;
        EncryptedField ann = property.getAnnotation(EncryptedField.class);
        if (ann == null) ann = property.getContextAnnotation(EncryptedField.class);
        if (ann == null) {
            return ctxt.findContextualValueDeserializer(property.getType(), property);
        }
        return new EncryptedFieldDeserializer(ann.keyRef(), ann.algorithm());
    }

    /**
     * 反序列化：将密文字段值按指定算法解密后返回明文
     *
     * @param p    JSON 解析器
     * @param ctxt 反序列化上下文
     * @return 解密后的明文
     * @throws IOException 读取异常
     */
    @Override
    public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String cipher = p.getValueAsString();
        if (cipher == null || cipher.isEmpty()) {
            return cipher;
        }

        try {
            String plaintext;
            if (algorithm == EncryptedField.Algorithm.SM4_GCM) {
                plaintext = CryptoUtil.sm4GcmDecrypt(cipher, EncryptedFieldKeyRegistry.get(keyRef));
            } else {
                plaintext = CryptoUtil.aesGcmDecrypt(cipher, EncryptedFieldKeyRegistry.get(keyRef));
            }

            // P2-14: 审计日志记录
            DataAccessAuditService auditService = DataAccessAuditService.getInstance();
            if (auditService != null) {
                auditService.recordDecryption(keyRef, algorithm.name());
            }

            return plaintext;
        } catch (Exception e) {
            throw new SecurityException("字段解密失败: " + e.getMessage(), e);
        }
    }
}
