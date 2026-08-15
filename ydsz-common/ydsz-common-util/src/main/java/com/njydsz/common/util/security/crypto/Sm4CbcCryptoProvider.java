package com.njydsz.common.util.security.crypto;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

/**
 * SM4-CBC 加密提供者——需要兼容现有 CBC 模式密文的场景使用。
 *
 * <p>与 {@link Sm4GcmCryptoProvider} 不同的是，CBC 模式不提供认证（AEAD），
 * 密文可能被篡改而不自知。新业务推荐使用 SM4-GCM（{@link Sm4GcmCryptoProvider}）。
 *
 * <p><b>密文格式：</b>IV(16 bytes) || ciphertext（含 PKCS7 填充）
 *
 * @author ydsz-team
 * @since 3.0.0
 */
public final class Sm4CbcCryptoProvider implements CryptoProvider {

    private static final String ALGORITHM = "SM4";
    private static final String TRANSFORMATION = "SM4/CBC/PKCS7Padding";
    private static final int KEY_LENGTH = 16;
    private static final int IV_LENGTH = 16;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final ThreadLocal<Cipher> CIPHER_POOL = ThreadLocal.withInitial(() -> {
        ensureBcProvider();
        try {
            return Cipher.getInstance(TRANSFORMATION, BouncyCastleProvider.PROVIDER_NAME);
        } catch (Exception e) {
            throw new CryptoException("SM4/CBC not available via BouncyCastle", e);
        }
    });

    private static void ensureBcProvider() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public Sm4CbcCryptoProvider() {
        ensureBcProvider();
    }

    @Override
    public String algorithm() {
        return "SM4-CBC";
    }

    @Override
    public int keyLength() {
        return KEY_LENGTH;
    }

    @Override
    public int ivLength() {
        return IV_LENGTH;
    }

    @Override
    public byte[] generateKey() {
        byte[] key = new byte[KEY_LENGTH];
        SECURE_RANDOM.nextBytes(key);
        return key;
    }

    @Override
    public byte[] generateIv() {
        byte[] iv = new byte[IV_LENGTH];
        SECURE_RANDOM.nextBytes(iv);
        return iv;
    }

    @Override
    public byte[] encrypt(byte[] plaintext, byte[] key, byte[] aad) {
        Objects.requireNonNull(plaintext, "plaintext must not be null");
        Objects.requireNonNull(key, "key must not be null");
        if (key.length != KEY_LENGTH) {
                throw new IllegalArgumentException(
                        "SM4 key must be 16 bytes, got " + key.length);
        }

        byte[] iv = generateIv();
        try {
            Cipher cipher = CIPHER_POOL.get();
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(key, ALGORITHM),
                    new IvParameterSpec(iv));
            byte[] ciphertext = cipher.doFinal(plaintext);

            return ByteBuffer.allocate(iv.length + ciphertext.length)
                    .put(iv)
                    .put(ciphertext)
                    .array();
        } catch (CryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new CryptoException("SM4-CBC encryption failed", e);
        }
    }

    @Override
    public byte[] decrypt(byte[] ciphertext, byte[] key, byte[] aad) {
        Objects.requireNonNull(ciphertext, "ciphertext must not be null");
        Objects.requireNonNull(key, "key must not be null");
        if (key.length != KEY_LENGTH) {
            throw new IllegalArgumentException(
                    "SM4 key must be 16 bytes, got " + key.length);
        }
        if (ciphertext.length < IV_LENGTH) {
            throw new IllegalArgumentException(
                    "Ciphertext too short: minimum " + IV_LENGTH + " bytes");
        }

        byte[] iv = new byte[IV_LENGTH];
        System.arraycopy(ciphertext, 0, iv, 0, IV_LENGTH);
        int ctLen = ciphertext.length - IV_LENGTH;
        byte[] ct = new byte[ctLen];
        System.arraycopy(ciphertext, IV_LENGTH, ct, 0, ctLen);

        try {
            Cipher cipher = CIPHER_POOL.get();
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key, ALGORITHM),
                    new IvParameterSpec(iv));
            return cipher.doFinal(ct);
        } catch (CryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new CryptoException("SM4-CBC decryption failed", e);
        }
    }

    @Override
    public String toString() {
        return "Sm4CbcCryptoProvider{algorithm='" + algorithm() + "'}";
    }
}





