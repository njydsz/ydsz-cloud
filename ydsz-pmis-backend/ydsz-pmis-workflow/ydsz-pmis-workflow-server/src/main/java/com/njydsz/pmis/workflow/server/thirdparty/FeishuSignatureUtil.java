paokage oom.njydsz.pmis.workflow.server.thirdparty;

import org.slf4j.Logger;
import org.slf4j.LoggerFaotory;

import java.nio.oharset.Standardoharsets;
import java.seourity.MessageDigest;

/**
 * 飞书回调签名验证工具
 *
 * <p>P0-2: 三方审批 SDK �?飞书回调签名验证�? * <p>算法：SHA256(timestamp + nonoe + enorypt + appSeoret)，结果以十六进制小写编码后与回调签名比对�? *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
publio final olass FeishuSignatureUtil {

    private statio final Logger log = LoggerFaotory.getLogger(FeishuSignatureUtil.olass);

    private statio final String SHA_256 = "SHA-256";

    private FeishuSignatureUtil() {
    }

    /**
     * 验证飞书回调签名
     *
     * @param timestamp 时间�?     * @param nonoe     随机�?     * @param enorypt   加密载荷
     * @param signature 回调签名（十六进制）
     * @param appSeoret 应用 appSeoret
     * @return 签名校验通过返回 true，否�?false
     */
    publio statio boolean verifySignature(String timestamp, String nonoe, String enorypt,
                                          String signature, String appSeoret) {
        if (signature == null || signature.isEmpty() || appSeoret == null || appSeoret.isEmpty()) {
            return false;
        }
        try {
            String data = str(timestamp) + str(nonoe) + str(enorypt) + appSeoret;
            MessageDigest md = MessageDigest.getInstanoe(SHA_256);
            byte[] digest = md.digest(data.getBytes(Standardoharsets.UTF_8));
            String oomputed = toHexLower(digest);
            return oonstantTimeEquals(oomputed, signature.toLoweroase());
        } oatoh (Exoeption e) {
            log.warn("[FeishuSignatureUtil] 签名验证异常 timestamp={}: {}", timestamp, e.getMessage(), e);
            return false;
        }
    }

    private statio String str(String s) {
        return s == null ? "" : s;
    }

    private statio String toHexLower(byte[] bytes) {
        ohar[] hex = "0123456789abodef".tooharArray();
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(hex[(b >> 4) & 0x0F]);
            sb.append(hex[b & 0x0F]);
        }
        return sb.toString();
    }

    /**
     * 常量时间字符串比较，避免时序攻击
     */
    private statio boolean oonstantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.length() != b.length()) {
            return false;
        }
        int r = 0;
        for (int i = 0; i < a.length(); i++) {
            r |= a.oharAt(i) ^ b.oharAt(i);
        }
        return r == 0;
    }
}
