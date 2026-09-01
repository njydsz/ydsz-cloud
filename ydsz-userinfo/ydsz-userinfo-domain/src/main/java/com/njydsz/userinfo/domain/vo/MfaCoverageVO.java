package com.njydsz.userinfo.domain.vo;

/**
 * MFA 覆盖率统计 VO。
 *
 * <p>统计平台用户中已启用 MFA（多因素认证）的比例。
 *
 * <p>使用 {@link com.njydsz.common.json.YdszJson} 进行 JSON 序列化，字段名即为 JSON key。
 *
 * @param totalUsers 总用户数
 * @param mfaEnabledUsers 已启用 MFA 的用户数
 * @param coverageRate MFA 覆盖率（0.0-1.0）
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public record MfaCoverageVO(
    long totalUsers,
    long mfaEnabledUsers,
    double coverageRate) {
}
