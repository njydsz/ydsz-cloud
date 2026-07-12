package com.njydsz.pmis.common.util.security.password;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * PBKDF2 WithHmacSHA256 密码编码器
 *
 * <p>在缺少 BCrypt 库时可用作替代实现。默认迭代 600000 次（OWASP 2023 推荐）。</p>
 *
 * <p><b>编码格式：</b>{@code pbkdf2_sha256$<iterations>$<base64-salt>$<base64-hash>}</p>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 3.5.0
 */
public class Pbkdf2PasswordEncoder implements PasswordEncoder {

    public static final int DEFAULT_ITERATIONS = 600_000;
    public static final int DEFAULT_SALT_LENGTH = 16;
    public static final int DEFAULT_KEY_LENGTH = 32;
    public static final String ALGORITHM = "pbkdf2_sha256";

    private static final String PRF = "PBKDF2WithHmacSHA256";

    private final SecureRandom random = new SecureRandom();
    private final int iterations;
    private final int saltLength;
    private final int keyLength;

    public Pbkdf2PasswordEncoder() {
        this(DEFAULT_ITERATIONS, DEFAULT_SALT_LENGTH, DEFAULT_KEY_LENGTH);
    }

    public Pbkdf2PasswordEncoder(int iterations, int saltLength, int keyLength) {
        if (iterations < 1000) {
            throw new IllegalArgumentException("iterations must be >= 1000");
        }
        this.iterations = iterations;
        this.saltLength = saltLength;
        this.keyLength = keyLength;
    }

    @Override
    public String encode(CharSequence rawPassword) {
        byte[] salt = new byte[saltLength];
        random.nextBytes(salt);
        byte[] hash = pbkdf2(rawPassword.toString().toCharArray(), salt, iterations, keyLength);
        return ALGORITHM + "$" + iterations + "$"
                + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(hash);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        String[] parts = encodedPassword.split("\\$");
        if (parts.length != 4 || !ALGORITHM.equals(parts[0])) {
            return false;
        }
        int iters;
        try {
            iters = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return false;
        }
        byte[] salt;
        byte[] expected;
        try {
            salt = Base64.getDecoder().decode(parts[2]);
            expected = Base64.getDecoder().decode(parts[3]);
        } catch (IllegalArgumentException e) {
            return false;
        }
        byte[] actual = pbkdf2(rawPassword.toString().toCharArray(), salt, iters, expected.length);
        return MessageDigest.isEqual(expected, actual);
    }

    @Override
    public String getAlgorithmName() {
        return ALGORITHM;
    }

    private byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyLength) {
        try {
            javax.crypto.spec.PBEKeySpec spec = new javax.crypto.spec.PBEKeySpec(password, salt, iterations, keyLength * 8);
            javax.crypto.SecretKeyFactory factory = javax.crypto.SecretKeyFactory.getInstance(PRF);
            return factory.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("PBKDF2WithHmacSHA256 not supported", e);
        } catch (java.security.spec.InvalidKeySpecException e) {
            throw new IllegalStateException("Invalid PBKDF2 key spec", e);
        }
    }
}
