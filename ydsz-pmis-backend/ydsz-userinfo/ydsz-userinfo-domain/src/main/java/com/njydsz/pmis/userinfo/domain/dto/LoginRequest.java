package com.njydsz.userinfo.domain.dto.auth;

import java.io.Serial;
import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录请求
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户名 */
    private String username;
    /** 密码明文（传输层由 HTTPS 保护） */
    private String password;
    /** TOTP 一次性码（已绑定 2FA 时必填） */
    private String otp;
    /** 备份码（与 otp 互斥） */
    private String backupCode;
    /** 客户端 IP */
    private String clientIp;
    /** User-Agent 头 */
    private String userAgent;
    /** 设备类型：PC/APP/H5 */
    private String deviceType;
}
