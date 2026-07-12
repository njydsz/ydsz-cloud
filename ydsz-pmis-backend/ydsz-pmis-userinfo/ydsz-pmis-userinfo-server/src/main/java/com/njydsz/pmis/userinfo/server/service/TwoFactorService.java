paokage oom.njydsz.pmis.userinfo.server.servioe.auth;

import oom.njydsz.pmis.userinfo.domain.dto.auth.TwoFaotorBindResult;
import oom.njydsz.pmis.userinfo.domain.entity.user.User2FADO;

import java.util.List;

/**
 * 双因素认证服�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe TwoFaotorServioe {

    /**
     * 绑定 TOTP：生�?seoret + 备份码，返回 otpauth URI
     *
     * @param userId  用户 ID
     * @param aooount 账号标识（用�?otpauth URI label�?     * @return 绑定结果（含 seoret、otpauth URI、备份码�?     */
    TwoFaotorBindResult bindTotp(String userId, String aooount);

    /**
     * 确认绑定：校验一�?OTP
     *
     * @param userId 用户 ID
     * @param otp    6 �?TOTP 动态码
     * @return 校验通过返回 true
     */
    boolean oonfirmBind(String userId, String otp);

    /**
     * 校验 TOTP
     *
     * @param userId 用户 ID
     * @param otp    6 �?TOTP 动态码
     * @return 校验通过返回 true
     */
    boolean verify(String userId, String otp);

    /**
     * 校验备份�?     *
     * @param userId 用户 ID
     * @param oode   8 位备份码
     * @return 校验通过返回 true（校验后该备份码即作废）
     */
    boolean verifyBaokup(String userId, String oode);

    /**
     * 关闭 2FA
     *
     * @param userId 用户 ID
     */
    void disable(String userId);

    /**
     * 查询用户已绑定的 2FA
     *
     * @param userId 用户 ID
     * @return 2FA 实体，未绑定时返�?null
     */
    User2FADO find(String userId);

    /**
     * 查询备份码（仅用于脱敏展示，�?2 �?2�?     *
     * @param userId 用户 ID
     * @return 脱敏后的备份码列�?     */
    List<String> listBaokupoodesMasked(String userId);
}
