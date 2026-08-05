package com.remisoft.common.util.security;

import java.security.GeneralSecurityException;

/**
 * 统一加密操作异常类。
 *
 * <p>为 remi-common-util 安全模块（AesUtils / Rsa2Utils / DigestUtils / PwdUtils）提供一致的异常类型，
 * 方便调用方统一捕获并处理所有加密相关错误，避免零散地捕获 {@code RuntimeException} / {@link GeneralSecurityException}。
 *
 * <p><b>设计选择（ RuntimeException vs Checked Exception）：</b>
 * <ul>
 *   <li>继承 {@link RuntimeException}，与模块内 Rsa2Utils / PwdUtils 已有的异常抛出风格一致</li>
 *   <li>加密失败通常是不可恢复的系统错误（配置错误、非法参数、底层 Provider 缺失），
 *       强制捕获（checked）并不带来实际收益，反而增加调用方样板代码</li>
 *       （参考 {@code java.security.ProviderException} / {@code javax.crypto.BadPaddingException#getCause()} 等 JDK 同类设计）
 * </ul>
 *
 * <p><b>典型用法：</b>
 * <pre>{@code
 * try {
 *     String ciphertext = AesUtils.encrypt(data, key);
 * } catch (CryptoException e) {
 *     log.error("加密失败: {}", e.getMessage(), e);
 *     throw new BusinessException("数据加密服务暂不可用", e);
 * }
 * }</pre>
 *
 * @author remi-team
 * @since 1.3.0
 */
public class CryptoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 构造 CryptoException。
     *
     * @param message 异常消息
     */
    public CryptoException(String message) {
        super(message);
    }

    /**
     * 构造 CryptoException（带根因）。
     *
     * @param message 异常消息
     * @param cause   根因异常
     */
    public CryptoException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 构造 CryptoException（仅根因）。
     *
     * @param cause 根因异常
     */
    public CryptoException(Throwable cause) {
        super(cause);
    }

    /**
     * 从 {@link GeneralSecurityException} 便捷转换为 CryptoException。
     *
     * <p>JDK 的加密方法（如 {@code Cipher.getInstance}、{@code KeyFactory.generatePublic}）都会抛出
     * {@link GeneralSecurityException}，该异常是 checked exception，调用方必须 try-catch。
     * 使用本方法可以一行将其转为 unchecked 的 CryptoException，避免方法签名污染。
     *
     * <pre>{@code
     * // 在工具方法内部：
 * PublicKey key = CryptoException.wrap(() -> keyFactory.generatePublic(spec));
     * }</pre>
     *
     * @param operation 可能抛出 GeneralSecurityException 的操作
     * @param <T>       操作返回类型
     * @return 操作结果
     * @throws CryptoException 包装了原始 GeneralSecurityException
     */
    public static <T> T wrap(ThrowingSupplier<T> operation) {
        try {
            return operation.get();
        } catch (GeneralSecurityException e) {
            throw new CryptoException("加密操作失败: " + e.getMessage(), e);
        }
    }

    /**
     * 可能抛出 {@link GeneralSecurityException} 的操作。
     *
     * @param <T> 操作返回类型
     */
    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws GeneralSecurityException;
    }
}
