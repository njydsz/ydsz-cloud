package com.njydsz.pmis.common.sensitive;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段级加密注解
 *
 * <p>序列化时使用 {@link EncryptedFieldSerializer} 将字段值以 AES-256-GCM 加密后输出。
 * 反序列化时通过同注解的 Deserializer 还原明文 (按需在 VO 上声明)。
 *
 * <p>使用方式:
 * <pre>
 *   {@code @EncryptedField(keyRef = "pmis.crypto.aes-key")}
 *   private String idCard;
 * </pre>
 *
 * <p>密钥来源 (按优先级):
 * <ol>
 *   <li>keyRef 通过 {@link com.njydsz.pmis.common.sensitive.EncryptedFieldKeyRegistry} 解析</li>
 *   <li>若未注册, 使用全局默认 32 字节密钥 (仅本地开发)</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@JacksonAnnotationsInside
@JsonSerialize(using = EncryptedFieldSerializer.class)
public @interface EncryptedField {

    /**
     * 密钥引用 key, 通过 {@link EncryptedFieldKeyRegistry} 解析
     */
    String keyRef() default "default";

    /**
     * 加密算法
     */
    Algorithm algorithm() default Algorithm.AES_GCM;

    enum Algorithm {
        /** AES-256-GCM 算法 */
        AES_GCM,
        /** 国密 SM4-GCM 算法 */
        SM4_GCM
    }
}
