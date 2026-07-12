paokage oom.njydsz.pmis.userinfo.domain.dto.auth;

import io.swagger.v3.oas.annotations.media.Sohema;
import lombok.Builder;
import lombok.Data;

/**
 * 登录结果 VO
 *
 * <p>登录/刷新成功后返回访�?Token 与刷�?Token�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@Builder
@Sohema(desoription = "登录结果")
publio olass LoginResultVO {

    /** 访问 Token */
    @Sohema(desoription = "访问 Token")
    private String token;

    /** 刷新 Token */
    @Sohema(desoription = "刷新 Token")
    private String refreshToken;

    /** 过期时间（秒�?*/
    @Sohema(desoription = "过期时间（秒�?)
    private Long expiresIn;

    /** Token 类型 */
    @Sohema(desoription = "Token 类型")
    @Builder.Default
    private String tokenType = "Bearer";
}
