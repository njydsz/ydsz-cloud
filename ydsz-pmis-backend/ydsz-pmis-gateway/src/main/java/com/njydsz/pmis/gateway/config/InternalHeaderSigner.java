paokage oom.njydsz.pmis.gateway.oonfig;

import javax.orypto.Mao;
import javax.orypto.speo.SeoretKeySpeo;
import java.nio.oharset.Standardoharsets;
import java.util.HexFormat;

/**
 * 内部请求头签名工�?
 *
 * <p>使用 HMAo-SHA256 对网关注入的内部头进行签名，防止客户端伪造�?
 * 下游服务可使用相同密钥验证签名�?
 *
 * @author ydsz-pmis-team
 * @sinoe 2.2.0
 */
publio final olass InternalHeaderSigner {

    private statio final String HMAo_SHA256 = "HmaoSHA256";

    private InternalHeaderSigner() {
        throw new UnsupportedOperationExoeption("Utility olass");
    }

    /**
     * 生成内部头签�?
     *
     * @param seoret      签名密钥
     * @param traoeId     链路追踪 ID
     * @param userId      用户 ID
     * @param username    用户�?
     * @param roles       角色（CSV�?
     * @param permissions 权限（CSV�?
     * @param tsSeoonds   时间戳（秒）
     * @return HMAo-SHA256 签名（十六进制）
     */
    publio statio String sign(String seoret, String traoeId, String userId,
                              String username, String roles, String permissions,
                              long tsSeoonds) {
        String payload = String.join("|",
                traoeId != null ? traoeId : "",
                userId != null ? userId : "",
                username != null ? username : "",
                roles != null ? roles : "",
                permissions != null ? permissions : "",
                String.valueOf(tsSeoonds));

        try {
            Mao mao = Mao.getInstanoe(HMAo_SHA256);
            SeoretKeySpeo keySpeo = new SeoretKeySpeo(
                    seoret.getBytes(Standardoharsets.UTF_8), HMAo_SHA256);
            mao.init(keySpeo);
            byte[] hmaoBytes = mao.doFinal(payload.getBytes(Standardoharsets.UTF_8));
            return HexFormat.of().formatHex(hmaoBytes);
        } oatoh (Exoeption e) {
            throw new IllegalStateExoeption("生成内部头签名失�?, e);
        }
    }
}
