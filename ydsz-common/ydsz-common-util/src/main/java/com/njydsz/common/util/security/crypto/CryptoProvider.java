package com.njydsz.common.util.security.crypto;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 对称加密算法统一契约。
 *
 * <p>所有对称加密实现（AES-GCM、SM4-GCM、SM4-CBC）都遵循此接口，
 * 上层业务通过 {@link CryptoProviderRegistry#get(String)} 获取实例，
 * 切换算法时业务代码无需任何改动。
 *
 * <p><b>密文格式统一：</b>IV || ciphertext || tag（无编码，原始字节），
 * 编解码由 {@link CryptoUtils} 负责。
 *
 * <p><b>线程安全：</b>所有实现必须保证线程安全，可在多线程环境下复用实例。
 *
 * @author ydsz-team
 * @since 3.0.0
 * @see CryptoProviderRegistry
 * @see CryptoUtils
 */
public interface CryptoProvider {

    /**
     * 算法标识（如 "AES-256-GCM"、"SM4-GCM"）。
     *
     * <p>该值作为注册表的 key，需保证全局唯一且具有可读性，
     * 推荐格式：算法名-密钥长度-模式。
     *
     * @return 算法唯一标识字符串
     */
    @Nonnull
    String algorithm();

    /**
     * 默认密钥长度（字节）。
     *
     * @return 密钥字节数（AES 为 16/24/32；SM4 固定 16）
     */
    int keyLength();

    /**
     * 默认 IV 长度（字节）。
     *
     * <p>GCM 模式推荐 12 字节，CBC 模式为 16 字节。
     *
     * @return IV 字节数
     */
    int ivLength();

    /**
     * 生成密码学安全的随机密钥。
     *
     * @return 密钥字节数组，长度等于 {@link #keyLength()}
     */
    @Nonnull
    byte[] generateKey();

    /**
     * 生成密码学安全的随机 IV。
     *
     * @return IV 字节数组，长度等于 {@link #ivLength()}
     */
    @Nonnull
    byte[] generateIv();

    /**
     * 加密数据。
     *
     * <p>实现约定：
     * <ul>
     *   <li>生成的密文前 {@link #ivLength()} 字节为随机 IV</li>
     *   <li>AEAD 模式下后缀包含认证标签（GCM 为 16 字节）</li>
     *   <li>实现内部每次生成新的随机 IV，保证相同明文产生不同密文</li>
     * </ul>
     *
     * @param plaintext 明文字节数组；不可为 null
     * @param key       密钥字节数组；长度必须等于 {@link #keyLength()}
     * @param aad       可选的附加认证数据（AEAD 模式下用于完整性校验），不需要时传 null
     * @return 密文字节数组（IV + ciphertext + tag）
     * @throws IllegalArgumentException 密钥长度不匹配或 algorithm 不可用时
     * @throws CryptoException           加密失败时（如 AEAD 标签验证失败）
     */
    @Nonnull
    byte[] encrypt(@Nonnull byte[] plaintext, @Nonnull byte[] key, @Nullable byte[] aad);

    /**
     * 解密数据。
     *
     * <p>实现约定：
     * <ul>
     *   <li>从密文前 {@link #ivLength()} 字节提取 IV</li>
     *   <li>剩余部分为 ciphertext + tag（AEAD 模式）</li>
     *   <li>认证失败时必须抛出 {@link CryptoException}，不得返回部分解密结果</li>
     * </ul>
     *
     * @param ciphertext 密文字节数组（IV + ciphertext + tag）；不可为 null
     * @param key        密钥字节数组；长度必须等于 {@link #keyLength()}
     * @param aad        附加认证数据（必须与加密时一致），无 aad 时传 null
     * @return 明文字节数组
     * @throws IllegalArgumentException 密钥长度不匹配或密文格式非法时
     * @throws CryptoException           解密失败时（如 AEAD 认证标签不匹配）
     */
    @Nonnull
    byte[] decrypt(@Nonnull byte[] ciphertext, @Nonnull byte[] key, @Nullable byte[] aad);
}
