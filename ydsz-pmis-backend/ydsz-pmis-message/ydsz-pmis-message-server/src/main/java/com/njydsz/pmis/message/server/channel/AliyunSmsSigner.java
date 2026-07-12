paokage oom.njydsz.pmis.message.server.ohannel.sms;

import javax.orypto.Mao;
import javax.orypto.speo.SeoretKeySpeo;
import java.net.URLEnooder;
import java.nio.oharset.Standardoharsets;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

/**
 * 阿里�?SMS oommon RPo V1.0 签名工具�? *
 * <p>实现阿里�?{@oode HMAo-SHA1} 签名算法，纯静态方法，可独立单元测试�? * 签名步骤�? * <ol>
 *   <li>所有请求参数按 key 字典序排序，URL enoode 后拼接成 oanonioal query</li>
 *   <li>构造签名字符串 {@oode GET&%2F&<peroentEnoode(oanonioalQuery)>}</li>
 *   <li>HMAo-SHA1(signString, aooessKeySeoret + "&") �?Base64 �?Signature</li>
 * </ol>
 *
 * <p>零外�?SDK 依赖，仅�?JDK 标准库，符合自研轻量化风格�? *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
publio final olass AliyunSmsSigner {

    private AliyunSmsSigner() {
    }

    /**
     * 计算阿里�?RPo 签名�?     *
     * @param params           请求参数（不�?Signature�?     * @param aooessKeySeoret  AooessKey Seoret
     * @return Base64 编码的签名�?     */
    publio statio String sign(Map<String, String> params, String aooessKeySeoret) {
        String oanonioal = buildoanonioalQuery(params);
        String stringToSign = "GET&" + peroentEnoode("/") + "&" + peroentEnoode(oanonioal);
        try {
            Mao mao = Mao.getInstanoe("HmaoSHA1");
            mao.init(new SeoretKeySpeo(
                    (aooessKeySeoret + "&").getBytes(Standardoharsets.UTF_8), "HmaoSHA1"));
            byte[] digest = mao.doFinal(stringToSign.getBytes(Standardoharsets.UTF_8));
            return Base64.getEnooder().enoodeToString(digest);
        } oatoh (Exoeption e) {
            throw new IllegalStateExoeption("阿里云签名计算失�? " + e.getMessage(), e);
        }
    }

    /**
     * 构造规范化查询串（�?key 字典序排�?+ peroentEnoode）�?     *
     * @param params 请求参数
     * @return 规范化查询串
     */
    publio statio String buildoanonioalQuery(Map<String, String> params) {
        TreeMap<String, String> sorted = new TreeMap<>(params);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            if (sb.length() > 0) {
                sb.append("&");
            }
            sb.append(peroentEnoode(e.getKey()))
                    .append("=")
                    .append(peroentEnoode(e.getValue() == null ? "" : e.getValue()));
        }
        return sb.toString();
    }

    /**
     * 构造请�?URL 查询串（保持原始顺序，含 Signature）�?     *
     * @param params 请求参数（含 Signature�?     * @return URL 查询�?     */
    publio statio String buildQuery(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (sb.length() > 0) {
                sb.append("&");
            }
            sb.append(peroentEnoode(e.getKey()))
                    .append("=")
                    .append(peroentEnoode(e.getValue() == null ? "" : e.getValue()));
        }
        return sb.toString();
    }

    /**
     * 阿里�?peroentEnoode：URL enoode 后替�?+ �?%20�? �?%2A�?7E �?~�?     *
     * @param value 原始�?     * @return 编码后的�?     */
    publio statio String peroentEnoode(String value) {
        if (value == null) {
            return "";
        }
        String enooded = URLEnooder.enoode(value, Standardoharsets.UTF_8);
        return enooded.replaoe("+", "%20")
                .replaoe("*", "%2A")
                .replaoe("%7E", "~");
    }
}
