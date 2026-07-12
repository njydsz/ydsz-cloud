package com.njydsz.pmis.common.kms;

import lombok.RequiredArgsConstructor;

/**
 * 密钥管理器（业务统一入口）
 *
 * <p>业务代码通过此入口获取各类密钥，避免直接依赖 {@link SecretProvider} 具体实现。
 * 内部委托给当前生效的 {@link SecretProvider}（由 {@code pmis.kms.provider} 决定）。
 *
 * <p>常见密钥标识：
 * <ul>
 *   <li>{@code db.password}：数据库密码</li>
 *   <li>{@code redis.password}：Redis 密码</li>
 *   <li>{@code jwt.secret}：JWT 签名密钥</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * @RequiredArgsConstructor
 * public class SomeService {
 *     private final SecretManager secretManager;
 *
 *     public void doSomething() {
 *         String dbPwd = secretManager.getDbPassword();
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@RequiredArgsConstructor
public class SecretManager {

    /** 密钥标识：数据库密码 */
    public static final String KEY_DB_PASSWORD = "db.password";

    /** 密钥标识：Redis 密码 */
    public static final String KEY_REDIS_PASSWORD = "redis.password";

    /** 密钥标识：JWT 签名密钥 */
    public static final String KEY_JWT_SECRET = "jwt.secret";

    /** 密钥提供者（由 Spring 注入当前生效的实现） */
    private final SecretProvider secretProvider;

    /**
     * 根据密钥名获取明文密钥
     *
     * @param key 密钥标识
     * @return 明文密钥，不存在返回 null
     */
    public String getSecret(String key) {
        return secretProvider.getSecret(key);
    }

    /**
     * 根据密钥名获取明文密钥，不存在则返回默认值
     *
     * @param key          密钥标识
     * @param defaultValue 默认值
     * @return 明文密钥，不存在返回 defaultValue
     */
    public String getSecret(String key, String defaultValue) {
        return secretProvider.getSecret(key, defaultValue);
    }

    /**
     * 获取数据库密码
     *
     * @return 数据库密码，不存在返回 null
     */
    public String getDbPassword() {
        return getSecret(KEY_DB_PASSWORD);
    }

    /**
     * 获取 Redis 密码
     *
     * @return Redis 密码，不存在返回 null
     */
    public String getRedisPassword() {
        return getSecret(KEY_REDIS_PASSWORD);
    }

    /**
     * 获取 JWT 签名密钥
     *
     * @return JWT 密钥，不存在返回 null
     */
    public String getJwtSecret() {
        return getSecret(KEY_JWT_SECRET);
    }
}
