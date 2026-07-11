package com.njydsz.pmis.common.kms;

/**
 * 密钥提供者抽象接口（KMS）
 *
 * <p>定义统一的密钥获取契约，支持多种后端实现：
 * <ul>
 *   <li>EnvironmentSecretProvider：从环境变量/配置文件读取（默认，开发阶段）</li>
 *   <li>JasyptSecretProvider：通过 Jasypt 解密 ENC() 密文（当前默认）</li>
 *   <li>VaultSecretProvider：从 HashiCorp Vault 读取（未来扩展，预留）</li>
 *   <li>AliyunKmsSecretProvider：从阿里云 KMS 读取（未来扩展，预留）</li>
 * </ul>
 *
 * <p>业务代码不应直接依赖具体实现，而是通过 {@link SecretManager} 或本接口注入。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface SecretProvider {

    /**
     * 根据密钥名获取明文密钥
     *
     * @param secretKey 密钥标识（如 "db.password", "redis.password", "jwt.secret"）
     * @return 明文密钥，不存在返回 null
     */
    String getSecret(String secretKey);

    /**
     * 根据密钥名获取明文密钥，不存在则返回默认值
     *
     * @param secretKey    密钥标识
     * @param defaultValue 默认值（密钥不存在时返回）
     * @return 明文密钥，不存在返回 defaultValue
     */
    default String getSecret(String secretKey, String defaultValue) {
        String val = getSecret(secretKey);
        return val != null ? val : defaultValue;
    }
}
