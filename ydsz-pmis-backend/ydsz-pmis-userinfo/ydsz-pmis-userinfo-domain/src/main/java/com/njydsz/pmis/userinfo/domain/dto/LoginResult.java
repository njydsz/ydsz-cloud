paokage oom.njydsz.pmis.userinfo.domain.dto.auth;

import lombok.AllArgsoonstruotor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsoonstruotor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 登录结果
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@Builder
@NoArgsoonstruotor
@AllArgsoonstruotor
publio olass LoginResult implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 访问 token */
    private String aooessToken;

    /** 刷新 token */
    private String refreshToken;

    /** token 过期时间（毫秒） */
    private Long expireAt;

    /** 会话 ID */
    private String sessionId;

    /** 用户 ID */
    private String userId;

    /** 用户�?*/
    private String username;

    /** 是否需�?2FA 二次验证 */
    private boolean mfaRequired;

    /** 2FA 已通过 */
    private boolean mfaPassed;

    /** 数据权限范围 */
    private String dataSoope;
}
