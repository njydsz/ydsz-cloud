package com.njydsz.pmis.common.util.security.password;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * PasswordEncoder 工厂
 *
 * <p>提供全局 PasswordEncoder 实例管理，业务方可通过 {@link #setDefault(PasswordEncoder)}
 * 替换实现（如 BCrypt/Argon2id/PBKDF2），不修改业务代码。</p>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 * @since 3.5.0
 */
public final class PasswordEncoderFactory {

    private static final AtomicReference<PasswordEncoder> DEFAULT = new AtomicReference<>(new Pbkdf2PasswordEncoder());

    private PasswordEncoderFactory() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static PasswordEncoder getDefault() {
        return DEFAULT.get();
    }

    public static void setDefault(PasswordEncoder encoder) {
        Objects.requireNonNull(encoder, "PasswordEncoder must not be null");
        DEFAULT.set(encoder);
    }
}
