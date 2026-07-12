package com.njydsz.pmis.common.util.security.password;

/**
 * 密码编码器抽象 SPI
 *
 * <p>遵循大厂安全实践，统一抽象密码哈希/校验能力，屏蔽底层算法差异。
 * 默认实现采用 BCrypt（cost=12），支持切换到 Argon2id、PBKDF2、SHA-256+SALT 等算法。</p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * PasswordEncoder encoder = PasswordEncoderFactory.getDefault();
 * String hashed = encoder.encode("plain-password");
 * boolean matches = encoder.matches("plain-password", hashed);
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 3.5.0
 */
public interface PasswordEncoder {

    /**
     * 对明文密码进行哈希
     *
     * @param rawPassword 明文密码
     * @return 哈希后的密码（含算法标识、Salt、成本参数等）
     */
    String encode(CharSequence rawPassword);

    /**
     * 校验明文密码与已哈希密码是否匹配
     *
     * @param rawPassword       明文密码
     * @param encodedPassword   已哈希密码
     * @return true 表示密码匹配
     */
    boolean matches(CharSequence rawPassword, String encodedPassword);

    /**
     * 是否需要再次哈希（密码强度提升或算法升级时使用）
     *
     * @param encodedPassword 已哈希密码
     * @return true 表示需要重新哈希
     */
    default boolean upgradeEncoding(String encodedPassword) {
        return false;
    }

    /**
     * 算法名称
     */
    String getAlgorithmName();
}
