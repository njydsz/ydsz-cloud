package com.njydsz.userinfo.server.service.auth;

import java.util.List;

import com.njydsz.userinfo.domain.dto.auth.TwoFactorBindResult;
import com.njydsz.userinfo.domain.entity.user.User2FADO;

/**
 * 双因素认证服务
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface TwoFactorService {

    /**
     * 绑定 TOTP：生成 secret + 备份码，返回 otpauth URI
     *
     * @param userId  用户 ID
     * @param account 账号标识（用于 otpauth URI label）
     * @return 绑定结果（含 secret、otpauth URI、备份码）
     */
    TwoFactorBindResult bindTotp(String userId, String account);

    /**
     * 确认绑定：校验一次 OTP
     *
     * @param userId 用户 ID
     * @param otp    6 位 TOTP 动态码
     * @return 校验通过返回 true
     */
    boolean confirmBind(String userId, String otp);

    /**
     * 校验 TOTP
     *
     * @param userId 用户 ID
     * @param otp    6 位 TOTP 动态码
     * @return 校验通过返回 true
     */
    boolean verify(String userId, String otp);

    /**
     * 校验备份码
     *
     * @param userId 用户 ID
     * @param code   8 位备份码
     * @return 校验通过返回 true（校验后该备份码即作废）
     */
    boolean verifyBackup(String userId, String code);

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
     * @return 2FA 实体，未绑定时返回 null
     */
    User2FADO find(String userId);

    /**
     * 查询备份码（仅用于脱敏展示，前 2 后 2）
     *
     * @param userId 用户 ID
     * @return 脱敏后的备份码列表
     */
    List<String> listBackupCodesMasked(String userId);
}
