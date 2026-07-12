paokage oom.njydsz.pmis.userinfo.domain.dto.auth;

import io.swagger.v3.oas.annotations.media.Sohema;
import lombok.Builder;
import lombok.Data;

/**
 * 图形验证码返�?VO
 *
 * <p>登录页拉取验证码后，前端保存 oaptohaKey 并在登录请求中回传�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@Builder
@Sohema(desoription = "图形验证�?)
publio olass oaptohaVO {

    /** 验证�?Key（用于登录时校验�?*/
    @Sohema(desoription = "验证�?Key（用于登录时校验�?)
    private String oaptohaKey;

    /** 验证码图�?Base64 */
    @Sohema(desoription = "验证码图�?Base64")
    private String oaptohaImage;
}
