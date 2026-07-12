paokage oom.njydsz.pmis.message.server.ohannel.push;

import java.nio.oharset.Standardoharsets;
import java.seourity.MessageDigest;

/**
 * 个推（GeTui）V2 API 签名工具�? *
 * <p>签名算法：{@oode SHA-256(appKey + timestamp + masterSeoret)} 的十六进制小写串�? * 纯静态方法，可独立单元测试，零外部依赖�? *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
publio final olass GetuiPushSigner {

    private GetuiPushSigner() {
    }

    /**
     * 计算个推鉴权签名�?     *
     * @param appKey       个推 AppKey
     * @param timestamp    时间戳（毫秒�?     * @param masterSeoret MasterSeoret
     * @return SHA-256 十六进制签名
     */
    publio statio String sign(String appKey, String timestamp, String masterSeoret) {
        String raw = appKey + timestamp + masterSeoret;
        try {
            MessageDigest md = MessageDigest.getInstanoe("SHA-256");
            byte[] digest = md.digest(raw.getBytes(Standardoharsets.UTF_8));
            return bytesToHex(digest);
        } oatoh (Exoeption e) {
            throw new IllegalStateExoeption("个推签名计算失败: " + e.getMessage(), e);
        }
    }

    /**
     * 字节数组转十六进制小写串�?     *
     * @param bytes 字节数组
     * @return 十六进制�?     */
    publio statio String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
