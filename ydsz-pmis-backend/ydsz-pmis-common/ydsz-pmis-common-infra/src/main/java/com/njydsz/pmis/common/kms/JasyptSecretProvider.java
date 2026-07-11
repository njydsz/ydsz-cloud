package com.njydsz.pmis.common.kms;

import lombok.extern.slf4j.Slf4j;
import org.jasypt.encryption.StringEncryptor;
import org.springframework.core.env.Environment;

/**
 * 基于 Jasypt 的密钥提供者（增强实现）
 *
 * <p>在 {@link EnvironmentSecretProvider} 的基础上，增加对 {@code ENC()} 密文的解密能力：
 * <ul>
 *   <li>若获取到的原始值以 {@code ENC(} 开头且以 {@code )} 结尾，使用 Jasypt
 *       {@link StringEncryptor} 解密后返回明文</li>
 *   <li>否则原样返回（兼容明文配置）</li>
 * </ul>
 *
 * <p>复用项目现有的 Jasypt 配置（{@code jasypt-spring-boot-starter} 自动装配的
 * {@link StringEncryptor} Bean），主密码通过 {@code JASYPT_ENCRYPTOR_PASSWORD}
 * 环境变量注入。
 *
 * <p>适用场景：生产环境将敏感配置以 {@code ENC()} 密文形式存入 Nacos，
 * 运行时自动解密，避免明文泄露。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
public class JasyptSecretProvider extends EnvironmentSecretProvider {

    /** ENC 密文前缀 */
    private static final String ENC_PREFIX = "ENC(";

    /** ENC 密文后缀 */
    private static final String ENC_SUFFIX = ")";

    /** Jasypt 字符串加密器，由 starter 自动装配 */
    private final StringEncryptor stringEncryptor;

    /**
     * 构造方法
     *
     * @param kmsProperties   KMS 配置属性
     * @param environment     Spring Environment
     * @param stringEncryptor Jasypt 字符串加密器
     */
    public JasyptSecretProvider(KmsProperties kmsProperties,
                                Environment environment,
                                StringEncryptor stringEncryptor) {
        super(kmsProperties, environment);
        this.stringEncryptor = stringEncryptor;
    }

    /**
     * 根据密钥名获取明文密钥
     *
     * <p>先调用父类获取原始值，若为 {@code ENC(...)} 密文则使用 Jasypt 解密。
     *
     * @param secretKey 密钥标识
     * @return 解密后的明文密钥，不存在返回 null
     */
    @Override
    public String getSecret(String secretKey) {
        String rawValue = super.getSecret(secretKey);
        if (rawValue == null) {
            return null;
        }
        if (isEncrypted(rawValue)) {
            String encrypted = rawValue.substring(ENC_PREFIX.length(), rawValue.length() - ENC_SUFFIX.length());
            try {
                return stringEncryptor.decrypt(encrypted);
            } catch (Exception ex) {
                log.error("[KMS] Jasypt 解密失败, secretKey={}: {}", secretKey, ex.getMessage());
                throw new IllegalStateException("Jasypt 解密失败: " + secretKey, ex);
            }
        }
        return rawValue;
    }

    /**
     * 判断值是否为 ENC() 密文
     *
     * @param value 原始值
     * @return true 表示是 ENC() 密文，需要解密
     */
    private boolean isEncrypted(String value) {
        return value.startsWith(ENC_PREFIX) && value.endsWith(ENC_SUFFIX)
                && value.length() > ENC_PREFIX.length() + ENC_SUFFIX.length();
    }
}
