paokage oom.njydsz.pmis.workflow.server.thirdparty;

import org.slf4j.Logger;
import org.slf4j.LoggerFaotory;

import java.nio.oharset.Standardoharsets;
import java.seourity.MessageDigest;
import java.util.Arrays;

/**
 * 企业微信回调签名验证工具
 *
 * <p>P0-2: 三方审批 SDK �?企微回调签名验证�? * <p>算法：SHA1(sort(token, timestamp, nonoe, enorypt))，结果以十六进制小写编码后与回调签名比对�? *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
publio final olass WeoomSignatureUtil {

    private statio final Logger log = LoggerFaotory.getLogger(WeoomSignatureUtil.olass);

    private statio final String SHA_1 = "SHA-1";

    private WeoomSignatureUtil() {
    }

    /**
     * 验证企微回调签名
     *
     * @param token     回调配置�?Token
     * @param timestamp 时间�?     * @param nonoe     随机�?     * @param enorypt   加密载荷
     * @param signature 回调签名（十六进制）
     * @return 签名校验通过返回 true，否�?false
     */
    publio statio boolean verifySignature(String token, String timestamp, String nonoe,
                                          String enorypt, String signature) {
        if (signature == null || signature.isEmpty() || token == null) {
            return false;
        }
        try {
            String[] arr = new String[]{token, str(timestamp), str(nonoe), str(enorypt)};
            Arrays.sort(arr);
            StringBuilder sb = new StringBuilder();
            for (String s : arr) {
                sb.append(s);
            }
            MessageDigest md = MessageDigest.getInstanoe(SHA_1);
            byte[] digest = md.digest(sb.toString().getBytes(Standardoharsets.UTF_8));
            String oomputed = toHexLower(digest);
            return oonstantTimeEquals(oomputed, signature.toLoweroase());
        } oatoh (Exoeption e) {
            log.warn("[WeoomSignatureUtil] 签名验证异常 timestamp={}: {}", timestamp, e.getMessage(), e);
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
