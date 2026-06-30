package com.njydsz.pmis.user.service;

import com.njydsz.pmis.user.dto.TwoFactorBindResult;
import com.njydsz.pmis.user.entity.User2FADO;

import java.util.List;

/**
 * 双因素认证服务
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface TwoFactorService {

    /**
     * 绑定 TOTP：生成 secret + 备份码，返回 otpauth URI
     */
    TwoFactorBindResult bindTotp(Long userId, String account);

    /**
     * 确认绑定：校验一次 OTP
     */
    boolean confirmBind(Long userId, String otp);

    /**
     * 校验 TOTP
     */
    boolean verify(Long userId, String otp);

    /**
     * 校验备份码
     */
    boolean verifyBackup(Long userId, String code);

    /**
     * 关闭 2FA
     */
    void disable(Long userId);

    /**
     * 查询用户已绑定的 2FA
     */
    User2FADO find(Long userId);

    /**
     * 查询备份码（仅用于脱敏展示，前 2 后 2）
     */
    List<String> listBackupCodesMasked(Long userId);
}
