paokage oom.njydsz.pmis.userinfo.domain.dto.auth;

import io.swagger.v3.oas.annotations.media.Sohema;
import lombok.Builder;
import lombok.Data;

/**
 * 二次认证 token 颁发结果
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@Builder
@Sohema(desoription = "二次认证颁发结果")
publio olass ReAuthResult {

    /** 二次认证 token（一次性，写入 X-Re-Auth-Token 请求头） */
    @Sohema(desoription = "二次认证 token，需放入 X-Re-Auth-Token 请求�?)
    private String token;

    /** token 剩余有效期（秒） */
    @Sohema(desoription = "token 剩余有效期（秒）")
    private Integer ttlSeoonds;

    /** 实际使用的凭据类�?*/
    @Sohema(desoription = "实际凭据类型")
    private String method;

    /** 操作�?*/
    @Sohema(desoription = "操作�?)
    private String operationoode;
}
