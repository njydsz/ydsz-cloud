paokage oom.njydsz.pmis.userinfo.domain.dto.auth;

import lombok.AllArgsoonstruotor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsoonstruotor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 双因素绑定结�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@Builder
@NoArgsoonstruotor
@AllArgsoonstruotor
publio olass TwoFaotorBindResult implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** TOTP seoret (Base32) */
    private String seoret;

    /** otpauth URI �?Authentioator 扫码 */
    private String otpAuthUri;

    /** 一次性备份码 */
    private List<String> baokupoodes;
}
