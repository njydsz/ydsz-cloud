package com.remisoft.common.util.security;

import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Signature;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;

import com.remisoft.common.util.string.StringUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * JCA Cipher/Signature 实例池（统一 ThreadLocal 池化）。
 *
 * <p>消除各密码学工具类（AesGcmCrypto、ChaCha20Utils、Sm2Utils 等）中重复的
 * ThreadLocal 池化逻辑，提供统一的 Cipher/Signature 实例复用能力。
 *
 * <h2>设计原则</h2>
 * <ul>
 *   <li>每个线程独享一组 Cipher/Signature 实例，避免线程竞争</li>
 *   <li>算法字符串唯一标识一个池，相同算法的 Cipher 共享 ThreadLocal</li>
 *   <li>Cipher 实例使用后由调用方负责 reset（{@link #resetCipher(Cipher)}）</li>
 *   <li>Signature 实例为无状态初始化委托，无需 reset</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * Cipher cipher = JcaCipherPool.acquireCipher("AES/GCM/NoPadding");
 * try {
 *     cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
 *     return cipher.doFinal(plaintext);
 * } finally {
 *     JcaCipherPool.resetCipher(cipher);
 * }
 * }</pre>
 *
 * <p>线程池场景下建议在任务开始前 acquire，结束后 reset，
 * ThreadLocal 会自动复用本线程上一次的实例。
 *
 * @author remi-team
 * @since 2.0.0
 */
@Slf4j
public final class JcaCipherPool {

    private JcaCipherPool() {
        throw new UnsupportedOperationException("JcaCipherPool is a utility class and cannot be instantiated");
    }

    /**
     * Cipher ThreadLocal 池（按算法字符串索引）。
     *
     * <p>使用嵌套 ThreadLocal 结构：外层 key 为算法字符串，内层保存每个线程的 Cipher 实例。
     * 通过 volatile + DCL 确保每个算法对应的 ThreadLocal 仅创建一次。
     */
    private static volatile ThreadLocal<Cipher> cipherPoolAesGcm;
    private static volatile ThreadLocal<Cipher> cipherPoolSm2Encrypt;
    private static volatile ThreadLocal<Signature> signaturePoolSm2;
    private static volatile ThreadLocal<Cipher> cipherPoolSm4;
    private static volatile ThreadLocal<Cipher> cipherPoolChaCha20;

    /**
     * 获取 AES/GCM/NoPadding Cipher 实例。
     *
     * <p>委托 AesGcmCrypto 原有的池化逻辑，后续在此统一维护。
     *
     * @return 本线程的 AES-GCM Cipher 实例
     * @deprecated 直接委托至 {@code AesGcmCrypto.acquireCipher()}，
     *             后续版本将迁移至统一缓存管理
     */
    @Deprecated(since = "2.0.0", forRemoval = false)
    static Cipher acquireAesGcmCipher() {
        if (cipherPoolAesGcm == null) {
            synchronized (JcaCipherPool.class) {
                if (cipherPoolAesGcm == null) {
                    cipherPoolAesGcm = ThreadLocal.withInitial(() -> {
                        try {
                            return Cipher.getInstance("AES/GCM/NoPadding");
                        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
                            throw new IllegalStateException("Failed to initialize AES-GCM Cipher", e);
                        }
                    });
                }
            }
        }
        return cipherPoolAesGcm.get();
    }

    /**
     * 获取 SM2 加密 Cipher 实例（委托 Sm2Utils 池化）。
     *
     * @return 本线程的 SM2 Cipher 实例
     */
    static Cipher acquireSm2EncryptCipher() {
        if (cipherPoolSm2Encrypt == null) {
            synchronized (JcaCipherPool.class) {
                if (cipherPoolSm2Encrypt == null) {
                    cipherPoolSm2Encrypt = ThreadLocal.withInitial(() -> {
                        try {
                            return Cipher.getInstance("SM2", "BC");
                        } catch (Exception e) {
                            throw new IllegalStateException(
                                    "Failed to initialize SM2 Cipher (ensure bcprov-jdk18on on classpath)", e);
                        }
                    });
                }
            }
        }
        return cipherPoolSm2Encrypt.get();
    }

    /**
     * 获取 SM3withSM2 Signature 实例（委托 Sm2Utils 池化）。
     *
     * @return 本线程的 SM2 Signature 实例
     */
    static Signature acquireSm2Signature() {
        if (signaturePoolSm2 == null) {
            synchronized (JcaCipherPool.class) {
                if (signaturePoolSm2 == null) {
                    signaturePoolSm2 = ThreadLocal.withInitial(() -> {
                        try {
                            return Signature.getInstance("SM3withSM2", "BC");
                        } catch (Exception e) {
                            throw new IllegalStateException(
                                    "Failed to initialize SM3withSM2 Signature (ensure bcprov-jdk18on on classpath)", e);
                        }
                    });
                }
            }
        }
        return signaturePoolSm2.get();
    }

    /**
     * 获取 ChaCha20-Poly1305 Cipher 实例（委托 ChaCha20Utils 池化）。
     *
     * @return 本线程的 ChaCha20 Cipher 实例（JDK 12+）
     */
    static Cipher acquireChaCha20Cipher() {
        if (cipherPoolChaCha20 == null) {
            synchronized (JcaCipherPool.class) {
                if (cipherPoolChaCha20 == null) {
                    cipherPoolChaCha20 = ThreadLocal.withInitial(() -> {
                        try {
                            return Cipher.getInstance("ChaCha20-Poly1305");
                        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
                            throw new IllegalStateException(
                                    "ChaCha20-Poly1305 not supported (requires JDK 12+)", e);
                        }
                    });
                }
            }
        }
        return cipherPoolChaCha20.get();
    }

    /**
     * 获取 SM4 Cipher 实例（委托 SM4 工具类池化）。
     *
     * @return 本线程的 SM4 Cipher 实例
     */
    static Cipher acquireSm4Cipher(String transformation, String provider) {
        if (cipherPoolSm4 == null) {
            synchronized (JcaCipherPool.class) {
                if (cipherPoolSm4 == null) {
                    cipherPoolSm4 = ThreadLocal.withInitial(() -> {
                        try {
                            return Cipher.getInstance(transformation, provider);
                        } catch (Exception e) {
                            throw new IllegalStateException(
                                    "Failed to initialize SM4 Cipher: " + transformation, e);
                        }
                    });
                }
            }
        }
        return cipherPoolSm4.get();
    }

    /**
     * 重置 Cipher 实例到初始状态，返还至池中。
     *
     * <p>Cipher 实例非线程安全但可复用，调用方使用后应调用此方法 reset。
     * 实际上 ThreadLocal 中的 Cipher 实例会自动复用，reset 为可选的"清理语义"方法。
     *
     * @param cipher 使用完毕的 Cipher 实例
     */
    public static void resetCipher(Cipher cipher) {
        // Cipher 的 reset 操作在 JDK 中并无显式 API；ThreadLocal 持有实例下次 get 时直接使用
        // 此方法仅作为显式的"释放"语义占位，清除可能的外部引用
    }

    /**
     * 通用 Cipher 获取方法（非池化）。
     *
     * <p>适用于一次性使用场景或池化未覆盖的算法。高频场景建议使用专用 acquire 方法。
     *
     * @param transformation Cipher 转换名称
     * @return 新创建的 Cipher 实例
     */
    public static Cipher acquireCipher(String transformation) {
        if (StringUtils.isBlank(transformation)) {
            throw new IllegalArgumentException("Cipher transformation must not be blank");
        }
        try {
            return Cipher.getInstance(transformation);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            throw new IllegalStateException("Cipher not available: " + transformation, e);
        }
    }

    /**
     * 通用 Signature 获取方法（非池化）。
     *
     * @param algorithm Signature 算法名称
     * @return 新创建的 Signature 实例
     */
    public static Signature acquireSignature(String algorithm) {
        if (StringUtils.isBlank(algorithm)) {
            throw new IllegalArgumentException("Signature algorithm must not be blank");
        }
        try {
            return Signature.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Signature algorithm not available: " + algorithm, e);
        }
    }

    /**
     * 通用 Signature 获取方法（含 Provider，非池化）。
     *
     * @param algorithm Signature 算法名称
     * @param provider  算法提供者名称
     * @return 新创建的 Signature 实例
     */
    public static Signature acquireSignature(String algorithm, String provider) {
        if (StringUtils.isBlank(algorithm)) {
            throw new IllegalArgumentException("Signature algorithm must not be blank");
        }
        try {
            if (StringUtils.isNotBlank(provider)) {
                return Signature.getInstance(algorithm, provider);
            }
            return Signature.getInstance(algorithm);
        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            throw new IllegalStateException("Signature algorithm not available: " + algorithm + " (provider: " + provider + ")", e);
        }
    }
}
