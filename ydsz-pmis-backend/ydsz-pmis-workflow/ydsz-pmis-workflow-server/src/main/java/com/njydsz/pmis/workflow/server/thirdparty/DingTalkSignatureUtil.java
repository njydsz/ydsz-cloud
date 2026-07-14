package com.njydsz.pmis.workflow.server.thirdparty;

import com.njydsz.pmis.common.util.security.DigestUtils;

/**
 * 钉钉回调签名验证工具
 *
 * <p>P0-2: 三方审批 SDK — 钉钉回调签名验证。
 * <p>算法：HmacSHA256，密钥为 appSecret，签名内容为 timestamp + nonce + encrypt，
 * 计算结果经 Base64 编码后与回调签名比对。
 *
 * <p><b>P1-1 架构优化</b>：签名计算和常量时间比较委托到 {@link CryptoSignUtil}，
 * 消除重复的 HmacSHA256 实现。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public final class DingTalkSignatureUtil {

    private DingTalkSignatureUtil() {
    }

    /**
     * 验证钉钉回调签名
     *
     * @param timestamp 时间戳
     * @param nonce     随机串
     * @param encrypt   加密载荷
     * @param signature 回调签名（Base64）
     * @param appSecret 应用 appSecret（作为 HmacSHA256 密钥）
     * @return 签名校验通过返回 true，否则 false
     */
    public static boolean verifySignature(String timestamp, String nonce, String encrypt,
                                          String signature, String appSecret) {
        if (signature == null || signature.isEmpty() || appSecret == null || appSecret.isEmpty()) {
            return false;
        }
        String data = str(timestamp) + str(nonce) + str(encrypt);
        return DigestUtils.verifySignature(data, appSecret, signature,
                DigestUtils.SignatureEncoding.BASE64);
    }

    private static String str(String s) {
        return s == null ? "" : s;
    }
}
