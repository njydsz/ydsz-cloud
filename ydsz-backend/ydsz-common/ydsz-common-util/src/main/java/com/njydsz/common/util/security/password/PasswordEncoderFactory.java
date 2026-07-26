package com.njydsz.common.util.security.password;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * PasswordEncoder 工厂
 *
 * <p>提供全局 PasswordEncoder 实例管理，业务方可通过 {@link #setDefault(PasswordEncoder)}
 * 替换实现（如 BCrypt/Argon2id/PBKDF2），不修改业务代码。</p>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class PasswordEncoderFactory {

    private static final AtomicReference<PasswordEncoder> DEFAULT = new AtomicReference<>(new Pbkdf2PasswordEncoder());

    private PasswordEncoderFactory() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 获取全局默认密码编码器
     *
     * @return PasswordEncoder 实例，默认为 Pbkdf2PasswordEncoder
     */
    public static PasswordEncoder getDefault() {
        return DEFAULT.get();
    }

    /**
     * 设置全局默认密码编码器
     *
     * @param encoder 密码编码器实现，不能为 null
     * @throws NullPointerException 当 encoder 为 null 时
     */
    public static void setDefault(PasswordEncoder encoder) {
        Objects.requireNonNull(encoder, "PasswordEncoder must not be null");
        DEFAULT.set(encoder);
    }
}
