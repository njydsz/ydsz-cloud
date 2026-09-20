package com.njydsz.common.seata.xid;

import com.njydsz.common.util.string.StringUtils;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * XID 签名校验器 — 防止 XID 伪造攻击。
 *
 * <p>规范建议生产环境配置 {@code ydzs.seata.xid-sign-secret}（长度 ≥ 16 位），
 * 本工具对 XID 进行 HMAC-SHA256 签名校验，确保跨服务传播的 XID 未被篡改。
 *
 * <p>签名机制：
 * <ul>
 *   <li>发送方：将明文 XID 通过 HMAC-SHA256 计算签名，将签名附加在 XID Header 中（格式 {@code base64(xid:signature)}）</li>
 *   <li>接收方：解析得到明文 XID 和签名，使用相同 secret 重新计算 HMAC 并比对</li>
 * </ul>
 *
 * <p>使用方式：
 * <pre>
 *   // 发送方（通常在 Gateway 或根服务中）
 *   String xid = RootContext.getXID();
 *   String signed = XidSignatureValidator.sign(xid, secret);
 *   // 设置到 Header: TX_XID: signed
 *
 *   // 接收方（XidServletFilter 自动调用）
 *   String plainXid = XidSignatureValidator.verifyAndExtract(signedXid, secret);
 *   // 验签失败返回 null，调用方应拒绝绑定
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.20
 */
public final class XidSignatureValidator {

    /** 日志实例 */
    private static final Logger LOG = LoggerFactory.getLogger(XidSignatureValidator.class);

    /** HMAC 算法 */
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /** 签名分隔符 */
    private static final String SIGN_SEPARATOR = ":";

    /** 私有构造器（工具类禁止实例化） */
    private XidSignatureValidator() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 对 XID 进行 HMAC-SHA256 签名，返回 base64(xid:signature) 格式。
     *
     * @param xid 原始 XID（不可为空）
     * @param secret 签名密钥（不可为空，建议 ≥ 16 位）
     * @return 签名后的字符串，格式 base64(xid:hexHmacSignature)
     * @throws IllegalArgumentException secret 为空时抛出
     */
    public static String sign(String xid, String secret) {
        if (StringUtils.isEmpty(xid) || StringUtils.isEmpty(secret)) {
            throw new IllegalArgumentException("XID and secret must not be empty for signing");
        }

        String signature = hmacSha256(xid, secret);
        return java.util.Base64.getEncoder()
            .encodeToString((xid + SIGN_SEPARATOR + signature).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 验签并提取原始 XID。
     *
     * @param signedXid 签名的 XID（base64 编码，{@code sign()} 方法的输出）
     * @param secret 签名密钥
     * @return 验签成功时返回原始 XID；验签失败时返回 null
     */
    public static String verifyAndExtract(String signedXid, String secret) {
        if (StringUtils.isEmpty(signedXid) || StringUtils.isEmpty(secret)) {
            return null;
        }

        try {
            // Base64 解码
            byte[] decoded = java.util.Base64.getDecoder().decode(signedXid);
            String payload = new String(decoded, StandardCharsets.UTF_8);

            int separatorIndex = payload.lastIndexOf(SIGN_SEPARATOR);
            if (separatorIndex <= 0 || separatorIndex >= payload.length() - 1) {
                LOG.warn("Invalid signed XID format: missing separator");
                return null;
            }

            String plainXid = payload.substring(0, separatorIndex);
            String receivedSignature = payload.substring(separatorIndex + 1);

            // 重新计算签名
            String expectedSignature = hmacSha256(plainXid, secret);

            // 安全比较（防时序攻击）
            if (MessageDigest.isEqual(
                receivedSignature.getBytes(StandardCharsets.UTF_8),
                expectedSignature.getBytes(StandardCharsets.UTF_8))) {
                return plainXid;
            }

            LOG.warn("XID signature verification failed, possible forgery attempt");
            return null;
        } catch (Exception e) {
            LOG.warn("Failed to verify XID signature: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 计算 HMAC-SHA256 签名（Hex 字符串）。
     *
     * @param data 待签名数据
     * @param secret 密钥
     * @return Hex 编码的签名
     */
    private static String hmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKey = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(secretKey);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate HMAC-SHA256", e);
        }
    }

    /**
     * 字节数组转 Hex 字符串。
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
