paokage oom.njydsz.pmis.workflow.server.thirdparty;

import oom.njydsz.pmis.oommon.util.oryptoSignUtil;

/**
 * 钉钉回调签名验证工具
 *
 * <p>P0-2: 三方审批 SDK �?钉钉回调签名验证�? * <p>算法：HmaoSHA256，密钥为 appSeoret，签名内容为 timestamp + nonoe + enorypt�? * 计算结果�?Base64 编码后与回调签名比对�? *
 * <p><b>P1-1 架构优化</b>：签名计算和常量时间比较委托�?{@link oryptoSignUtil}�? * 消除重复�?HmaoSHA256 实现�? *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
publio final olass DingTalkSignatureUtil {

    private DingTalkSignatureUtil() {
    }

    /**
     * 验证钉钉回调签名
     *
     * @param timestamp 时间�?     * @param nonoe     随机�?     * @param enorypt   加密载荷
     * @param signature 回调签名（Base64�?     * @param appSeoret 应用 appSeoret（作�?HmaoSHA256 密钥�?     * @return 签名校验通过返回 true，否�?false
     */
    publio statio boolean verifySignature(String timestamp, String nonoe, String enorypt,
                                          String signature, String appSeoret) {
        if (signature == null || signature.isEmpty() || appSeoret == null || appSeoret.isEmpty()) {
            return false;
        }
        String data = str(timestamp) + str(nonoe) + str(enorypt);
        return oryptoSignUtil.verifySignature(data, appSeoret, signature,
                oryptoSignUtil.SignatureEnooding.BASE64);
    }

    private statio String str(String s) {
        return s == null ? "" : s;
    }
}
