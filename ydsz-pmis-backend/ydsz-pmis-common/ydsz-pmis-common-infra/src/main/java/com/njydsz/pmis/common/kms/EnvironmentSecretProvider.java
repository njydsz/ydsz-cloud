package com.njydsz.pmis.common.kms;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;

import java.util.Map;

/**
 * 基于环境变量/Nacos 配置的密钥提供者（默认实现）
 *
 * <p>密钥获取优先级：
 * <ol>
 *   <li>环境变量：将密钥标识转换为环境变量名读取
 *       （转换规则：{@code db.password} → {@code PMIS_SECRETS_DB_PASSWORD}）</li>
 *   <li>Nacos 配置：从 {@code pmis.kms.secrets.*} 配置项读取
 *       （由 {@link KmsProperties#getSecrets()} 绑定）</li>
 * </ol>
 *
 * <p>开发阶段直接使用明文配置即可；生产环境建议通过环境变量注入，
 * 或切换到 {@link JasyptSecretProvider} 使用 ENC() 加密。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class EnvironmentSecretProvider implements SecretProvider {

    /**
     * 环境变量名前缀
     *
     * <p>密钥标识 {@code db.password} 对应环境变量 {@code PMIS_SECRETS_DB_PASSWORD}。
     */
    private static final String ENV_PREFIX = "PMIS_SECRETS_";

    /** KMS 配置属性，提供 {@code pmis.kms.secrets.*} 配置项 */
    private final KmsProperties kmsProperties;

    /** Spring Environment，用于读取环境变量与系统属性 */
    private final Environment environment;

    /**
     * 根据密钥名获取明文密钥
     *
     * <p>优先从环境变量读取，其次从 Nacos 配置（{@link KmsProperties#getSecrets()}）读取。
     *
     * @param secretKey 密钥标识（如 "db.password"）
     * @return 明文密钥，不存在返回 null
     */
    @Override
    public String getSecret(String secretKey) {
        // 1. 优先从环境变量读取
        String envVarName = toEnvVarName(secretKey);
        String envValue = environment.getProperty(envVarName);
        if (envValue != null && !envValue.isEmpty()) {
            return envValue;
        }
        // 2. 其次从 Nacos 配置文件读取（pmis.kms.secrets.*）
        Map<String, String> secrets = kmsProperties.getSecrets();
        if (secrets != null) {
            String configValue = secrets.get(secretKey);
            if (configValue != null && !configValue.isEmpty()) {
                return configValue;
            }
        }
        return null;
    }

    /**
     * 将密钥标识转换为环境变量名
     *
     * <p>转换规则：点号替换为下划线，全部转大写，加 {@code PMIS_SECRETS_} 前缀。
     * 示例：{@code db.password} → {@code PMIS_SECRETS_DB_PASSWORD}
     *
     * @param secretKey 密钥标识
     * @return 对应的环境变量名
     */
    protected String toEnvVarName(String secretKey) {
        return ENV_PREFIX + secretKey.toUpperCase().replace('.', '_');
    }
}
