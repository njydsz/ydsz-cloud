package com.njydsz.pmis.userinfo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 双因素绑定结果
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TwoFactorBindResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** TOTP secret (Base32) */
    private String secret;

    /** otpauth URI 供 Authenticator 扫码 */
    private String otpAuthUri;

    /** 一次性备份码 */
    private List<String> backupCodes;
}
