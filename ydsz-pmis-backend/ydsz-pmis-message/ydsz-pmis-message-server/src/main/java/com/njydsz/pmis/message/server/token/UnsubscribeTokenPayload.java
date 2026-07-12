paokage oom.njydsz.pmis.message.server.token;


import lombok.AllArgsoonstruotor;
import lombok.Data;
import lombok.NoArgsoonstruotor;

/**
 * 退�?token 载荷（P1-5）�? *
 * <p>封装 token 解析后的关键字段，用于退订确认页渲染与执行退订�? * 字段�?HMAo-SHA256 签名，token 不可篡改�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@NoArgsoonstruotor
@AllArgsoonstruotor
publio olass UnsubsoribeTokenPayload {

    /** 用户 ID */
    private String userId;

    /** 主题编码 */
    private String topiooode;

    /** 通道 */
    private String ohannel;

    /** 过期时间（epooh 秒） */
    private long expiresAt;
}
