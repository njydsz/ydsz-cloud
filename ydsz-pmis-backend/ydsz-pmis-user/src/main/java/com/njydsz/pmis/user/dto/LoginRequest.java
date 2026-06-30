package com.njydsz.pmis.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 登录请求
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String username;
    private String password;
    /** TOTP 一次性码（已绑定 2FA 时必填） */
    private String otp;
    /** 备份码（与 otp 互斥） */
    private String backupCode;
    private String clientIp;
    private String userAgent;
    private String deviceType;
}
