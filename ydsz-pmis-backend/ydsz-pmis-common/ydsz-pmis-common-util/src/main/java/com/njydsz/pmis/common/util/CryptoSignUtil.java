package com.njydsz.pmis.common.util;

import com.njydsz.pmis.common.util.security.DigestUtils;

/**
 * 签名验证工具类（已废弃，请使用 {@link DigestUtils}）。
 *
 * <p>HMAC-SHA256 签名计算与验签功能已统一到 {@link DigestUtils}。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * @deprecated 请使用 {@link DigestUtils}（HMAC-SHA256 / 签名验证 / 常量时间比较）
 */
@Deprecated(since = "1.4.0", forRemoval = true)
public final class CryptoSignUtil {

    private CryptoSignUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 签名编码格式。
     *
     * @deprecated 请使用 {@link DigestUtils.SignatureEncoding}
     */
    @Deprecated(since = "1.4.0", forRemoval = true)
    public enum SignatureEncoding {
        /** Base64 编码 */
        BASE64,
        /** Hex 编码 */
        HEX
    }

    /**
     * 计算 HMAC-SHA256 并返回 Base64 编码的签名。
     *
     * @param data   原始数据
     * @param secret 密钥
     * @return Base64 编码的签名
     * @deprecated 请使用 {@link DigestUtils#hmacSha256Base64(String, String)}
     */
    @Deprecated(since = "1.4.0", forRemoval = true)
    public static String hmacSha256Base64(String data, String secret) {
        return DigestUtils.hmacSha256Base64(data, secret);
    }

    /**
     * 计算 HMAC-SHA256 并返回 Hex 编码的签名。
     *
     * @param data   原始数据
     * @param secret 密钥
     * @return Hex 编码的签名
     * @deprecated 请使用 {@link DigestUtils#hmacSha256Hex(String, String)}
     */
    @Deprecated(since = "1.4.0", forRemoval = true)
    public static String hmacSha256Hex(String data, String secret) {
        return DigestUtils.hmacSha256Hex(data, secret);
    }

    /**
     * 验证签名（常量时间比较，防止时序攻击）。
     *
     * @param data      原始数据
     * @param secret    密钥
     * @param signature 待验证的签名
     * @param encoding  签名编码格式
     * @return 验证通过返回 true
     * @deprecated 请使用 {@link DigestUtils#verifySignature(String, String, String, DigestUtils.SignatureEncoding)}
     */
    @Deprecated(since = "1.4.0", forRemoval = true)
    public static boolean verifySignature(String data, String secret, String signature, SignatureEncoding encoding) {
        DigestUtils.SignatureEncoding targetEncoding = encoding == SignatureEncoding.BASE64
                ? DigestUtils.SignatureEncoding.BASE64
                : DigestUtils.SignatureEncoding.HEX;
        return DigestUtils.verifySignature(data, secret, signature, targetEncoding);
    }
}
